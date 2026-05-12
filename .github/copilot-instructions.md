# Copilot Instructions

## Project Overview

Kotlin Multiplatform portfolio/blog site targeting **Wasm/JS** (browser), built with Compose Multiplatform. The UI uses advanced Compose animations (shape morphing, LookaheadScope placement, Skia runtime shaders) and integrates a **WebGPU** rendering canvas alongside the Compose layer.

A custom Gradle plugin (`webIdlBinding`) transpiles the WebGPU IDL specification into type-safe Kotlin/Wasm external declarations at build time.

## Build & Run

```bash
# Dev server with hot reload (webpack)
./gradlew wasmJsBrowserDevelopmentRun

# Production build (output: composeApp/build/dist/wasmJs/productionExecutable/)
./gradlew wasmJsBrowserDistribution

# Run only the WebIDL transpiler (downloads IDL spec → generates Kotlin bindings)
./gradlew :composeApp:transpileWebIdl

# Download the WebIDL grammar for ANTLR (plugin build-logic)
./gradlew :webIdlBinding:downloadWebGrammar
```

There are no tests in the project.

## Architecture

### Module layout

```
composeApp/       ← Main Wasm/JS application (Compose UI + WebGPU canvas)
webGpuRuntime/    ← Minimal JS interop helpers (createJsObject, toJsArray, Promise.await)
plugins/
  build-logic/    ← Convention plugin: downloads ANTLR WebIDL grammar
  webIdlBinding/  ← Gradle plugin: WebIDL → Kotlin/Wasm code generator
```

### WebIDL transpiler pipeline (`plugins/webIdlBinding`)

The custom Gradle plugin `com.cdodi.webidl.bindings` generates all `com.cdodi.webgpu.bindings.*` code. The pipeline is:

1. **DownloadIdlTask** — fetches the WebGPU IDL spec from `webIdlUrl` (in `gradle.properties`)
2. **ANTLR parse** — lexes/parses the IDL with a WebIDL grammar (grammar URL in `plugins/gradle.properties`)
3. **SymbolCollectorVisitor** — walks the ANTLR tree, populates a `MutableBindingContext` with slices (interfaces, dictionaries, enums, typedefs, mixins, namespaces, includes directives)
4. **InterfaceCollector / TypeResolver** — extract members and resolve WebIDL types
5. **Resolution** (`resolveSemantics`) — merges partials, resolves mixin includes, flattens dictionary inheritance, unrolls typedefs, resolves union types into marker interfaces
6. **Generation** (`generateKotlin`) — emits two KotlinPoet `FileSpec`s:
   - `WebGpuBindings.kt` — `external interface` / `external abstract class` declarations
   - `WebGpuFactories.kt` — dictionary factory functions (using `createJsObject`), enum entry objects, and `suspend` wrappers for Promise-returning methods

Key abstractions:
- **`BindingContext` / `Slice<K,V>`** — typed key-value store where each `Slice` holds one category of descriptors
- **`TypeMapping`** — dual mapping: JS-facing types (`JsNumber`, `JsString`) for external decls vs Kotlin-facing types (`Int`, `String`) for factory functions
- **`Descriptor` sealed hierarchy** — AST: `InterfaceDescriptor`, `EnumDescriptor`, `TypeDescriptor`; members: `VariableDescriptor`, `FunctionDescriptor`, `ConstantDescriptor`

### Application layer (`composeApp`)

- **Entry point**: `main.kt` — `suspend fun main()` calls `prepareWebGPUCanvas()` (currently active); the Compose `App()` composable is commented out but contains the full UI
- **Navigation**: enum-based `Screen` (Home, About, Boids, GameOfLife) with `updateTransition` + `AnimatedContent`. Page transitions use a custom `PIXEL_MELT_SHADER` (AGSL RuntimeEffect)
- **Layout**: `LookaheadScope` wraps the app; cards use `movableContentWithReceiverOf` for stateful shared-element transitions between Home grid and top-bar layouts

### Shader system

