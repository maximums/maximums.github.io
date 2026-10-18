---
title: I built a WebIDL compiler to get type-safe WebGPU in Kotlin/Wasm
date: 2026-10-18
summary: Kotlin/Wasm has no WebGPU bindings, so I generate them from the spec's own WebIDL. It uses an ANTLR grammar, a chain of pure resolution passes and KotlinPoet, and golden files and a compile test check the output.
---

Everything behind this site's glass is drawn with WebGPU from Kotlin compiled to WebAssembly: the rainy street, the Game of Life, the flock of boids. Kotlin/Wasm talks to the browser through `external` declarations, and for WebGPU there weren't any. I could have written the hundred or so declarations the site needs by hand. Instead I wrote a compiler that reads the WebGPU specification's own interface definitions and writes all of them.

## Why generate them

WebGPU is specified in WebIDL, the interface language every web API is written in. The WebGPU IDL is 1,379 lines: around 170 interfaces, dictionaries, enums and typedefs. Hand-written bindings cover what you happen to use today. Then they drift from the spec, and they're wrong in the places you didn't look closely. Generated bindings are only as wrong as the generator, and a generator can be tested.

It's also how the platform does it: Rust's `web-sys` and Kotlin/JS's own DOM declarations are generated from the same IDL files.

From the outside, the result is ordinary, typed Kotlin:

```kotlin
val pipeline = gpu.device.createRenderPipeline(
    GPURenderPipelineDescriptor(
        layout = GPUAutoLayoutMode.auto,
        vertex = GPUVertexState(module = module, entryPoint = "vs"),
        fragment = GPUFragmentState(module = module, entryPoint = "fs", targets = listOf(GPUColorTargetState(format = format))),
        label = label,
    )
)
```

Dictionaries are built with named parameters, and the required ones have no default. Enum values are checked by the compiler, so a typo in `"triangle-list"` can't reach the GPU. Every declaration links to its section of the spec.

## The pipeline

The compiler is a Gradle plugin, about 1,900 lines of Kotlin, in four stages:

1. **Parse.** An ANTLR grammar for WebIDL (from `antlr/grammars-v4`, pinned to a commit) turns the IDL into a parse tree. A syntax error fails the build with the file, line and column; it never produces half a set of bindings.
2. **Collect.** A visitor walks the tree and records every definition: interfaces, mixins, partial interfaces, dictionaries, enums, typedefs, namespaces.
3. **Resolve.** A chain of pure passes, each taking a snapshot of the definitions and returning a new one:
   - mixins are copied into the interfaces that include them;
   - partial interfaces are merged;
   - dictionaries are flattened, so each one carries every member it inherits;
   - supertypes are checked;
   - every type name is validated;
   - typedefs are unrolled and unions normalised.
4. **Generate.** KotlinPoet writes five files: enums, interfaces, dictionaries, namespaces, and suspend wrappers for the operations that return promises. For WebGPU that's about 5,400 lines.

Keeping the middle stage as pure functions over immutable snapshots made it easy to test. It also fixed a real bug from the first version: a pass used to add marker interfaces to the very map it was iterating over.

## The interesting part: the type decisions

Most of the work is deciding what each WebIDL construct should become in Kotlin/Wasm. A few of the decisions:

**Kotlin types wherever the language allows.** Kotlin/Wasm externals take `Int`, `Double`, `Boolean` and `String` directly. JS wrapper types (`JsNumber`, `JsString`) are only needed as type arguments, as in `Promise<T>` and `JsArray<T>`. So that's the only place they appear.

**Nullability comes from the IDL, and only from the IDL.** `Promise<GPUAdapter?> requestAdapter()` keeps its question mark. A null adapter is the most common way WebGPU fails (an unsupported browser, a blocklisted GPU), and the type should say it can happen. An early version lost the `?` on objects and added one to every primitive, so it was wrong in both directions.

**64-bit sizes are `Double`.** `GPUSize64` is an `unsigned long long`. As an `Int` it overflows past 2 GiB; a JS number holds every integer up to 2^53.

**Enums are strings, typed.** In JS a WebGPU enum is just a string. The compiler makes each enum an external interface with a companion object, and each value an inline property that casts the string:

```kotlin
public inline val GPUPrimitiveTopology.Companion.triangleList: GPUPrimitiveTopology
  get() = "triangle-list".toJsString().unsafeCast()
```

At runtime that's a string constant. At compile time it's a distinct type that only accepts its own values.

**Unions of objects become marker interfaces.** A bind group entry's resource is `(GPUSampler or GPUTextureView or GPUBufferBinding)`. Kotlin has no union types, so the compiler creates an interface named after the union and makes each member extend it. Enums can join too: a pipeline's layout is `(GPUPipelineLayout or GPUAutoLayoutMode)`, which is why `layout = GPUAutoLayoutMode.auto` type-checks in the example above. A union that includes a primitive has no common Kotlin type, so it becomes `JsAny`, and operations get one overload per member instead.

**Platform types get extensions.** The IDL says `Navigator includes NavigatorGPU`. `Navigator` belongs to kotlinx-browser, so the compiler can't add members to it. Instead, `gpu` becomes an extension property, and it's nullable: a browser without WebGPU simply doesn't have it.

**Unknown types fail the build.** A type name that isn't defined in the IDL or mapped to an existing Kotlin type stops the build, listing every unknown name and where it's used. The alternative, quietly turning it into `JsAny`, is how bindings lose their types without anyone noticing.

## Reproducible builds

The IDL and the grammar are committed to the repository, and builds only ever read those copies. Updating is a task I run by hand:

```
./gradlew :webgpu:updateWebIdl -PwebrefVersion=3.84.0
```

It downloads that exact version of `@webref/idl`, writes a header naming the version, and leaves the change to review as an ordinary diff. A build never changes because upstream did, and a fresh checkout builds offline.

## Tests

Three layers:

- **Golden files.** 19 small fixtures, one per construct (unions, mixins, promises, optional arguments and so on). Each starts with a comment saying what it should become. The compiler's output for each one is compared with a committed copy. `-PupdateGoldens` rewrites them, so a change to the generated API shows up as a diff in review.
- **A compile test.** Gradle TestKit builds a throwaway Kotlin/Wasm project per fixture and compiles the generated code. The goldens show what is generated; this shows that it compiles.
- **Runtime tests.** The bindings drive a real WebGPU device in headless Chrome, on SwiftShader's software GPU: they request a device, run a compute shader, read the results back and render a frame. The site's own GPU code is tested the same way, against CPU reference implementations.

Nothing in the pipeline is specific to WebGPU, apart from the table that maps DOM names to kotlinx-browser types. Other specs (WebCodecs, WebXR) are the obvious next test of that claim.
