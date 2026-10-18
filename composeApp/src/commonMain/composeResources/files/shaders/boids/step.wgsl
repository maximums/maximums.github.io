// Boids: Reynolds' separation, alignment and cohesion. Two ways to find a boid's neighbours:
// - stepNaive: every boid looks at every other one, O(N²). Small flocks, and the check for the grid version.
// - countCells, (an exclusive scan of the counts), scatter, stepGrid: the boids are counting-sorted into a grid whose
//   cells are at least the perception radius wide, so each boid only looks at the 3×3 cells around its own.
// The arithmetic follows features/boids/reference (Rule.kt, BoidsReference.kt), in the same order.

struct Boid {
    pos: vec2f,
    vel: vec2f,
}

struct Params {
    world: vec2f,
    count: f32,
    dt: f32,
    perception: f32,
    separationDistance: f32,
    separationWeight: f32,
    alignmentWeight: f32,
    cohesionWeight: f32,
    maxSpeed: f32,
    minSpeed: f32,
    maxForce: f32,
    pointer: vec2f,
    pointerRadius: f32,
    pointerMode: f32,       // 1 attracts, -1 repels, 0 nothing
    pointerWeight: f32,
    maxNeighbours: f32,
    grid: vec2f,            // cells across and down
    cellSize: vec2f,
    _pad: vec2f,
}

@group(0) @binding(0) var<uniform> params: Params;
@group(0) @binding(1) var<storage, read> boidsIn: array<Boid>;
@group(0) @binding(2) var<storage, read_write> boidsOut: array<Boid>;
@group(0) @binding(3) var<storage, read_write> sorted: array<Boid>;
@group(0) @binding(4) var<storage, read_write> boidCell: array<u32>;
@group(0) @binding(5) var<storage, read_write> boidRank: array<u32>;
@group(0) @binding(6) var<storage, read_write> cellCounts: array<atomic<u32>>;
@group(0) @binding(7) var<storage, read> cellStarts: array<u32>;

// What a boid gathers from the neighbours it sees.
struct Sums {
    away: vec2f,     // separation: away from those too close, closest most
    heading: vec2f,  // alignment: the sum of their velocities
    centre: vec2f,   // cohesion: the sum of the offsets to them
    count: u32,
}

// The shortest offset in a world that wraps around.
fn wrappedDelta(d: vec2f) -> vec2f {
    return d - params.world * round(d / params.world);
}

fn limited(v: vec2f, max: f32) -> vec2f {
    let length = length(v);
    if (length > max) {
        return v * (max / length);
    }
    return v;
}

// Rule.kt steerTowards: turn towards `desired` at full speed, with at most maxForce; nothing if there's nowhere to go.
fn steerTowards(desired: vec2f, vel: vec2f) -> vec2f {
    let length = length(desired);
    if (length == 0.0) {
        return vec2f(0.0);
    }
    return limited(desired / length * params.maxSpeed - vel, params.maxForce);
}

fn visit(sums: ptr<function, Sums>, me: Boid, other: Boid) {
    let offset = wrappedDelta(other.pos - me.pos);
    let distance = length(offset);
    if (distance > 0.0 && distance < params.perception && f32((*sums).count) < params.maxNeighbours) {
        (*sums).heading += other.vel;
        (*sums).centre += offset;
        (*sums).count += 1u;
        if (distance < params.separationDistance) {
            (*sums).away -= offset / (distance * distance);
        }
    }
}

fn pointerForce(me: Boid) -> vec2f {
    if (params.pointerMode == 0.0) {
        return vec2f(0.0);
    }
    let offset = wrappedDelta(params.pointer - me.pos);
    let distance = length(offset);
    if (distance >= params.pointerRadius) {
        return vec2f(0.0);
    }
    return steerTowards(offset * params.pointerMode, me.vel) * (params.pointerWeight * (1.0 - distance / params.pointerRadius));
}

