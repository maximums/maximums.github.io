# Code Review — maximums.github.io

_Reviewed: 2026-09-21, at commit `b24b10c` plus uncommitted Boids WIP._

All paths below are relative to `composeApp/src/wasmJsMain/kotlin/com/cdodi/` unless stated otherwise.

## What the project is

A personal portfolio site built with Compose Multiplatform for Kotlin/Wasm. Everything is rendered to a canvas, and the site is deployed to GitHub Pages on every push to `main`.

- **`main.kt`**: the app shell. It has a full-screen SkSL "bokeh rain" background shader, and a home menu of four triangles around a rhombus. When you navigate, those shapes morph into a top bar plus a body card, using `movableContent` and a `LookaheadScope`. Pages switch with a "pixel melt" runtime-shader transition.
- **`buses/`**: three app-wide buses passed through CompositionLocals. `TimeBus` is a per-frame delta-time heartbeat. `AppEventBus` and `AppLifecycleBus` are scaffolding that nothing uses.
- **`data/Manager`**: a fixed-timestep simulation base class driven by `TimeBus`. `GameOfLifeManager` is finished. `FlockingManager` (Boids) is work in progress.
- **Pages**: About is a raytraced-sphere shader sandbox, Boids is WIP, Game of Life is finished, Home is an empty file, and there's a fallback page for small screens.

## Summary

The graphics and Compose-internals work is strong:

- hand-written `Modifier.Node`s
- an `ApproachLayoutModifierNode` for the lookahead placement animation
- a custom `TwoWayConverter` for the shape morph
- a 4-argument `movableContentWithReceiverOf` built on `InternalComposeApi`
- a fixed-timestep accumulator
- a sparse-set Game of Life
- solid shaders

The weak side is engineering discipline. The working tree doesn't compile. There's one real logic bug in the only finished simulation. Infrastructure has been built ahead of any need. Commented-out code and debug `println`s are scattered around, shaders live in inconsistent places, and there are no tests. For a portfolio, the biggest gap is outside the code: a canvas-only Wasm site is invisible to search engines and slow to show anything on first load.

---

## 1. Bugs

### 1.1 The working tree doesn't compile
Verified with `./gradlew compileKotlinWasmJs`:
```
BoidsPage.kt:39:37 No value passed for parameter 'pointMode' / 'points' / 'paint'
```
`skiaCanvas.drawRawPoints()` in `pages/BoidsPage.kt:39` has no arguments. It's uncommitted WIP, but committing it as-is breaks the CI deploy.

- [ ] Fix or remove the `drawRawPoints()` call before committing.

### 1.2 Game of Life compares cell coordinates to pixel sizes
`data/gameoflife/GameOfLifeManger.kt:143`:
```kotlin
private infix fun Cell.isIn(grid: Grid) = x in 0 until grid.width && y in 0 until grid.height
```
`Cell` is measured in cells, but `grid.width` and `grid.height` are in pixels (`cellSize * columns`). On a 1500×800 canvas, a glider keeps living until x = 1499, which is about 1400 columns off-screen. Consequences:
- The population counter includes cells you can't see.
- The game never auto-stops (`isRunning = cells.isNotEmpty()`) once something has escaped.
- The simulation keeps spending CPU on invisible cells.

The same unit mix-up appears in `updateGridBounds` (line 83), which also combines `updatedGrid.width` with `newGridSize.height`. Separately, taps in the leftover strip below `cleanHeight` create cells at `row == rows`, outside the drawn grid.

- [ ] Use `columns` and `rows` in `isIn` and in `updateGridBounds`.
- [ ] Ignore taps outside the drawn grid in `addCell`.

### 1.3 Game of Life reads outer state inside `update {}`
`evaluateNextGeneration` reads `state.value.grid` (line 106) while running inside `_state.update { lifeState -> ... }`. `update` can retry, so logic inside the lambda should only read `lifeState`.

- [ ] Pass `lifeState.grid` in as a parameter. This also makes the function pure and testable (see 4.1).

