// The Life grid as embers (DESIGN.md §8): fresh cells glow lamp amber and cool to rust as they age, the recently dead
// leave a faint ghost, embers flicker gently, and a faint Rain-coloured grid shows once cells are big enough. One
// full-screen pass reads the state buffer directly: no draw call per cell.

struct View {
    resolution: vec2f,  // target size in pixels
    origin: vec2f,      // the grid position (in cells) at the target's top-left corner
    cellSize: f32,      // pixels per cell
    time: f32,          // seconds, folded
    width: f32,         // grid size in cells
    height: f32,
}

@group(0) @binding(0) var<uniform> view: View;
@group(0) @binding(1) var<storage, read> cells: array<u32>;

const AGE = 0xFFFFu;
const GHOST_GENERATIONS = 12.0;

// DESIGN.md colour tokens.
const NIGHT = vec3f(13.0, 16.0, 19.0) / 255.0;
const LAMP = vec3f(216.0, 161.0, 90.0) / 255.0;
const EMBER = vec3f(180.0, 83.0, 46.0) / 255.0;
const RUST = vec3f(64.0, 28.0, 20.0) / 255.0;
const RAIN = vec3f(126.0, 151.0, 163.0) / 255.0;

@vertex
fn vs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
    let corners = array(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
    return vec4f(corners[i], 0.0, 1.0);
}

fn cellAt(c: vec2i) -> u32 {
    if (c.x < 0 || c.y < 0 || c.x >= i32(view.width) || c.y >= i32(view.height)) {
        return 0u;
    }
    return cells[u32(c.y) * u32(view.width) + u32(c.x)];
}

fn hash(c: vec2i) -> f32 {
    return fract(sin(dot(vec2f(c), vec2f(127.1, 311.7))) * 43758.5453);
}

// How fast an ember has cooled: 0 when just born, 1 after about a minute of generations.
fn cooling(age: u32) -> f32 {
    return smoothstep(1.0, 60.0, f32(age));
}

// Lamp amber when fresh, through ember, to rust.
fn emberColor(age: u32) -> vec3f {
    let t = cooling(age);
    return mix(mix(LAMP, EMBER, smoothstep(0.0, 0.45, t)), RUST, smoothstep(0.45, 1.0, t));
}

// How much light a cell gives off: hot when fresh, a little when old, a breath when it's a ghost.
fn heat(cell: u32, c: vec2i) -> f32 {
    let age = cell & AGE;
    if (age != 0u) {
        let flicker = 0.92 + 0.08 * sin(view.time * 2.6 + hash(c) * 6.2831853); // DESIGN: embers flicker (2.4 s)
        return mix(1.0, 0.3, cooling(age)) * flicker;
    }
    return f32(cell >> 16u) / GHOST_GENERATIONS * 0.18;
}

@fragment
fn fs(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let grid = view.origin + position.xy / view.cellSize;
    let c = vec2i(floor(grid));
    if (c.x < 0 || c.y < 0 || c.x >= i32(view.width) || c.y >= i32(view.height)) {
        return vec4f(NIGHT * 0.6, 1.0); // a darker ground outside, so the edge of the world reads
    }

    // Light spilling from the 3×3 neighbourhood: embers glow onto their surroundings.
    var color = NIGHT;
    for (var dy = -1; dy <= 1; dy++) {
        for (var dx = -1; dx <= 1; dx++) {
            let n = c + vec2i(dx, dy);
            let cell = cellAt(n);
            if (cell == 0u) {
                continue;
            }
            let distance = length(grid - (vec2f(n) + 0.5));
            let tint = select(EMBER, emberColor(cell & AGE), (cell & AGE) != 0u);
            color += tint * heat(cell, n) * exp(-distance * distance * 1.6) * 0.32;
        }
    }

    // The cell itself: a soft rounded ember, filling the whole cell once cells are only a few pixels wide.
    let cell = cellAt(c);
    let age = cell & AGE;
    if (age != 0u) {
        let roundness = smoothstep(4.0, 12.0, view.cellSize);
        let distance = length(fract(grid) - 0.5);
        let body = mix(1.0, 1.0 - smoothstep(0.32, 0.5, distance), roundness);
        color = mix(color, emberColor(age) * (0.55 + 0.75 * heat(cell, c)), body);
    }

    // Grid lines, one pixel wide, once a cell is at least 6 px.
    let inCell = fract(grid);
    let edge = min(min(inCell.x, 1.0 - inCell.x), min(inCell.y, 1.0 - inCell.y)) * view.cellSize;
    let line = (1.0 - smoothstep(0.0, 1.0, edge)) * smoothstep(5.0, 10.0, view.cellSize) * 0.12;
    color = mix(color, RAIN, line);

    return vec4f(color, 1.0);
}