Two shader languages are used:
- **AGSL/SKSL** — Skia runtime shaders for Compose UI effects. Loaded from `composeResources/files/*.sksl` via `Res.readBytes()`, or defined inline with `// language=agsl` comments. Applied via `RuntimeShaderModifierNode` (custom `DrawModifierNode`) or `graphicsLayer { renderEffect = ... }`
- **WGSL** — WebGPU shaders for the `<canvas>` rendering path (currently a triangle demo in `GpuMain.kt`)

### WebGPU integration

`GpuMain.kt` (`prepareWebGPUCanvas`) sets up a full WebGPU render pipeline:
- Gets the GPU via `@JsFun("() => navigator.gpu")` in `Core.kt`
- Uses generated bindings (`com.cdodi.webgpu.bindings.*`) for type-safe descriptor construction
- The runtime helpers in `webGpuRuntime` provide `createJsObject<T>()` (creates empty JS objects and casts), `List<T>.toJsArray()`, and `Promise<T>.await()`

## Key Conventions

### Custom Compose modifiers use the Modifier.Node API

All custom modifiers follow the `ModifierNodeElement` + `Modifier.Node` pattern (not the deprecated `Modifier.composed`):
- `MorphingShapeModifierNode` / `MorphingShapeModifierNodeElement` — animated quad-vertex clipping
- `PlacementModifierNode` / `PlacementNodeElement` — `ApproachLayoutModifierNode` for animated position/size with `DeferredTargetAnimation`
- `RuntimeShaderModifierNode` / `RuntimeShaderModifierNodeElement` — continuous shader background with `withInfiniteAnimationFrameNanos`

Extension function pattern: `Modifier.morphingShape(...)`, `Modifier.animatePlacement(...)`, `Modifier.backgroundShader(...)`

### MovableContent for shared-element transitions

`movableCard` and `movableBodyCard` return lambdas of type `@Composable LookaheadScope.(Modifier, MorphingShape) -> Unit`. They use `movableContentWithReceiverOf` (including a custom 4-parameter overload using nested `Pair` packing) to preserve composable identity across layout changes.

### Viewport-relative units

`Utils.kt` provides `Number.vw` and `Number.vh` extension properties that return `Dp` values relative to the viewport, backed by `LocalViewportSize` (a `compositionLocalWithComputedDefaultOf`). Usage: `15.vw`, `80f.vh`.

### Responsive layout

`LocalIsSmallWindow` switches to `SmallScreenPage()` when viewport < 800dp in either dimension.

### Game simulation pattern

`GameOfLifeManager` uses a fixed-tick game loop (`withInfiniteAnimationFrameMillis` + accumulator pattern at 100ms tick rate) with `StateFlow` for reactive cell state. Rules are modeled as `fun interface GameRule` instances composed via `Collection<GameRule>`.

### Package structure

```
com.cdodi                      ← Entry point, shaders, utilities
com.cdodi.pages                ← Full-page composables (AboutPage, GameOfLifePage, etc.)
com.cdodi.components           ← Reusable UI: modifiers, cards, menu
com.cdodi.data.gameoflife      ← Game of Life simulation engine
com.cdodi.webgpu               ← WebGPU setup + runtime JS interop helpers
com.cdodi.webgpu.bindings      ← Generated (do not edit) — WebGPU API bindings
com.cdodi.webgpu.{core,canvas,pipeline,command} ← Legacy manual bindings (commented out, superseded by generated bindings)
```

### Generated code

Files under `com.cdodi.webgpu.bindings` are generated by the `transpileWebIdl` task. Do not edit them manually. To change the generated output, modify the transpiler in `plugins/webIdlBinding/src/`.

### Dependency versions

Managed in `gradle/libs.versions.toml`. Key versions: Kotlin 2.3.20, Compose Multiplatform 1.10.3, ANTLR 4.13.1, KotlinPoet 2.0.0.

## CI/CD

GitHub Actions workflow (`.github/workflows/main.yaml`): builds on push to `main`, deploys `composeApp/build/dist/wasmJs/productionExecutable/` to GitHub Pages. Uses Java 17 (Temurin).
