// The street at full resolution: the background (street.wgsl, drawn at a fraction of the pixels) scaled up with
// linear filtering, then what has to stay sharp: the rain, in two layers of slanting streaks (near ones long and
// bright, far ones short and faint) that catch the light where they cross a lamp; the vignette; and a trace of dither
// so the dark gradients don't band. Rain lengths, spacing and speeds are the design study's, in its 1440 × 900 space.

struct Frame {
    resolution: vec2f,
    time: f32,
    _pad: f32,
}

@group(0) @binding(0) var source: texture_2d<f32>;
@group(0) @binding(1) var smoothSampler: sampler;
@group(0) @binding(2) var<uniform> frame: Frame;

const NEAR = vec3f(222.0, 228.0, 230.0) / 255.0;
const FAR = vec3f(200.0, 210.0, 214.0) / 255.0;
const NIGHT = vec3f(8.0, 10.0, 12.0) / 255.0;
const DEGREES = 0.017453292;

struct Out {
    @builtin(position) position: vec4f,
    @location(0) uv: vec2f,
}

@vertex
fn vs(@builtin(vertex_index) i: u32) -> Out {
    let corners = array(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
    let corner = corners[i];
    return Out(vec4f(corner, 0.0, 1.0), vec2f(corner.x * 0.5 + 0.5, 0.5 - corner.y * 0.5));
}

fn hash1(n: f32) -> f32 {
    return fract(sin(n * 127.1) * 43758.5453);
}

fn hash2(p: vec2f) -> f32 {
    return fract(sin(dot(p, vec2f(127.1, 311.7))) * 43758.5453);
}

// One layer of rain at [p] (study px): a lane at a random place in every [cell].x-wide column, streaks [halfLength]
// long and [halfWidth] wide, one in most [cell].y-tall stretches of the lane, falling [speed] px a second and leaning
// [angle]. 1 on a streak's axis, fading to 0 at its edge.
fn rain(p: vec2f, cell: vec2f, halfLength: f32, halfWidth: f32, speed: f32, angle: f32, t: f32, seed: f32) -> f32 {
    let c = cos(angle);
    let s = sin(angle);
    let q = vec2f(c * p.x + s * p.y, c * p.y - s * p.x);
    let column = floor(q.x / cell.x);
    let lane = (0.15 + 0.7 * hash1(column + seed)) * cell.x;
    let dx = q.x - column * cell.x - lane;
    let y = q.y - t * speed + hash1(column * 1.7 + seed) * cell.y * 13.0;
    let row = floor(y / cell.y);
    let present = step(hash2(vec2f(column, row) + seed), 0.82);
    let dy = (fract(y / cell.y) - 0.5) * cell.y;
    return present * clamp(1.0 - length(vec2f(dx / halfWidth, dy / halfLength)), 0.0, 1.0);
}

@fragment
fn fs(in: Out) -> @location(0) vec4f {
    var colour = textureSample(source, smoothSampler, in.uv).rgb;
    let p = in.position.xy * (900.0 / frame.resolution.y);
    let t = frame.time;

    // Rain shows most where there's light behind it.
    let lit = smoothstep(0.03, 0.3, dot(colour, vec3f(0.2126, 0.7152, 0.0722)));
    let glint = mix(0.65, 1.5, lit);

    let near = rain(p, vec2f(47.0, 160.0), 28.0, 1.0, 229.0, 8.0 * DEGREES, t, 0.0) * 0.46
        + rain(p, vec2f(71.0, 230.0), 22.0, 1.0, 329.0, 8.0 * DEGREES, t, 3.0) * 0.32
        + rain(p, vec2f(29.0, 120.0), 16.0, 1.0, 171.0, 8.0 * DEGREES, t, 7.0) * 0.24;
    let far = rain(p, vec2f(23.0, 110.0), 12.0, 1.0, 100.0, 5.0 * DEGREES, t, 11.0) * 0.22
        + rain(p, vec2f(37.0, 120.0), 9.0, 1.0, 109.0, 5.0 * DEGREES, t, 13.0) * 0.16;
    colour = mix(colour, FAR, clamp(far * 0.85 * glint, 0.0, 1.0));
    colour = mix(colour, NEAR, clamp(near * 0.85 * glint, 0.0, 1.0));

    // The vignette: clear in the middle, Night at 75 % at the far corners.
    let edge = length((in.uv - vec2f(0.5, 0.45)) / vec2f(0.75, 0.7));
    colour = mix(colour, NIGHT, 0.75 * clamp((edge - 0.55) / 0.45, 0.0, 1.0));

    // Dither: half a step of noise, so slow dark gradients don't show bands.
    colour += (hash2(in.position.xy) - 0.5) / 255.0;
    return vec4f(colour, 1.0);
}
