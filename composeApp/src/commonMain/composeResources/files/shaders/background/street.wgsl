// The street behind the glass, from the design canvas's background study: a dark sky warming towards the ground, an
// amber glow low in the middle, the soft lights of a street at night breathing slowly (StreetLights.kt places them),
// their reflections in the wet road, and fog drifting across. Heavy, not fast: nothing here moves quickly.
//
// It's drawn at the viewer's quality (a quarter, half or all of the pixels): it's all soft. The rain, the vignette
// and the dither come at full resolution, in rain.wgsl.
//
// The colours are the theme's (common/theme.wgsl): its sky, its street lights by kind, its fog.
//
// Positions are in the study's 1440 × 900 space: the viewport shows it at the same height, centred, so a wider window
// sees more street at the sides. A tall window would see only the middle, with the lamps cut off at its sides, so
// there the street is drawn narrower: positions move in towards the middle, the lights keep their size and shape.

struct Uniforms {
    resolution: vec2f,
    time: f32,   // seconds, on the ambient clock (it stands still with reduced motion)
    _pad: f32,
}

struct Light {
    place: vec4f,    // x, y, size (study px), blur (study px)
    look: vec4f,     // x: its kind, which of theme.lights it is; w: alpha at the rim
    extra: vec4f,    // x: breath delay (s), y: 1 if the wet road reflects it
}

@group(0) @binding(0) var<uniform> u: Uniforms;
@group(0) @binding(1) var<storage, read> lights: array<Light>;

const STUDY = vec2f(1440.0, 900.0);

@vertex
fn vs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
    let corners = array(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
    return vec4f(corners[i], 0.0, 1.0);
}

fn over(base: vec3f, colour: vec3f, alpha: f32) -> vec3f {
    return mix(base, colour, clamp(alpha, 0.0, 1.0));
}

// The sky: the theme's four stops, from the top down to the ground.
fn sky(y: f32) -> vec3f {
    let c0 = theme.sky[0].rgb;
    let c1 = theme.sky[1].rgb;
    let c2 = theme.sky[2].rgb;
    let c3 = theme.sky[3].rgb;
    if (y < 0.48) {
        return mix(c0, c1, y / 0.48);
    }
    if (y < 0.78) {
        return mix(c1, c2, (y - 0.48) / 0.30);
    }
    return mix(c2, c3, (y - 0.78) / 0.22);
}

// 0 → 1 → 0 over [period] seconds, eased at both ends: a breath, a drift there and back.
fn pingPong(t: f32, period: f32) -> f32 {
    let phase = fract(t / period);
    return smoothstep(0.0, 1.0, 1.0 - abs(phase * 2.0 - 1.0));
}

// A light out of focus, as the study draws it: an even glow, a slightly brighter rim near the edge, nothing past it.
// [r] is the distance from the centre in radii; [softness] widens the edge (the nearer the light, the softer).
fn disc(r: f32, softness: f32, rim: f32) -> f32 {
    let body = mix(rim * 0.55, rim, smoothstep(0.735, 0.89, r));
    return body * (1.0 - smoothstep(0.89 - softness, 1.0 + softness, r));
}

fn hash(p: vec2f) -> f32 {
    return fract(sin(dot(p, vec2f(127.1, 311.7))) * 43758.5453);
}

fn valueNoise(p: vec2f) -> f32 {
    let i = floor(p);
    let f = fract(p);
    let s = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2f(1.0, 0.0)), s.x), mix(hash(i + vec2f(0.0, 1.0)), hash(i + vec2f(1.0, 1.0)), s.x), s.y);
}

fn fbm(p: vec2f) -> f32 {
    var sum = 0.0;
    var amplitude = 0.5;
    var q = p;
    for (var i = 0; i < 4; i++) {
        sum += amplitude * valueNoise(q);
        q = q * 2.03 + vec2f(17.0, 9.0);
        amplitude *= 0.5;
    }
    return sum;
}

