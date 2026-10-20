// The maple on the About page (BonsaiRenderer.kt), in two steps:
// - vsModel/fsModel draw the model into a target of its own, the size of its widget, multisampled and cleared to
//   transparent (ModelTarget.kt);
// - vsComposite/fsComposite lay that target over the street, in the background's final pass, premultiplied.
//
// The look (PLAN Phase 10): close to a silhouette. The street's lamp (the theme's) lights it from one side and
// catches its edges, the night gives it a faint fill from above, and its far side sinks into the mist. The lighting
// is done in linear light and written out encoded, the way everything else on the site draws.

struct Camera {
    viewProjection: mat4x4f,
    view: mat4x4f,
    eye: vec4f,      // xyz: where the camera is (world)
    lamp: vec4f,     // xyz: towards the lamp (world, unit length)
    depth: vec4f,    // x, y: the view depth of the tree's nearest and farthest points, for the mist; z, w: the
                     // height of its bottom and top (world), for the lamp's reach
}

@group(0) @binding(0) var<uniform> camera: Camera;
@group(0) @binding(1) var<storage, read> instances: array<mat4x4f>;

@group(2) @binding(0) var baseColor: texture_2d<f32>;   // sRGB: samples are linear light
@group(2) @binding(1) var normalMap: texture_2d<f32>;
@group(2) @binding(2) var surface: sampler;

struct Vertex {
    @location(0) position: vec3f,
    @location(1) normal: vec3f,
    @location(2) tangent: vec4f,
    @location(3) uv: vec2f,
}

struct Shaded {
    @builtin(position) position: vec4f,
    @location(0) world: vec3f,
    @location(1) normal: vec3f,
    @location(2) tangent: vec4f,
    @location(3) uv: vec2f,
}

@vertex
fn vsModel(vertex: Vertex, @builtin(instance_index) instance: u32) -> Shaded {
    let m = instances[instance];
    let linear = mat3x3f(m[0].xyz, m[1].xyz, m[2].xyz);
    // Normals turn by the inverse transpose. The cofactor matrix is that times the determinant, with no inverse to
    // take; the scale goes in the normalize. (Its sign can flip a mirrored normal: both sides are lit alike below.)
    let cofactor = mat3x3f(cross(linear[1], linear[2]), cross(linear[2], linear[0]), cross(linear[0], linear[1]));
    let world = m * vec4f(vertex.position, 1.0);
    var out: Shaded;
    out.position = camera.viewProjection * world;
    out.world = world.xyz;
    out.normal = cofactor * vertex.normal;
    out.tangent = vec4f(linear * vertex.tangent.xyz, vertex.tangent.w);
    out.uv = vertex.uv;
    return out;
}

fn toLinear(c: vec3f) -> vec3f {
    return pow(c, vec3f(2.2));
}

fn toEncoded(c: vec3f) -> vec3f {
    return pow(max(c, vec3f(0.0)), vec3f(1.0 / 2.2));
}

@fragment
fn fsModel(in: Shaded, @builtin(front_facing) front: bool) -> @location(0) vec4f {
    // Sampled first, while control flow is uniform.
    let albedo = textureSample(baseColor, surface, in.uv).rgb;
    let bump = textureSample(normalMap, surface, in.uv).xyz * 2.0 - 1.0;

    // A leaf is one sheet seen from both sides: the side towards the camera is the one lit.
    var n = normalize(in.normal);
    if (!front) {
        n = -n;
    }
    let t = normalize(in.tangent.xyz - n * dot(n, in.tangent.xyz));
    let b = cross(n, t) * in.tangent.w;
    n = normalize(mat3x3f(t, b, n) * bump);

    let toEye = normalize(camera.eye.xyz - in.world);
    let toLamp = camera.lamp.xyz;
    let lamp = toLinear(theme.lights[0].rgb);
    let night = toLinear(theme.sky[1].rgb);

    // The lamp is high: its light catches the crown and falls off down the trunk, leaving the pot in the night.
    let height = clamp((in.world.y - camera.depth.z) / (camera.depth.w - camera.depth.z), 0.0, 1.0);
    let reach = mix(0.25, 1.0, smoothstep(0.1, 0.75, height));
    // The lamp, wrapped a little, as light comes through thin leaves; the night, a faint fill from above.
    let lit = max((dot(n, toLamp) + 0.25) / 1.25, 0.0) * reach;
    let fill = 0.5 + 0.5 * n.y;
    // The edges towards the lamp catch it: strongest where the surface turns away from the camera.
    let rim = pow(1.0 - clamp(dot(n, toEye), 0.0, 1.0), 3.0) * clamp(dot(n, toLamp) * 0.5 + 0.6, 0.0, 1.0) * reach;

    var colour = albedo * (lamp * lit * 0.38 + night * fill * 2.0) + lamp * rim * 0.5;

    // The far side sinks into the mist.
    let depth = -(camera.view * vec4f(in.world, 1.0)).z;
    let far = smoothstep(camera.depth.x, camera.depth.y, depth);
    colour = mix(colour, toLinear(theme.fog.rgb) * 0.06, far * 0.6);

    return vec4f(toEncoded(colour), 1.0);
}

// The composite. Its bindings are group 0's 2 and 3, apart from the model's 0 and 1: each pipeline's layout holds
// only what its own entry points use.

struct Placement {
    rect: vec4f,     // where the widget is: x, y, width, height in target pixels
    frame: vec4f,    // xy: the final pass's target size; z: the widget's visibility
}

@group(0) @binding(2) var<uniform> placement: Placement;
@group(0) @binding(3) var drawn: texture_2d<f32>;

struct Placed {
    @builtin(position) position: vec4f,
}

@vertex
fn vsComposite(@builtin(vertex_index) v: u32) -> Placed {
    let corners = array(vec2f(0.0, 0.0), vec2f(1.0, 0.0), vec2f(0.0, 1.0), vec2f(0.0, 1.0), vec2f(1.0, 0.0), vec2f(1.0, 1.0));
    let pixel = placement.rect.xy + corners[v] * placement.rect.zw;
    return Placed(vec4f(pixel.x / placement.frame.x * 2.0 - 1.0, 1.0 - pixel.y / placement.frame.y * 2.0, 0.0, 1.0));
}

@fragment
fn fsComposite(in: Placed) -> @location(0) vec4f {
    // The target is the widget's size: one texel per pixel, no filtering.
    let size = vec2i(textureDimensions(drawn));
    let texel = clamp(vec2i(in.position.xy - placement.rect.xy), vec2i(0), size - 1);
    return textureLoad(drawn, texel, 0) * placement.frame.z;
}
