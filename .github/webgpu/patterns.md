# WebGPU Patterns

## Simulation phases

Break complex simulations into separate compute passes:

1. **state** — compute derived data (densities, neighbor caches)
2. **apply** — apply forces, rules, gradient steps
3. **integrate** — advance positions with time-step
4. **constrain** — enforce bounds, wrap-around, stability limits
5. **correct** — post-pass fixes, clamping, projection

Each phase is a separate `beginComputePass()` → `dispatchWorkgroups()` → `end()`.

## Ping-pong buffers/textures

For iterative effects (trails, blur, simulations), alternate read/write resources each frame:

```kotlin
var swap = false

fun frame() {
    val (input, output) = if (swap) textureA to textureB else textureB to textureA
    // Bind input as read, output as write
    // Dispatch compute or render
    swap = !swap
}
```

## Workgroup sizing

Common sizes: 64 (1D), 8×8 (2D grids). Make configurable for tuning:

```wgsl
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let i = gid.x;
    if (i >= arrayLength(&particles)) { return; }
    // ...
}
```

## Storage buffer layout

Use array-of-structs for per-element cohesion (most common in this project):

```wgsl
struct Particle {
    position: vec2<f32>,
    velocity: vec2<f32>,
};

@group(0) @binding(0) var<storage, read_write> particles: array<Particle>;
```

## Rendering pipeline patterns

### Intermediate textures for post-processing

- Pass 1: render scene to texture A
- Pass 2: compute/blur into texture B
- Pass 3: composite into swapchain

### Instancing

For large numbers of similar objects (particles, lines), use instancing and keep instance data in a storage buffer read from the vertex shader.

## Debugging

- Use `device.pushErrorScope("validation")` / `device.popErrorScope()` during development.
- Keep readbacks behind a debug flag — they stall the pipeline.
- Expose tuning knobs: workgroup size, max particle count, grid cell size.

## Common pitfalls

- **Alignment**: WGSL structs must align to 16 bytes. A `vec2<f32>` + `vec2<f32>` = 16 bytes ✓. A single `f32` field wastes 12 bytes of padding.
- **Bind group stability**: keep layouts stable across frames to avoid pipeline rebuilds.
- **Resource lifetimes**: call `.destroy()` on textures/buffers when no longer needed (especially per-frame MSAA textures).
- **Delta time clamping**: clamp `dt` to avoid unstable physics when the tab is backgrounded.
- **JsNumber conversions**: WebGPU APIs expect `JsNumber` — use `.toJsNumber()` for Int/Long parameters.

## Feature → pipeline mapping

| Feature | Pipeline | Notes |
|---------|----------|-------|
| Game of Life | Compute (ping-pong storage buffers) | Cell state as `u32` grid, rules in shader |
| Boids | Compute (spatial grid + particle buffer) | Position/velocity per agent, neighbor query via tiles |
| Background effects | Render (fullscreen quad + fragment shader) | Port AGSL shaders to WGSL fragment shaders |
| Page transitions | Render (post-process pass) | Sample previous frame texture, apply dissolve/melt |
| Particle trails | Compute + Render (ping-pong textures) | Fade texture each frame, render particles on top |