fn integrate(me: Boid, sums: Sums) -> Boid {
    var acceleration = steerTowards(sums.away, me.vel) * params.separationWeight;
    if (sums.count > 0u) {
        let n = f32(sums.count);
        acceleration += steerTowards(sums.heading / n, me.vel) * params.alignmentWeight;
        acceleration += steerTowards(sums.centre / n, me.vel) * params.cohesionWeight;
    }
    acceleration += pointerForce(me);

    var vel = limited(me.vel + acceleration * params.dt, params.maxSpeed);
    let speed = length(vel);
    if (speed < params.minSpeed) {
        vel = select(vec2f(0.0), vel / speed, speed > 0.0) * params.minSpeed;
    }
    let pos = me.pos + vel * params.dt;
    return Boid(pos - params.world * floor(pos / params.world), vel);
}

fn noSums() -> Sums {
    return Sums(vec2f(0.0), vec2f(0.0), vec2f(0.0), 0u);
}

@compute @workgroup_size(256)
fn stepNaive(@builtin(global_invocation_id) id: vec3u) {
    let count = u32(params.count);
    let i = id.x;
    if (i >= count) {
        return;
    }
    let me = boidsIn[i];
    var sums = noSums();
    for (var j = 0u; j < count; j++) {
        if (j != i) {
            visit(&sums, me, boidsIn[j]);
        }
    }
    boidsOut[i] = integrate(me, sums);
}

fn cellOf(pos: vec2f) -> vec2u {
    return min(vec2u(max(pos / params.cellSize, vec2f(0.0))), vec2u(params.grid) - 1u);
}

// 1. Each boid takes a place in its cell; atomicAdd's old value is its rank within the cell.
@compute @workgroup_size(256)
fn countCells(@builtin(global_invocation_id) id: vec3u) {
    let i = id.x;
    if (i >= u32(params.count)) {
        return;
    }
    let cell = cellOf(boidsIn[i].pos);
    let index = cell.y * u32(params.grid.x) + cell.x;
    boidCell[i] = index;
    boidRank[i] = atomicAdd(&cellCounts[index], 1u);
}

// 3. After the scan, each cell's boids sit together, from the cell's start: a counting sort.
@compute @workgroup_size(256)
fn scatter(@builtin(global_invocation_id) id: vec3u) {
    let i = id.x;
    if (i >= u32(params.count)) {
        return;
    }
    sorted[cellStarts[boidCell[i]] + boidRank[i]] = boidsIn[i];
}

// The eight cells around a boid's own, in a ring.
var<private> RING: array<vec2i, 8> = array(
    vec2i(-1, -1), vec2i(0, -1), vec2i(1, -1), vec2i(1, 0), vec2i(1, 1), vec2i(0, 1), vec2i(-1, 1), vec2i(-1, 0),
);

// 4. Each boid only looks at the 3×3 cells around its own. The output stays in sorted order: nearby boids sit nearby
// in memory for the next frame too.
//
// A boid stops looking once it has seen maxNeighbours (like a starling, which keeps track of a handful of birds, not of
// the whole flock), so a dense crowd costs no more than a sparse one. In a crowd, the order it looks in decides whom it
// sees: its own cell first, then the ring, starting from a different cell for each boid so no direction is favoured.
@compute @workgroup_size(256)
fn stepGrid(@builtin(global_invocation_id) id: vec3u) {
    let i = id.x;
    if (i >= u32(params.count)) {
        return;
    }
    let me = sorted[i];
    let grid = vec2i(params.grid);
    let home = vec2i(cellOf(me.pos));
    let enough = u32(params.maxNeighbours);
    var sums = noSums();
    for (var k = 0u; k < 9u && sums.count < enough; k++) {
        var c = home;
        if (k > 0u) {
            c = (home + RING[(k - 1u + i) % 8u] + grid) % grid;
        }
        let cell = u32(c.y) * u32(grid.x) + u32(c.x);
        let start = cellStarts[cell];
        let end = start + atomicLoad(&cellCounts[cell]);
        for (var j = start; j < end && sums.count < enough; j++) {
            if (j != i) {
                visit(&sums, me, sorted[j]);
            }
        }
    }
    boidsOut[i] = integrate(me, sums);
}