### 1.4 `FlockingManager` is never closed, so managers leak
In `pages/BoidsPage.kt:29`, `remember { FlockingManager(timeBus) }` has no `DisposableEffect`. Every visit to Boids starts a new manager whose tick-collecting coroutine runs forever. This is already solved properly in `rememberLifeManager()` (`pages/GameOfLifePage.kt:51`).

- [ ] Apply the same pattern here, or generalize it into a `rememberManager { ... }` helper.

### 1.5 `PlacementModifierNode` ignores updated settings
`components/PlacementModifier.kt:36-37` builds `offsetAnimSpec` and `sizeAnimSpec` as `val`s at construction. `update()` then sets `durationMillis` and `easing`, which nothing reads afterwards. `MorphingShapeModifierNodeElement.update` has a similar issue: the new duration and easing are only used if the target shape also changed.

- [ ] Rebuild the specs in `update`, or derive them lazily.

### 1.6 Smaller correctness issues
- [ ] `data/gameoflife/GameOfLifeManger.kt:147`: `isUnspecified` uses `&&`. It should probably be `||`, since a grid with zero in either dimension is unusable.
- [ ] `data/boids/Math.kt`: `Vec2.Zero.normalize` divides by zero and gives NaN. It also computes `magnitude` (a `hypot`) twice.
- [ ] `FlockingManager.setWorldSize`: `size.height - randomY` is still uniformly random, so the "transformation" does nothing. Every resize also re-randomizes the flock.
- [ ] `Utils.kt:24`: `TODO("Why?")` throws `NotImplementedError` at runtime for a `Double` uniform. Use `require(...)` with a message, or a typed builder (see 3.3).

## 2. Performance

### 2.1 The app never idles
`heartBeat` runs `withFrameNanos` forever, and the bokeh background shader is heavy: it evaluates roughly 250 `bokeh()` calls per pixel, at full resolution, on every frame. On top of that, the home card runs a sphere raytrace and page transitions run the melt shader. Expect laptop fans and battery drain on a mostly static site.

- [ ] Render the background at half resolution and upscale.
- [ ] Throttle it to around 30 fps.
- [ ] Respect `prefers-reduced-motion`.
- [ ] Pause when the window loses focus. (The unused `AppLifecycleBus` could finally earn its keep here.)

### 2.2 A coroutine is launched every frame
`TimeBusImpl.onFrame` does `scope.launch { _ticks.emit(...) }` 60 times a second. The flow has `extraBufferCapacity = 1, DROP_OLDEST`, so `tryEmit` always succeeds synchronously. It's also better for timing: the launch hops through the dispatcher, so collectors see each tick after the frame has already been produced, which adds a frame of latency and an extra invalidation.

- [ ] Use `_ticks.tryEmit(deltaTime)`.
- [ ] Use `trySend` in `AppEventBusImpl.sendEvent` for the same reason.

### 2.3 Native objects are allocated every frame
`RuntimeShaderModifierNode.draw` allocates a new `Data` (via `uniformData`) and a new Skia `Shader` each frame, relying on finalizers to free native memory.

- [ ] Reuse a `RuntimeShaderBuilder`, or keep one `ByteArray` and one `Data`.

### 2.4 Duplicated time accumulation
`ticks.scan { (acc + tick) % 10000f }` appears three times (`components/RuntimeShaderModifier.kt:48`, `components/MainMenu.kt:75`, `pages/AboutPage.kt:131`). The `% 10000` also makes every shader visibly jump about every 2.8 hours.

- [ ] Expose `elapsed: StateFlow<Float>` once on `TimeBus`.

### 2.5 Shader compile failures crash the app
`RuntimeEffect.makeForShader` throws on invalid SkSL, and nothing catches it. `FALLBACK_SHADER` only covers the loading state, not compile errors.

- [ ] Catch the compile error and fall back to `FALLBACK_SHADER`.

## 3. Design and architecture

### 3.1 Infrastructure built ahead of need
`AppEventBus` (with `NavigateTo`, `ShowNotification`, `ToggleTheme`) and `AppLifecycleBus` are provided at the root and used nowhere. Navigation is a local `mutableStateOf(Page)` in `AppContent`, which is the right call at this size. As it stands, the buses suggest an architecture that doesn't exist.

