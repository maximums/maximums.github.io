// A dual Kawase blur (Marius Bjørge, "Bandwidth-efficient rendering", SIGGRAPH 2015): the frame goes down a few
// half-size steps and back up, each step a handful of bilinear taps. Wide and smooth for very little work: the glass
// panes' frosted view of the street.

struct Params {
    texel: vec2f,   // one texel of the source, in uv
    offset: f32,    // how far apart the taps are, in source texels
    _pad: f32,
}

@group(0) @binding(0) var source: texture_2d<f32>;
@group(0) @binding(1) var smoothSampler: sampler;
@group(0) @binding(2) var<uniform> params: Params;

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

// Half size: the centre and the four diagonal neighbours (each tap already averages four texels).
@fragment
fn down(in: Out) -> @location(0) vec4f {
    let o = params.texel * params.offset;
    var colour = textureSample(source, smoothSampler, in.uv) * 4.0;
    colour += textureSample(source, smoothSampler, in.uv - o);
    colour += textureSample(source, smoothSampler, in.uv + o);
    colour += textureSample(source, smoothSampler, in.uv + vec2f(o.x, -o.y));
    colour += textureSample(source, smoothSampler, in.uv - vec2f(o.x, -o.y));
    return colour / 8.0;
}

// Double size: a ring of eight taps around the point, the diagonal ones weighted twice.
@fragment
fn up(in: Out) -> @location(0) vec4f {
    let o = params.texel * params.offset;
    var colour = textureSample(source, smoothSampler, in.uv + vec2f(-o.x * 2.0, 0.0));
    colour += textureSample(source, smoothSampler, in.uv + vec2f(-o.x, o.y)) * 2.0;
    colour += textureSample(source, smoothSampler, in.uv + vec2f(0.0, o.y * 2.0));
    colour += textureSample(source, smoothSampler, in.uv + vec2f(o.x, o.y)) * 2.0;
    colour += textureSample(source, smoothSampler, in.uv + vec2f(o.x * 2.0, 0.0));
    colour += textureSample(source, smoothSampler, in.uv + vec2f(o.x, -o.y)) * 2.0;
    colour += textureSample(source, smoothSampler, in.uv + vec2f(0.0, -o.y * 2.0));
    colour += textureSample(source, smoothSampler, in.uv + vec2f(-o.x, -o.y)) * 2.0;
    return colour / 12.0;
}
