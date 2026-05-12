# WGSL Shader Templates

Ready-to-use WGSL templates for this project's planned WebGPU features.

## Fullscreen fragment shader (background effect)

Use for porting AGSL/SKSL background shaders (bokeh, wet_neural_network, etc.) to WebGPU.

```wgsl
struct Uniforms {
    resolution: vec2<f32>,
    time: f32,
    _pad: f32,
};

@group(0) @binding(0) var<uniform> u: Uniforms;

struct VSOut {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
};

@vertex
fn vs_main(@builtin(vertex_index) vid: u32) -> VSOut {
    // Fullscreen triangle (3 vertices, no vertex buffer needed)
    let x = f32(i32(vid & 1u)) * 4.0 - 1.0;
    let y = f32(i32(vid >> 1u)) * 4.0 - 1.0;
    var out: VSOut;
    out.position = vec4<f32>(x, y, 0.0, 1.0);
    out.uv = vec2<f32>(x * 0.5 + 0.5, 1.0 - (y * 0.5 + 0.5));
    return out;
}

@fragment
fn fs_main(in: VSOut) -> @location(0) vec4<f32> {
    let uv = in.uv;
    // --- Your effect here ---
    return vec4<f32>(uv.x, uv.y, sin(u.time) * 0.5 + 0.5, 1.0);
}
```

## Game of Life compute

Ping-pong pattern: read from `cellsIn`, write to `cellsOut`, swap each frame.

```wgsl
struct Params {
    width: u32,
    height: u32,
};

@group(0) @binding(0) var<uniform> params: Params;
@group(0) @binding(1) var<storage, read> cellsIn: array<u32>;
@group(0) @binding(2) var<storage, read_write> cellsOut: array<u32>;

fn idx(x: u32, y: u32) -> u32 {
    return y * params.width + x;
}

fn countNeighbors(x: u32, y: u32) -> u32 {
    var count: u32 = 0u;
    for (var dy: i32 = -1; dy <= 1; dy++) {
        for (var dx: i32 = -1; dx <= 1; dx++) {
            if (dx == 0 && dy == 0) { continue; }
            let nx = (i32(x) + dx + i32(params.width)) % i32(params.width);
            let ny = (i32(y) + dy + i32(params.height)) % i32(params.height);
            count += cellsIn[idx(u32(nx), u32(ny))];
        }
    }
    return count;
}

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let x = gid.x;
    let y = gid.y;
    if (x >= params.width || y >= params.height) { return; }

    let alive = cellsIn[idx(x, y)];
    let neighbors = countNeighbors(x, y);

    var next: u32 = 0u;
    if (alive == 1u && (neighbors == 2u || neighbors == 3u)) { next = 1u; }
    if (alive == 0u && neighbors == 3u) { next = 1u; }

    cellsOut[idx(x, y)] = next;
}
```

## Particle simulation compute

Basic integrate-and-wrap for a particle buffer.

```wgsl
struct Particle {
    pos: vec2<f32>,
    vel: vec2<f32>,
};

@group(0) @binding(0) var<storage, read_write> particles: array<Particle>;

struct Uniforms {
    dt: f32,
    _pad: vec3<f32>,
};

@group(0) @binding(1) var<uniform> u: Uniforms;

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let i = gid.x;
    if (i >= arrayLength(&particles)) { return; }

    var p = particles[i];
    p.pos += p.vel * u.dt;

    // Wrap in clip space
    if (p.pos.x > 1.0) { p.pos.x = -1.0; }
    if (p.pos.x < -1.0) { p.pos.x = 1.0; }
    if (p.pos.y > 1.0) { p.pos.y = -1.0; }
    if (p.pos.y < -1.0) { p.pos.y = 1.0; }

    particles[i] = p;
}
```

## Particle rendering (vertex + fragment)

Reads particle positions from a storage buffer; renders as point-list.

```wgsl
struct Particle {
    pos: vec2<f32>,
    vel: vec2<f32>,
};

@group(0) @binding(0) var<storage, read> particles: array<Particle>;

struct VSOut {
    @builtin(position) position: vec4<f32>,
};

@vertex
fn vs_main(@builtin(vertex_index) vid: u32) -> VSOut {
    let p = particles[vid];
    var out: VSOut;
    out.position = vec4<f32>(p.pos, 0.0, 1.0);
    return out;
}

@fragment
fn fs_main() -> @location(0) vec4<f32> {
    return vec4<f32>(1.0, 1.0, 1.0, 1.0);
}
```

## Trail fade compute (ping-pong texture)

Fades texture by multiplying each pixel's alpha. Alternate input/output textures each frame.

```wgsl
@group(0) @binding(0) var inputTex: texture_2d<f32>;
@group(0) @binding(1) var outputTex: texture_storage_2d<rgba16float, write>;

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let uv = vec2<i32>(gid.xy);
    let color = textureLoad(inputTex, uv, 0);
    textureStore(outputTex, uv, color * 0.97);
}
```
