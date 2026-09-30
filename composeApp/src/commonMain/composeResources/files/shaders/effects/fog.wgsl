// Fog on the window (DESIGN.md §9, "Breath on the window"): it rolls in from the corners over the scene being
// left, breathes while the next scene gets ready, and parts from the centre over the scene being entered.

struct Uniforms {
    resolution: vec2f,
    time: f32,
    amount: f32,   // how far this segment has gone, 0..1
    mode: f32,     // 0 rolls in, 1 holds, 2 clears
    side: f32,     // which scene shows through: 0 the one being left, 1 the one being entered
    _pad: vec2f,
}

@group(0) @binding(0) var<uniform> u: Uniforms;
@group(0) @binding(1) var fromScene: texture_2d<f32>;
@group(0) @binding(2) var toScene: texture_2d<f32>;
@group(0) @binding(3) var smoothSampler: sampler;

const FOG = vec3f(140.0, 148.0, 152.0) / 255.0; // DESIGN.md: scene fog

@vertex
fn vs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
    let corners = array(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
    return vec4f(corners[i], 0.0, 1.0);
}

fn hash(p: vec2f) -> f32 {
    var q = fract(p * vec2f(123.34, 456.21));
    q += dot(q, q + 45.32);
    return fract(q.x * q.y);
}

fn valueNoise(p: vec2f) -> f32 {
    let cell = floor(p);
    let f = fract(p);
    let s = f * f * (3.0 - 2.0 * f);
    let a = hash(cell);
    let b = hash(cell + vec2f(1.0, 0.0));
    let c = hash(cell + vec2f(0.0, 1.0));
    let d = hash(cell + vec2f(1.0, 1.0));
    return mix(mix(a, b, s.x), mix(c, d, s.x), s.y);
}

// Five octaves, normalised to 0..1.
fn fbm(start: vec2f) -> f32 {
    var p = start;
    var sum = 0.0;
    var weight = 0.5;
    for (var i = 0; i < 5; i++) {
        sum += valueNoise(p) * weight;
        p = p * 2.03 + vec2f(1.7, 9.2);
        weight *= 0.5;
    }
    return sum / 0.96875;
}

// The scene behind the glass, blurred where the fog is: 12 taps on a golden-angle spiral of radius `blur` pixels,
// turned per pixel so the taps' pattern dissolves into fine grain instead of showing as dots around sharp drops.
fn sceneBehind(position: vec2f, uv: vec2f, blur: f32) -> vec3f {
    let turn = hash(position) * 6.2831853;
    var leaving = vec3f(0.0);
    var entering = vec3f(0.0);
    for (var i = 0; i < 12; i++) {
        let radius = sqrt((f32(i) + 0.5) / 12.0) * blur;
        let angle = f32(i) * 2.3999632 + turn;
        let at = uv + vec2f(cos(angle), sin(angle)) * radius / u.resolution;
        leaving += textureSampleLevel(fromScene, smoothSampler, at, 0.0).rgb;
        entering += textureSampleLevel(toScene, smoothSampler, at, 0.0).rgb;
    }
    return mix(leaving, entering, u.side) / 12.0;
}

@fragment
fn fs(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let uv = position.xy / u.resolution;
    let aspect = u.resolution.x / u.resolution.y;
    let p = vec2f((uv.x - 0.5) * aspect, uv.y - 0.5);

    let drift = vec2f(u.time * 0.021, -u.time * 0.013);
    let density = fbm(p * 2.2 + drift) * 0.65 + fbm(p * 5.3 - drift * 1.7) * 0.35;
    // 0 in the centre, about 1 in the corners, frayed by the fog itself.
    let reach = length(p) / length(vec2f(aspect, 1.0) * 0.5) + (density - 0.5) * 0.45;

    var cover = 1.0;
    if (u.mode < 0.5) {
        let front = 1.3 - u.amount * 1.9; // from the corners inwards
        cover = smoothstep(front - 0.05, front + 0.3, reach);
    } else if (u.mode > 1.5) {
        let front = -0.35 + u.amount * 1.9; // from the centre outwards
        cover = smoothstep(front - 0.3, front + 0.05, reach);
    }

    // DESIGN.md: scene fog at 36–78 %, heavily blurred. Thicker where the noise is denser, breathing slowly.
    let breathing = 0.93 + 0.07 * sin(u.time * 0.8);
    let alpha = cover * mix(0.36, 0.78, smoothstep(0.25, 0.75, density)) * breathing;
    let color = mix(sceneBehind(position.xy, uv, cover * 14.0), FOG * (0.5 + 0.5 * density), alpha);
    return vec4f(color, 1.0);
}