- [ ] Delete them, or wire them in when a feature actually needs them.

> **Overridden (2026-09-21):** the buses are kept on purpose. The heartbeat becomes the single clock for all animation, alongside the navigation and lifecycle buses. See `PLAN.md`, Phase 2.

### 3.2 Components depend on pages
`components/MainMenu.kt:29` imports `MY_TRY` from `pages/AboutPage.kt`. The README marks "Move shaders to resource files" as done, yet `PIXEL_MELT_SHADER`, `MY_TRY` and `SANDBOX` (unused) are still inline strings.

- [ ] Move them into `composeResources/files/*.sksl`, next to `bokeh.sksl`.

### 3.3 Two uniform APIs
The background uses hand-packed bytes (`uniformData(width, height, time)`), which depend on declaration order and quietly break if the uniforms are reordered. Everywhere else uses `RuntimeShaderBuilder.uniform("name", ...)`.

- [ ] Standardize on `RuntimeShaderBuilder`.

### 3.4 The Boids data model will fight you
- `Boid(direction, velocity)` should be `position, velocity`.
- `Rule.invoke(neighbors: Set<Boid>)` with data-class equality means two boids with identical state collapse into one. It also hashes every boid every step.
- `Vec2` wraps `Offset`, which already has `plus`, `minus`, `times` and `getDistance()`.
- `operator fun times(Vec2)` as a dot product is surprising. Call it `dot`.
- The current `LongArray(16)` draws 14 circles at (0, 0).

- [ ] For N boids, use flat `FloatArray`s (x, y, vx, vy) plus a spatial grid for neighbour lookup. The comment in `Boids.kt` already has the right instinct.

### 3.5 The Boids canvas redraws through a hack
The canvas redraws because it reads `tick.value`, while `flock` is a plain array that isn't observable. It works, but it depends on the manager having stepped before draw.

- [ ] Give the manager a `frame` counter state that it bumps after each `loop`, and have the canvas read that.

### 3.6 Transition timing is mismatched
In `main.kt:176-187`, the fade takes 2000 ms but the melt finishes in 1000 ms, so the outgoing page is fully dissolved halfway through its fade. Also, transitions into or out of Home skip the melt entirely, because `AnimatedContent` only exists in the non-Home branch.

- [ ] Decide whether this is intended; if not, align the durations.

## 4. Code hygiene

### 4.1 No tests
`junit` and `kotlin-test` are declared in `gradle/libs.versions.toml` and never used. The Game of Life logic is pure, and a single "glider exits the grid and dies" test would have caught bug 1.2. `EvolutionEngine` is implemented by the manager itself, so the abstraction doesn't buy anything.

- [ ] Extract `evaluateNextGeneration` into a pure function or object and add tests for it.

### 4.2 Dead and debug code
- [ ] Commented-out blocks in `main.kt:218-237`, `components/MainMenu.kt:55,143-150`, `components/MorphingShapeModifier.kt:81-98`, `pages/BoidsPage.kt:41-44` and inside `MY_TRY`.
- [ ] `println` in `SideEffect` on the About and Boids pages.
- [ ] Empty `pages/HomePage.kt`.
- [ ] Unused: `UiCard`, `SANDBOX`, `ONE_SECOND_NANOS` (duplicates `ONE_SECOND_IN_NANO`), `test_shader.sksl`.
- [ ] Unused imports: `PointMode`, `unpackFloat1`, `sign`, `withInfiniteAnimationFrameNanos`.

Git history is the place to keep experiments.

### 4.3 Naming
- [ ] `contactsButton` is labelled "Boids"; `sketchButton` is labelled "Game Of Life".
- [ ] Misspellings: the file `GameOfLifeManger.kt`, the variable `flockingManger`, and `AppLifecycleBusImp` (should be `Impl`).
- [ ] `MY_TRY` doesn't describe anything.
- [ ] `heartBeat` is a Unit-returning composable in camelCase.
- [ ] `Vec2.normalize` is a property named like a verb.
- [ ] `topBarModifier` is a top-level global.
- [ ] `rootProject.name = "blog"` means generated resources live in `blog.composeapp`.

