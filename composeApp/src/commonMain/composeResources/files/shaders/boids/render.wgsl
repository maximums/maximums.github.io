// The murmuration (DESIGN.md §8): dark birds against a lit fog bank, a few catching the lamp light. One render pass:
// the fog bank as a full-screen triangle, then every bird as an instanced triangle. The vertex shader reads the boids
// straight from their storage buffer; there is no vertex buffer at all.

struct Boid {
    pos: vec2f,
    vel: vec2f,
}

struct View {
    world: vec2f,     // world size: world units map onto the target one to one
    birdSize: f32,    // a bird's length, world units
    time: f32,        // seconds, folded
    lamp: vec2f,      // unit direction towards the lamp: birds heading that way catch its light
    _pad: vec2f,
}

@group(0) @binding(0) var<uniform> view: View;
@group(0) @binding(1) var<storage, read> boids: array<Boid>;

// DESIGN.md colour tokens.
const NIGHT = vec3f(13.0, 16.0, 19.0) / 255.0;
const FOG = vec3f(140.0, 148.0, 152.0) / 255.0;
const LAMP = vec3f(216.0, 161.0, 90.0) / 255.0;
const BIRD = vec3f(16.0, 19.0, 23.0) / 255.0;

fn hash(p: vec2f) -> f32 {
    var q = fract(p * vec2f(123.34, 456.21));
    q += dot(q, q + 45.32);
    return fract(q.x * q.y);
}

fn valueNoise(p: vec2f) -> f32 {
    let cell = floor(p);
    let f = fract(p);
    let s = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(cell), hash(cell + vec2f(1.0, 0.0)), s.x), mix(hash(cell + vec2f(0.0, 1.0)), hash(cell + vec2f(1.0, 1.0)), s.x), s.y);
}

fn fbm(start: vec2f) -> f32 {
    var p = start;
    var sum = 0.0;
    var weight = 0.5;
    for (var i = 0; i < 4; i++) {
        sum += valueNoise(p) * weight;
        p = p * 2.07 + vec2f(3.1, 1.7);
        weight *= 0.5;
    }
    return sum / 0.9375;
}

struct FogOut {
    @builtin(position) position: vec4f,
    @location(0) uv: vec2f,
}

@vertex
fn fogVertex(@builtin(vertex_index) i: u32) -> FogOut {
    let corners = array(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
    let corner = corners[i];
    return FogOut(vec4f(corner, 0.0, 1.0), vec2f(corner.x * 0.5 + 0.5, 0.5 - corner.y * 0.5));
}

// A bank of fog lit from below by a street lamp: a bright band low across the sky, fading to Night above and below,
// drifting slowly; the lamp's warm glow in the bottom right corner.
@fragment
fn fogFragment(in: FogOut) -> @location(0) vec4f {
    let uv = in.uv;
    let n = fbm(uv * vec2f(3.0, 2.2) + vec2f(view.time * 0.012, view.time * 0.004));
    let band = exp(-pow((uv.y - 0.58) / 0.38, 2.0));
    let lampGlow = exp(-length((uv - vec2f(0.84, 1.0)) * vec2f(1.5, 2.4)) * 2.2);
    var color = mix(NIGHT, FOG * 0.9, clamp(band * (0.55 + 0.45 * n), 0.0, 1.0));
    color += LAMP * lampGlow * 0.38;
    return vec4f(color, 1.0);
}

struct BirdOut {
    @builtin(position) position: vec4f,
    @location(0) light: f32,
}

@vertex
fn birdVertex(@builtin(vertex_index) v: u32, @builtin(instance_index) i: u32) -> BirdOut {
    let boid = boids[i];
    let speed = length(boid.vel);
    let forward = select(vec2f(1.0, 0.0), boid.vel / speed, speed > 0.0);
    let side = vec2f(-forward.y, forward.x);
    // A slim arrowhead: the beak ahead, the wing tips behind.
    let shape = array(vec2f(0.6, 0.0), vec2f(-0.4, 0.3), vec2f(-0.4, -0.3));
    let corner = shape[v];
    let pos = boid.pos + (forward * corner.x + side * corner.y) * view.birdSize;
    let clip = vec2f(pos.x / view.world.x * 2.0 - 1.0, 1.0 - pos.y / view.world.y * 2.0);
    return BirdOut(vec4f(clip, 0.0, 1.0), smoothstep(0.86, 1.0, dot(forward, view.lamp)));
}

@fragment
fn birdFragment(in: BirdOut) -> @location(0) vec4f {
    return vec4f(mix(BIRD, LAMP, in.light * 0.75), 1.0);
}
