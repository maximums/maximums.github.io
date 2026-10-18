// Draws a texture over the whole target with linear filtering: a scene rendered at a fraction of the resolution,
// scaled back up. The bokeh is soft by nature, so a quarter of the pixels look much the same.

@group(0) @binding(0) var source: texture_2d<f32>;
@group(0) @binding(1) var smoothSampler: sampler;

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

@fragment
fn fs(in: Out) -> @location(0) vec4f {
    return textureSample(source, smoothSampler, in.uv);
}
