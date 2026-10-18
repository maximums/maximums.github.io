// Exclusive prefix sum of u32s (Blelloch), in blocks of 512 values: each workgroup scans its block in shared memory
// (an up-sweep that builds partial sums in a tree, then a down-sweep that hands them back down) and writes the
// block's total. The totals are scanned the same way, and addBlockSums adds each block's offset back in.

struct Params {
    count: u32,
    _pad0: u32,
    _pad1: u32,
    _pad2: u32,
}

@group(0) @binding(0) var<uniform> params: Params;
@group(0) @binding(1) var<storage, read> input: array<u32>;
@group(0) @binding(2) var<storage, read_write> output: array<u32>;
@group(0) @binding(3) var<storage, read_write> blockSums: array<u32>;

const BLOCK = 512u;
const THREADS = 256u;

var<workgroup> tree: array<u32, 512>;

fn load(i: u32) -> u32 {
    if (i < params.count) {
        return input[i];
    }
    return 0u;
}

@compute @workgroup_size(256)
fn scanBlocks(@builtin(local_invocation_id) local: vec3u, @builtin(workgroup_id) group: vec3u) {
    let base = group.x * BLOCK;
    let a = local.x;
    let b = local.x + THREADS;
    tree[a] = load(base + a);
    tree[b] = load(base + b);

    // Up-sweep: after it, the last element holds the block's total.
    var offset = 1u;
    for (var d = BLOCK >> 1u; d > 0u; d >>= 1u) {
        workgroupBarrier();
        if (local.x < d) {
            let left = offset * (2u * local.x + 1u) - 1u;
            let right = offset * (2u * local.x + 2u) - 1u;
            tree[right] += tree[left];
        }
        offset <<= 1u;
    }

    workgroupBarrier();
    if (local.x == 0u) {
        blockSums[group.x] = tree[BLOCK - 1u];
        tree[BLOCK - 1u] = 0u;
    }

    // Down-sweep: each node passes its value to its left child and the sum to its right child.
    for (var d = 1u; d < BLOCK; d <<= 1u) {
        offset >>= 1u;
        workgroupBarrier();
        if (local.x < d) {
            let left = offset * (2u * local.x + 1u) - 1u;
            let right = offset * (2u * local.x + 2u) - 1u;
            let value = tree[left];
            tree[left] = tree[right];
            tree[right] += value;
        }
    }

    workgroupBarrier();
    if (base + a < params.count) {
        output[base + a] = tree[a];
    }
    if (base + b < params.count) {
        output[base + b] = tree[b];
    }
}

// Here `input` holds the scanned block totals and `output` the data being scanned.
@compute @workgroup_size(256)
fn addBlockSums(@builtin(global_invocation_id) id: vec3u) {
    let i = id.x;
    if (i < params.count) {
        output[i] += input[i / BLOCK];
    }
}
