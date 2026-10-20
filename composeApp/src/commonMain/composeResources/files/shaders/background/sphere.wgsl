// The sphere behind the About page (SphereRenderer.kt), a GPU-backed widget: Compose reports where the element is, and
// the scene draws there, under it. One quad per element: a raytraced sphere lit by a circling light, the old MY_TRY
// Skia shader.

struct Widget {
    rect: vec4f,    // x, y, width, height in target pixels
    style: vec4f,   // x: visibility 0..1, yzw: unused
}

struct Frame {
    resolution: vec2f,
    time: f32,      // seconds, folded
    _pad: f32,
}

@group(0) @binding(0) var<uniform> frame: Frame;
@group(0) @binding(1) var<storage, read> widgets: array<Widget>;

struct Out {
    @builtin(position) position: vec4f,
    @location(0) local: vec2f,                     // 0..1 across the element, y down
    @location(1) @interpolate(flat) index: u32,
}

@vertex
fn vs(@builtin(vertex_index) v: u32, @builtin(instance_index) i: u32) -> Out {
    let corners = array(vec2f(0.0, 0.0), vec2f(1.0, 0.0), vec2f(0.0, 1.0), vec2f(0.0, 1.0), vec2f(1.0, 0.0), vec2f(1.0, 1.0));
    let corner = corners[v];
    let widget = widgets[i];
    let pixel = widget.rect.xy + corner * widget.rect.zw;
    let clip = vec2f(pixel.x / frame.resolution.x * 2.0 - 1.0, 1.0 - pixel.y / frame.resolution.y * 2.0);
    return Out(vec4f(clip, 0.0, 1.0), corner, i);
}

@fragment
fn fs(in: Out) -> @location(0) vec4f {
    let widget = widgets[in.index];
    let size = widget.rect.zw;

    // As MY_TRY: centred, y up, aspect-corrected by height.
    var uv = vec2f(in.local.x, 1.0 - in.local.y) - 0.5;
    uv.x *= size.x / size.y;

    let t = frame.time;
    let camera = vec3f(0.0);
    let light = normalize(vec3f(sin(t), 1.0, -cos(t)));
    let ray = normalize(vec3f(uv, 1.0) - camera);
    let centre = vec3f(0.0, 0.0, 5.0);
    let toCamera = camera - centre;
    let b = 2.0 * dot(ray, toCamera);
    let c = dot(toCamera, toCamera) - 1.0;
    let discriminant = b * b - 4.0 * c;

    // A soft rim one pixel wide instead of MY_TRY's hard step. (Derivatives first, while control flow is uniform.)
    let rim = max(fwidth(discriminant), 1e-6);
    let coverage = smoothstep(0.0, rim * 1.5, discriminant);

    let distance = (-b - sqrt(max(discriminant, 0.0))) / 2.0;
    let normal = normalize(camera + distance * ray - centre);
    let brightness = max(dot(normal, light), 0.0) * 0.9 + 0.1;
    return vec4f(vec3f(brightness), coverage * widget.style.x);
}
