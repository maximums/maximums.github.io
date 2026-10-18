// One generation of a Life-like automaton: each cell counts its eight neighbours and applies the rule's birth and
// survive masks. One thread per cell, 8×8 cells per workgroup. Live cells are counted per workgroup in shared
// memory, then added to the population once per workgroup instead of once per cell.

struct Params {
    width: u32,
    height: u32,
    birth: u32,     // bit n set: a dead cell with n live neighbours comes alive (B3 is 1 << 3)
    survive: u32,   // bit n set: a live cell with n live neighbours stays alive
    wrap: u32,      // 1: the grid is a torus; 0: everything outside it is dead
    _pad0: u32,
    _pad1: u32,
    _pad2: u32,
}

// A cell is one u32. While alive, its low 16 bits hold its age in generations (1..65535). Once it dies, its high
// 16 bits count down a ghost, so the render pass can draw a fading trace where it was.
const AGE = 0xFFFFu;
const GHOST_GENERATIONS = 12u;

@group(0) @binding(0) var<uniform> params: Params;
@group(0) @binding(1) var<storage, read> current: array<u32>;
@group(0) @binding(2) var<storage, read_write> next: array<u32>;
@group(0) @binding(3) var<storage, read_write> population: atomic<u32>;

var<workgroup> liveInGroup: atomic<u32>;

fn liveAt(x: i32, y: i32) -> u32 {
    let width = i32(params.width);
    let height = i32(params.height);
    var cx = x;
    var cy = y;
    if (params.wrap != 0u) {
        cx = (x + width) % width;
        cy = (y + height) % height;
    } else if (x < 0 || y < 0 || x >= width || y >= height) {
        return 0u;
    }
    return select(0u, 1u, (current[u32(cy) * params.width + u32(cx)] & AGE) != 0u);
}

fn nextCell(cell: u32, neighbours: u32) -> u32 {
    let age = cell & AGE;
    if (age != 0u) {
        if (((params.survive >> neighbours) & 1u) != 0u) {
            return min(age + 1u, AGE);
        }
        return GHOST_GENERATIONS << 16u;
    }
    if (((params.birth >> neighbours) & 1u) != 0u) {
        return 1u;
    }
    let ghost = cell >> 16u;
    return select(0u, (ghost - 1u) << 16u, ghost > 0u);
}

// Adds this workgroup's count once. Barriers must be reached by every thread, so no thread returns early.
fn addToPopulation(local: u32) {
    workgroupBarrier();
    if (local == 0u) {
        let count = atomicLoad(&liveInGroup);
        if (count > 0u) {
            atomicAdd(&population, count);
        }
    }
}

@compute @workgroup_size(8, 8)
fn step(@builtin(global_invocation_id) id: vec3u, @builtin(local_invocation_index) local: u32) {
    if (local == 0u) {
        atomicStore(&liveInGroup, 0u);
    }
    workgroupBarrier();

    if (id.x < params.width && id.y < params.height) {
        let x = i32(id.x);
        let y = i32(id.y);
        let neighbours = liveAt(x - 1, y - 1) + liveAt(x, y - 1) + liveAt(x + 1, y - 1)
            + liveAt(x - 1, y) + liveAt(x + 1, y)
            + liveAt(x - 1, y + 1) + liveAt(x, y + 1) + liveAt(x + 1, y + 1);
        let index = id.y * params.width + id.x;
        let result = nextCell(current[index], neighbours);
        next[index] = result;
        if ((result & AGE) != 0u) {
            atomicAdd(&liveInGroup, 1u);
        }
    }

    addToPopulation(local);
}

// Counts the live cells of `current` without stepping, after cells were drawn or cleared.
@compute @workgroup_size(8, 8)
fn count(@builtin(global_invocation_id) id: vec3u, @builtin(local_invocation_index) local: u32) {
    if (local == 0u) {
        atomicStore(&liveInGroup, 0u);
    }
    workgroupBarrier();

    if (id.x < params.width && id.y < params.height && (current[id.y * params.width + id.x] & AGE) != 0u) {
        atomicAdd(&liveInGroup, 1u);
    }

    addToPopulation(local);
}