// How much narrower the street is drawn on a tall window: 1 on a landscape one, a little over 2 on a phone.
fn squeeze() -> f32 {
    return max(1.0, 0.65 * (STUDY.x / STUDY.y) / (u.resolution.x / u.resolution.y));
}

// [x] in the study, moved in towards the middle by [squeeze].
fn narrowed(x: f32, squeeze: f32) -> f32 {
    return 720.0 + (x - 720.0) / squeeze;
}

// A bank of fog: a soft ellipse in [rect] (left, top, width, height), drifting 280 px across and back over 84 s.
fn fogBank(p: vec2f, rect: vec4f, delay: f32, t: f32, squeeze: f32) -> f32 {
    let d = pingPong(t + delay, 84.0);
    let centre = vec2f(narrowed(rect.x + rect.z * 0.5 + mix(-140.0, 140.0, d), squeeze), rect.y + rect.w * 0.5 + mix(0.0, -24.0, d));
    let r = length((p - centre) / (vec2f(rect.z / squeeze, rect.w) * 0.5));
    let fade = clamp(1.0 - r, 0.0, 1.0);
    return 0.2 * fade * fade * (3.0 - 2.0 * fade);
}

@fragment
fn fs(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let toStudy = STUDY.y / u.resolution.y;
    let p = (frag.xy - u.resolution * 0.5) * toStudy + STUDY * 0.5;
    let t = u.time;
    let narrow = squeeze();

    var colour = sky(frag.y / u.resolution.y);

    // Lamplight on the wet ground, low in the middle.
    let glow = length((p - vec2f(720.0, 900.0)) / vec2f(864.0 / narrow, 266.0));
    colour = over(colour, theme.lights[0].rgb, 0.16 * (1.0 - clamp(glow / 0.7, 0.0, 1.0)));

    for (var i = 0u; i < arrayLength(&lights); i++) {
        let light = lights[i];
        let tint = theme.lights[u32(light.look.x)].rgb;
        let breath = pingPong(t + light.extra.x, 14.0);
        let radius = light.place.z * 0.5 * (0.97 + 0.06 * breath);
        let shown = 0.6 + 0.4 * breath;
        let softness = (light.place.w + toStudy) / radius;
        let centre = vec2f(narrowed(light.place.x, narrow), light.place.y);
        let r = length(p - centre) / radius;
        if (r < 1.0 + softness) {
            colour = over(colour, tint, disc(r, softness, light.look.w) * shown);
        }
        // Car lights are low over the road: the wet asphalt stretches each into a faint, rippling column below it.
        if (light.extra.y > 0.5) {
            let q = (p - centre - vec2f(0.0, radius * 2.4)) / vec2f(radius * 0.5, radius * 2.0);
            let column = 1.0 - smoothstep(0.0, 1.0, length(q));
            if (column > 0.0) {
                let ripple = 0.75 + 0.25 * sin(p.y * 0.3 + t * 1.1 + light.extra.x * 3.0);
                colour = over(colour, tint, light.look.w * 0.22 * column * ripple * shown);
            }
        }
    }

    // Fog: four slow banks, with a grain of noise so they read as fog rather than ellipses.
    var fog = fogBank(p, vec4f(-200.0, 380.0, 1100.0, 360.0), 0.0, t, narrow);
    fog += fogBank(p, vec4f(600.0, 300.0, 1200.0, 420.0), 18.0, t, narrow);
    fog += fogBank(p, vec4f(200.0, 620.0, 1300.0, 320.0), 30.0, t, narrow);
    fog += fogBank(p, vec4f(900.0, 60.0, 900.0, 300.0), 9.0, t, narrow) * 0.6;
    let texture = 0.55 + 0.9 * fbm(p * 0.0035 + vec2f(t * 0.008, t * 0.0025));
    colour = over(colour, theme.streetFog.rgb, fog * 0.8 * texture);

    return vec4f(colour, 1.0);
}
