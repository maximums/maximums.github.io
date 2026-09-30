// A rainy street at night seen through a wet window: bokeh of street lamps, head lights, tail lights and distant
// windows, bent by drops running down the glass. A straight port of bokeh.sksl.

struct Uniforms {
    resolution: vec2f,
    time: f32,   // seconds, folded by the ambient clock
    _pad: f32,
}

@group(0) @binding(0) var<uniform> u: Uniforms;

const LOOP_STEP = 0.1;

@vertex
fn vs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
    // One triangle larger than the viewport covers every pixel.
    let corners = array(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
    return vec4f(corners[i], 0.0, 1.0);
}

struct Ray {
    origin: vec3f,
    direction: vec3f,
}

// smoothstep as SkSL defines it, which the scene relies on with reversed edges (low > high).
fn ramp(low: f32, high: f32, x: f32) -> f32 {
    let t = clamp((x - low) / (high - low), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

fn getRay(uv: vec2f, cameraPos: vec3f, lookAt: vec3f, zoom: f32) -> Ray {
    let forwardV = normalize(lookAt - cameraPos);
    let rightV = cross(vec3f(0.0, 1.0, 0.0), forwardV);
    let upV = cross(forwardV, rightV);
    let center = cameraPos + forwardV * zoom;
    let intersection = center + uv.x * rightV + uv.y * upV;
    return Ray(cameraPos, normalize(intersection - cameraPos));
}

fn noise(num: f32) -> f32 {
    return fract(sin(num * 3456.0) * 6547.0);
}

fn noiseVec4(num: f32) -> vec4f {
    return fract(sin(num * vec4f(123.0, 1024.0, 3456.0, 9564.0)) * vec4f(6547.0, 345.0, 8799.0, 1564.0));
}

fn closestPoint(ray: Ray, point: vec3f) -> vec3f {
    return ray.origin + max(0.0, dot(point - ray.origin, ray.direction)) * ray.direction;
}

fn distanceToRay(ray: Ray, point: vec3f) -> f32 {
    return length(point - closestPoint(ray, point));
}

fn bokeh(ray: Ray, point: vec3f, baseSize: f32, blur: f32) -> f32 {
    let size = baseSize * length(point);
    let d = distanceToRay(ray, point);
    var circle = ramp(size, size * (1.0 - blur), d);
    circle *= mix(0.6, 1.0, ramp(size * 0.8, size, d));
    return circle;
}

fn streetLights(rayIn: Ray, time: f32) -> vec3f {
    var ray = rayIn;
    var mask = 0.0;
    let side = step(ray.direction.x, 0.0);
    ray.direction.x = abs(ray.direction.x);

    for (var i = 0.0; i < 1.0; i += LOOP_STEP) {
        let t = fract(i + time + side * LOOP_STEP * 0.5);
        let point = vec3f(2.0, 2.0, 100.0 - t * 100.0);
        mask += bokeh(ray, point, 0.05, 0.1) * t * t * t;
    }

    return vec3f(1.0, 0.7, 0.3) * mask;
}

fn headLights(ray: Ray, timeIn: f32) -> vec3f {
    let time = timeIn * 2.0;
    var mask = 0.0;
    let width0 = 0.25;
    let width1 = width0 * 1.2;

    for (var i = 0.0; i < 1.0; i += 1.0 / 30.0) {
        if (noise(i) > 0.1) {
            continue;
        }

        let t = fract(i + time);
        let z = 100.0 - t * 100.0;
        let fade = t * t * t * t * t;
        let focus = ramp(0.9, 1.0, t);
        let size = mix(0.05, 0.03, focus);

        mask += bokeh(ray, vec3f(-1.0 - width0, 0.15, z), size, 0.1) * fade;
        mask += bokeh(ray, vec3f(-1.0 + width0, 0.15, z), size, 0.1) * fade;
        mask += bokeh(ray, vec3f(-1.0 - width1, 0.15, z), size, 0.1) * fade;
        mask += bokeh(ray, vec3f(-1.0 + width1, 0.15, z), size, 0.1) * fade;

        var reflection = 0.0;
        reflection += bokeh(ray, vec3f(-1.0 - width1, -0.15, z), size * 3.0, 1.0) * fade;
        reflection += bokeh(ray, vec3f(-1.0 + width1, -0.15, z), size * 3.0, 1.0) * fade;

        mask += reflection * focus;
    }

    return vec3f(0.9, 0.9, 1.0) * mask;
}

fn tailLights(ray: Ray, timeIn: f32) -> vec3f {
    let time = timeIn * 0.25;
    var mask = 0.0;
    let width0 = 0.25;
    let width1 = width0 * 1.2;

    for (var i = 0.0; i < 1.0; i += 1.0 / 10.0) {
        let n = noise(i);
        if (n > 0.3) {
            continue;
        }

        let lane = step(0.25, n);
        let t = fract(i + time);
        let z = 100.0 - t * 100.0;
        let fade = t * t * t * t * t;
        let focus = ramp(0.9, 1.0, t);
        let size = mix(0.05, 0.03, focus);
        let shiftLane = ramp(1.0, 0.96, t);
        let x = 1.5 - lane * shiftLane;
        let blink = step(0.0, sin(time * 500.0)) * 7.0 * lane * step(0.9, t);

        mask += bokeh(ray, vec3f(x - width0, 0.15, z), size, 0.1) * fade;
        mask += bokeh(ray, vec3f(x + width0, 0.15, z), size, 0.1) * fade;
        mask += bokeh(ray, vec3f(x - width1, 0.15, z), size, 0.1) * fade;
        mask += bokeh(ray, vec3f(x + width1, 0.15, z), size, 0.1) * fade * (1.0 + blink);

        var reflection = 0.0;
        reflection += bokeh(ray, vec3f(x - width1, -0.15, z), size * 3.0, 1.0) * fade;
        reflection += bokeh(ray, vec3f(x + width1, -0.15, z), size * 3.0, 1.0) * fade * (1.0 + blink * 0.1);

        mask += reflection * focus;
    }

    return vec3f(1.0, 0.1, 0.03) * mask;
}

fn envLights(rayIn: Ray, time: f32) -> vec3f {
    var ray = rayIn;
    var color = vec3f(0.0);
    let side = step(ray.direction.x, 0.0);
    ray.direction.x = abs(ray.direction.x);

    for (var i = 0.0; i < 1.0; i += 0.2) {
        let t = fract(i + time + side * 0.2 * 0.5);
        let n = noiseVec4(i + side * 100.0);
        let occlusion = sin(t * 6.28 * 10.0 * n.x) * 0.5 + 0.5;
        let x = mix(2.5, 10.0, n.x);
        let y = mix(0.1, 2.0, n.y);
        let point = vec3f(x, y, 50.0 - t * 50.0);

        color += bokeh(ray, point, 0.05, 0.1) * occlusion * n.wzy * 0.5;
    }

    return color;
}

fn distortion(uvIn: vec2f, timeIn: f32) -> vec2f {
    var uv = uvIn;
    var time = timeIn * 40.0;

    let aspectRatio = vec2f(3.0, 1.0);
    var newUV = uv * aspectRatio;
    var id = floor(newUV);

    newUV.y += time * 0.24;
    let n = fract(sin(id.x * 716.34) * 768.34);
    newUV.y += n;
    uv.y += n;
    id = floor(newUV);
    newUV = fract(newUV) - 0.5;
    time += fract(sin(id.x * 76.34 + id.y * 1453.7) * 768.34) * 6.283;

    let y = -sin(time + sin(time + sin(time) * 0.5)) * 0.42;
    let pos = vec2f(0.0, y);
    let mainOffset = (newUV - pos) / aspectRatio;
    let mask1 = ramp(0.07, 0.0, length(mainOffset));

    let tailOffset = (fract(uv * aspectRatio.x * vec2f(1.0, 2.0)) - 0.5) / vec2f(1.0, 2.0);
    let mask2 = ramp(0.25 * (0.5 - newUV.y), 0.0, length(tailOffset)) * ramp(-0.1, 0.1, newUV.y - pos.y);

    return mask1 * mainOffset * 30.0 + mask2 * tailOffset * 10.0;
}

@fragment
fn fs(@builtin(position) fragCoord: vec4f) -> @location(0) vec4f {
    let size = u.resolution;
    let pxCoord = vec2f(fragCoord.x, size.y - fragCoord.y); // y up, as the SkSL original
    var uv = pxCoord / size;

    uv -= 0.5;
    uv.x *= size.x / size.y;

    let cameraPos = vec3f(0.0, 0.2, 0.0);
    let lookAt = vec3f(0.0, 0.2, 1.0);
    let slowed = u.time * 0.05;
    var rainDistortion = distortion(uv * 5.0, slowed) * 0.5;
    rainDistortion += distortion(uv * 7.0, slowed) * 0.5;

    uv.x += sin(uv.y * 70.0) * 0.0045;
    uv.y += sin(uv.x * 170.0) * 0.002;

    let ray = getRay(uv - rainDistortion * 0.5, cameraPos, lookAt, 2.0);
    var color = streetLights(ray, slowed);

    color += headLights(ray, slowed);
    color += tailLights(ray, slowed);
    color += envLights(ray, slowed);
    color += (ray.direction.y + 0.25) * vec3f(0.1, 0.1, 0.4);

    return vec4f(color, 1.0);
}
