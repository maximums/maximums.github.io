// One mip level from the level above it (Mipmaps.kt): a triangle over the whole level, each pixel sampling the level
// above at the corner between four of its texels, so the linear filter averages that 2×2 block. On an sRGB texture
// the sampler decodes and the target encodes, so the average is of light, not of stored numbers.

@group(0) @binding(0) var above: texture_2d<f32>;
@group(0) @binding(1) var smoothSampler: sampler;

struct Out {
    @builtin(position) position: vec4f,
    @location(0) uv: vec2f,
}

@vertex
fn vs(@builtin(vertex_index) i: u32) -> Out {
    // (0, 0), (2, 0), (0, 2) in texture coordinates: one triangle covering the target, y down like a texture.
    let uv = vec2f(f32((i << 1u) & 2u), f32(i & 2u));
    return Out(vec4f(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, 0.0, 1.0), uv);
}

@fragment
fn fs(in: Out) -> @location(0) vec4f {
    return textureSample(above, smoothSampler, in.uv);
}