### 4.4 Hardcoded pixels
`CELL_SIZE_PX = 20f` and the boid radius of `25f` ignore density, so cells are half as big on a HiDPI display.

- [ ] Express them in `dp` and convert with the current density.

## 5. Build and CI

- [ ] `.github/workflows/main.yaml`: bump `actions/setup-java@v3` to v4, and add Gradle wrapper validation.
- [ ] `kotlin-js-store/` is in `.gitignore`. That folder holds `yarn.lock`, which JetBrains recommends committing for reproducible npm resolution. Right now CI resolves transitive JS dependencies fresh on every run.
- [ ] The build job's upload step has `id: deployment`, the same ID the deploy job uses. It's harmless but confusing.
- [ ] `compose.components.uiToolingPreview` has no effect on a Wasm-only target. You're also on Material 2 (`compose.material`); if more UI chrome gets added, Material 3 is the maintained path.
- [ ] Add `org.gradle.caching=true` and `org.gradle.configuration-cache=true` to `gradle.properties`.

## 6. The product itself

- [ ] **First load:** `index.html` has an empty `<body>`. Visitors see a blank white page while the wasm bundle downloads and compiles. Add an inline loading state, a dark background in `styles.css` to avoid a white flash, and a message for browsers without WasmGC support (older Safari versions just show nothing).
- [ ] **Discoverability:** everything is drawn on a canvas, so search engines, link previews and screen readers see a page titled "Dodi Cristian-Dumitru" and nothing else. Add at least a meta description and Open Graph tags. Consider putting About and Contact in plain HTML, and keeping Compose for the showcase pieces (shaders, simulations).
- [ ] **Small screens:** `width < 600.dp || height < 600.dp` sends phones, and even a short laptop window, to a "please use a larger screen" page. Many first visits to a portfolio come from a phone via a shared link. A simplified mobile layout would serve better than a wall.
- [ ] **Content:** the README TODO lists Home, About, Contact and Paint as not done, and Game of Life is the only finished page. Finishing About and Contact would help the site more than more shader work would.

## Suggested priority

1. Fix the Boids compile error before committing (1.1).
2. Fix the Game of Life unit bug (1.2, 1.3) and add a test for it (4.1).
3. Dispose `FlockingManager` (1.4). Switch to `tryEmit`/`trySend` (2.2).
4. Delete dead code and the unused buses; move inline shaders to resource files (3.1, 3.2, 4.2).
5. Add a loading state and meta tags to `index.html`; reduce the background shader's cost (6, 2.1).
6. Continue Boids with a flat-array data model (3.4).

---

# Branch review — `cdodi/webgpu-test`

_Reviewed: 2026-09-21, at `da7035c`. The branch forked from `ed22808`, before main's heartbeat, runtime-shader and Boids commits, so it has diverged from main. It builds (`:composeApp:compileKotlinWasmJs` passes). Findings marked **verified** were checked against the generated code of that build._

Paths in this section are relative to the repo root on that branch.

## What's on the branch

1. **`plugins/webIdlBinding`**: a Gradle plugin that works like a compiler. It downloads `webgpu.idl`, parses it with an ANTLR WebIDL grammar, and resolves mixins, partial interfaces, dictionary inheritance, typedefs and unions (unions become marker interfaces). It then uses KotlinPoet to write about 3,000 lines of Kotlin: `external` declarations, dictionary pseudo-constructors, enum-entry objects, and `*Suspend` wrappers for methods that return promises.
2. **`webGpuRuntime`**: helpers the generated code relies on (`createJsObject`, `toJsArray`, `Promise.await`).
3. **App code**: a compute shader that doubles an array and a single-frame triangle. `main()` now runs those instead of the Compose app.
4. **Cleanups**: some fixes from the review above (the file rename, `TODO("Why?")`, the duplicated time loop), plus Copilot docs and skill files.

## Verdict on the approach

