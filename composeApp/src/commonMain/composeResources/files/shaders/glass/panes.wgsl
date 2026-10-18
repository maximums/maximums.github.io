// The glass over the scene (DESIGN.md §5). The frame is copied to the canvas, then every pane Compose reports is drawn
// over it from the blurred frame, a little desaturated: wet glass. Compose draws the fog, condensation and hairline on
// top, so this is only what the glass does to the street behind it.
//
// A pane is a convex quad in window pixels (a triangle repeats a corner). How blurred it is follows how fogged it is:
// a cleared pane shows the street sharp. A wipe (the start of "Breath on the window") clears a band that sweeps across
// the pane from left to right.

struct Pane {
    a: vec4f,       // corners 0 and 1 (x, y, x, y)
    b: vec4f,       // corners 2 and 3
    style: vec4f,   // x: blur 0..1, y: visibility 0..1, z: wipe progress 0..1, w: unused
}

struct Frame {
    resolution: vec2f,
    _pad: vec2f,
}

@group(0) @binding(0) var frame: texture_2d<f32>;
@group(0) @binding(1) var smoothSampler: sampler;

struct CopyOut {
    @builtin(position) position: vec4f,
    @location(0) uv: vec2f,
}

@vertex
fn copyVs(@builtin(vertex_index) i: u32) -> CopyOut {
    let corners = array(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
    let corner = corners[i];
    return CopyOut(vec4f(corner, 0.0, 1.0), vec2f(corner.x * 0.5 + 0.5, 0.5 - corner.y * 0.5));
}

@fragment
fn copyFs(in: CopyOut) -> @location(0) vec4f {
    return textureSample(frame, smoothSampler, in.uv);
}

@group(0) @binding(2) var blurred: texture_2d<f32>;
@group(0) @binding(3) var<uniform> view: Frame;
@group(0) @binding(4) var<storage, read> panes: array<Pane>;

struct PaneOut {
    @builtin(position) position: vec4f,
    @location(0) @interpolate(flat) instance: u32,
    @location(1) @interpolate(flat) span: vec2f,   // the pane's left and right edges, for the wipe
}

@vertex
fn paneVs(@builtin(vertex_index) i: u32, @builtin(instance_index) instance: u32) -> PaneOut {
    let pane = panes[instance];
    let corners = array(pane.a.xy, pane.a.zw, pane.b.xy, pane.b.zw);
    let order = array(0u, 1u, 2u, 0u, 2u, 3u);
    let p = corners[order[i]];
    let left = min(min(corners[0].x, corners[1].x), min(corners[2].x, corners[3].x));
    let right = max(max(corners[0].x, corners[1].x), max(corners[2].x, corners[3].x));
    let clip = vec2f(p.x / view.resolution.x * 2.0 - 1.0, 1.0 - p.y / view.resolution.y * 2.0);
    return PaneOut(vec4f(clip, 0.0, 1.0), instance, vec2f(left, right));
}

// How cleared the wipe has left a point [d] of the way across (0 left, 1 right): a soft band sweeps across, and
// what it has passed stays clear.
fn wiped(progress: f32, d: f32) -> f32 {
    let softness = 0.3;
    let front = progress * (1.0 + softness);
    return 1.0 - smoothstep(front - softness, front, d);
}

@fragment
fn paneFs(in: PaneOut) -> @location(0) vec4f {
    let pane = panes[in.instance];
    let uv = in.position.xy / view.resolution;
    let across = clamp((in.position.x - in.span.x) / max(in.span.y - in.span.x, 1.0), 0.0, 1.0);
    let blur = pane.style.x * (1.0 - wiped(pane.style.z, across));
    let sharp = textureSample(frame, smoothSampler, uv).rgb;
    let soft = textureSample(blurred, smoothSampler, uv).rgb;
    var colour = mix(sharp, soft, blur);
    // DESIGN.md §5: saturation 0.8 behind fog.
    let luma = dot(colour, vec3f(0.2126, 0.7152, 0.0722));
    colour = mix(vec3f(luma), colour, 1.0 - 0.2 * blur);
    return vec4f(colour, pane.style.y);
}
