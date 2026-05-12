# Portfolio

A personal portfolio site built with **Kotlin Multiplatform** and **Compose for Web** (Wasm/JS). Features advanced Compose animations, Skia runtime shaders, and a **WebGPU** rendering canvas — all running in the browser via WebAssembly.

A custom Gradle plugin transpiles the [WebGPU IDL specification](https://www.w3.org/TR/webgpu/) into type-safe Kotlin/Wasm external declarations at build time.

## Tech Stack

- **Kotlin** 2.3.20 · **Compose Multiplatform** 1.10.3
- **WebGPU** via generated Kotlin/Wasm bindings
- **ANTLR** 4.13.1 (WebIDL parser) · **KotlinPoet** 2.0.0 (code generation)
- **GitHub Actions** → GitHub Pages deployment

## Project Structure

```
composeApp/        Main Wasm/JS application (Compose UI + WebGPU canvas)
webGpuRuntime/     JS interop helpers (createJsObject, toJsArray, Promise.await)
plugins/
  build-logic/     Convention plugin — downloads the ANTLR WebIDL grammar
  webIdlBinding/   Gradle plugin — WebIDL → Kotlin/Wasm code generator
```

## Build & Run

```bash
# Dev server with hot reload
./gradlew wasmJsBrowserDevelopmentRun

# Production build
./gradlew wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/

# Run the WebIDL transpiler standalone
./gradlew :composeApp:transpileWebIdl
```

> **Prerequisites:** JDK 17+

## Features

### Compose UI
- **Shape morphing** — custom `Modifier.Node` that animates quad-vertex clipping paths
- **Shared-element transitions** — `movableContentWithReceiverOf` inside `LookaheadScope` for stateful identity-preserving layout changes
- **Animated placement** — `ApproachLayoutModifierNode` with `DeferredTargetAnimation` for smooth position/size interpolation
- **Runtime shaders** — AGSL/SKSL shaders loaded from resources, applied via `RuntimeShaderModifierNode` for continuous UI effects and page transitions (pixel-melt)
- **Responsive layout** — viewport-relative units (`vw`/`vh`) and automatic small-screen adaptation

### Pages
- **Home** — animated card grid with shared-element navigation
- **About** — shader-animated content
- **Boids** — flocking simulation
- **Game of Life** — fixed-tick simulation with `StateFlow`, composable rule system via `fun interface GameRule`

### WebGPU Integration
- Full render pipeline setup in `GpuMain.kt` (MSAA, WGSL shaders)
- Type-safe bindings generated from the official WebGPU IDL spec
- Runtime helpers: `createJsObject<T>()`, `List<T>.toJsArray()`, `Promise<T>.await()`

### WebIDL Transpiler Pipeline
1. **Download** — fetches the IDL spec from the W3C repository
2. **Parse** — ANTLR lexer/parser with fail-fast error handling
3. **Collect** — walks the AST into a typed `BindingContext` (interfaces, dictionaries, enums, typedefs, mixins, namespaces)
4. **Resolve** — merges partials, resolves mixin includes, flattens inheritance, unrolls typedefs, resolves unions
5. **Generate** — emits `WebGpuBindings.kt` (external declarations) and `WebGpuFactories.kt` (factory functions, enum entries, suspend wrappers)

## TODO — Skia → WebGPU Migration

Replace Skia runtime shaders (AGSL/SKSL) with WebGPU compute and render pipelines for all visual effects.

### Foundation
- [ ] Build a WebGPU render loop (requestAnimationFrame + command encoder per frame)
- [ ] Create a uniform buffer abstraction (resolution, time, mouse) to replace `uniformData()`
- [ ] Implement a texture-based compositing bridge — render WebGPU output to a texture, display it in Compose via `HtmlView` or canvas overlay
- [ ] Design a `WgslShader` abstraction analogous to `RuntimeShader`, loadable from resource files

### Shader Ports (AGSL → WGSL)
- [ ] Port `bokeh.sksl` background effect to a WGSL fragment shader
- [ ] Port `wet_neural_network.sksl` to WGSL
- [ ] Port `test_shader.sksl` to WGSL
- [ ] Port `PIXEL_MELT_SHADER` page transition to a WGSL post-processing pass with `composable` input texture sampling
- [ ] Port `FALLBACK_SHADER` to WGSL

### Modifier Replacement
- [ ] Replace `RuntimeShaderModifierNode` (Skia `RuntimeEffect` + `Paint`) with a WebGPU-backed modifier that renders to an offscreen texture
- [ ] Replace the `graphicsLayer { renderEffect = ... }` page transition with a WebGPU post-process pass
- [ ] Remove Skia `Paint`, `RuntimeEffect`, and `RuntimeShaderBuilder` dependencies from the Compose layer

### Simulations
- [ ] Move Game of Life tick logic to a WGSL compute shader (cell state in storage buffers, ping-pong pattern)
- [ ] Move Boids simulation to a WGSL compute shader (position + velocity storage buffers)
- [ ] Render simulation results directly in WebGPU instead of mapping back to Compose state

### Cleanup
- [ ] Remove `.sksl` resource files once all shaders are ported
- [ ] Remove `Shaders.kt` (`rememberShader`, `PIXEL_MELT_SHADER`, `FALLBACK_SHADER`)
- [ ] Remove Skia-specific imports and utilities (`uniformData`, `nativeCanvas`, `ImageFilter`)

## CI/CD

Pushes to `main` trigger a GitHub Actions workflow that builds the Wasm distribution and deploys it to GitHub Pages.