Generating bindings from WebIDL is a legitimate approach; Rust's `web-sys` and Kotlin/JS's own DOM bindings were built this way. The pipeline is well structured: collect symbols, resolve them into an immutable context, then generate. The typed `Slice` store is a nice touch, and turning all-object unions into marker interfaces is clever.

The real question is **what the goal is**:

- **If the goal is WebGPU on the site:** about 1,500 lines of generator, an ANTLR build and two network downloads support about 200 lines of WebGPU code. Hand-written `external` declarations for the subset you use (device, buffers, pipelines, passes) are roughly 200 lines and avoid every problem below. An existing Kotlin WebGPU library such as wgpu4k is also worth checking before maintaining your own.
- **If the generator is itself the project:** it's a good portfolio piece. Treat it that way: give it tests and its own module or repo, and write it up on the site.

Either way, one architectural fact should shape the plan: **Compose for Web draws through Skia on WebGL into its own canvas, so WebGPU output can't be drawn inside Compose layouts.** It needs a separate HTML canvas positioned under or over the Compose canvas. The existing fragment effects (bokeh, melt) already run on the GPU through SkSL, so porting them gains little. **Compute shaders are the real gain**: Boids with 10k+ agents, Game of Life on the GPU, particle systems. That argues for using WebGPU per simulation page with a fallback to the Skia path, not as a new foundation for the whole site.

## W1. Generator bugs (verified in the generated output)

- [ ] **Nullability is lost for object types.** In `TypeMapping.kt:33`, the `isKnownDescriptor` branch ignores `isNullable`. `Promise<GPUAdapter?> requestAdapter(...)` becomes `Promise<GPUAdapter>`. A null adapter is the most common WebGPU failure (unsupported browser, blocklisted GPU), and the type says it can't happen.
- [ ] **Every primitive becomes nullable** (`mapPrimitiveJs` ends in `.copy(nullable = true)`). The non-null `readonly attribute GPUSize64Out size` becomes `val size: JsNumber?`. Combined with the previous bug, nullability is wrong in both directions.
- [ ] **`optional` arguments without a default are treated as required.** The spec says `getMappedRange(optional GPUSize64 offset = 0, optional GPUSize64 size)`; the output is `getMappedRange(offset: JsNumber? = definedExternally, size: JsNumber?)`. Only the presence of a default value is checked, not the `optional` keyword.
- [ ] **`undefined` return types become `JsAny?`** instead of `Unit`, e.g. `configure(...): kotlin.js.JsAny?`.
- [ ] **`JsNumber`/`JsString` are used in external declarations.** Kotlin/Wasm externals accept `Int`, `Double`, `Boolean` and `String` directly; `JsAny` subtypes are only needed as type arguments (`JsArray<T>`, `Promise<T>`). Switching removes every `0.toJsNumber()` (about a dozen in `Computation.kt`) and the whole JS/Kotlin dual mapping plus `conversionBridge`. This is the biggest simplification available.
- [ ] **64-bit sizes are mapped to `Int`.** `GPUSize64` becomes `Int` and overflows past 2 GiB. Map `long long` / `unsigned long` to `Double`.
- [ ] **`readonlysetlike` is skipped** (the build warns). `GPUSupportedFeatures` has no `has()`, so features like `shader-f16` can't be checked.
- [ ] **DOM supertypes are dropped** in `filterExternalSuperTypes`. `GPUDevice` loses `EventTarget`, so there's no typed way to listen for `uncapturederror`. The unused `EXTERNAL_TYPE` slice looks like it was meant for exactly this.
- [ ] **Maps are mutated while being iterated.** `resolveTypesInContext` loops over the live interface map while `resolveUnions` inserts marker interfaces into it. That's a latent `ConcurrentModificationException` and can overwrite a descriptor with a stale copy; it only works because WebGPU's all-object unions happen to appear in dictionaries. Build new maps instead. Also, `MutableBindingContext.get` hands out its internal map.
- [ ] **Latent compile error:** a dictionary member of type `sequence<primitive>` generates `List<Int>.toJsArray()`, which doesn't compile. `webgpu.idl` doesn't hit it, but other IDLs would.
- [ ] **Small issues:** `resolveUnionJs` and `resolveUnionKt` are identical; `resolveRecordJs` has unused locals (compiler warns); `asPoetKt` maps Promise to `kotlinx.coroutines.Deferred`, which isn't a dependency; enum entries are backticked raw strings (`` `triangle-list` ``) where camelCase would read better.
- [ ] **The runtime duplicates existing libraries.** kotlinx-coroutines already provides `Promise<T>.await()` for wasmJs. The custom `await` can't be cancelled and turns JS errors into `RuntimeException(error.toString())`, losing the `GPUError` type. `List.toJsArray()` exists in the stdlib (its `Array` overload is already used in `Computation.kt:50`).

## W2. Build problems

- [ ] **Unpinned network downloads.** The IDL comes from `wpt/master` and the grammar from `grammars-v4/master`. **Verified: running the build modified the committed `WebIDL.g4`**, because upstream had changed. Builds aren't reproducible, a fresh offline build fails, and CI can generate different bindings than a local machine (locally the download task stays up to date because its only input is the URL). Commit `webgpu.idl` to the repo (the grammar is already committed) and delete both download tasks, or pin both URLs to a commit SHA. Don't mark a download task `@CacheableTask`.
- [ ] **The ANTLR parser is generated in the default package** (`import WebIDLLexer`). Add `-package com.cdodi.webidl.parser` to the grammar generation arguments.
- [ ] **The plugin isn't generic yet.** The package `com.cdodi.webgpu.bindings`, output file names and runtime package are hard-coded in `TranspileWebIdlTask`. Add an extension DSL. The generated code also silently requires `webGpuRuntime` on the classpath.
- [ ] **Bindings are generated in the wrong module.** Apply the plugin to the runtime module instead of `composeApp` (and rename it `webgpu`), so bindings and helpers form one library compiled once.
- [ ] **Duplication and typos.** The two download tasks are copies of each other; `webIdlUrl` is duplicated, unused, in `plugins/gradle.properties`; the convention plugin is named `cdodi.antrl-setup` (should be `antlr`).
- [ ] **No tests.** The transpiler is the most testable code in the repo: feed small IDL snippets and compare output against saved expected files. That would have caught most of W1.
- [ ] **Configuration cache is disabled** because of an "Array out of bounds" error. Worth diagnosing rather than living with.

## W3. App side

- [ ] **`main()` replaces the whole site** with the triangle and compute demo, and `index.html` has a fixed full-screen canvas (`width="fill"` isn't a valid value). Not mergeable as-is.
- [ ] **The adapter and device are requested twice**, once per demo. No handling for `device.lost` or errors; the triangle renders a single frame; nothing handles resizing.
- [ ] **The branch is behind and diverged from main.** It has Kotlin 2.3.20, CMP 1.10.3 and Gradle 8.14.4 versus main's 2.4.10 and 1.11.1. `composeApp/build.gradle.kts` hard-codes `1.10.3` coordinates instead of using the version catalog. The same things were refactored differently on each branch (`rememberAnimatedTime` vs main's `TimeBus`, the Game of Life changes), so expect conflicts.
- [ ] **The Copilot docs are already out of date.** `.github/copilot-instructions.md` says `main` calls `prepareWebGPUCanvas()`, which doesn't exist, and names a task `downloadWebGrammar` when the real one is `downloadWebIdlGrammar`.

## Recommended path

1. Cherry-pick the cleanup commits into main first (`17664bf`, `08fe775`, `24dc5b7`, `8d6b335`, `00f80d8`). They overlap with items in the main review above and don't depend on WebGPU.
2. Decide between keeping the generator and writing bindings by hand. If keeping it: fix the nullability bugs and the primitive mapping first, commit the IDL to the repo, move generation into a `webgpu` module, and add expected-output tests.
3. Integrate per page, not app-wide: one shared GPU device, an overlay canvas for pages that need it, and a feature check with a fallback to the Skia path. Porting Boids to a compute shader is the natural first real use.
