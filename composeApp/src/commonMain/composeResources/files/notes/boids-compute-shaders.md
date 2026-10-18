---
title: "1M boids in the browser: spatial hashing with compute shaders"
date: 2026-10-18
summary: Every frame, the GPU counting-sorts the flock into a grid with atomics, a parallel scan and a scatter, so each boid only checks the cells around it. Tests check it against a CPU reference, boid by boid.
---

The [Boids page](/#/boids) is a murmuration: dark birds over a lit fog bank, up to a little over a million of them, all simulated on the GPU with WebGPU compute shaders. This is how it works.

## The rules

Craig Reynolds' boids (1987) need three rules, each looking at the birds within a perception radius:

- **separation**: steer away from neighbours that are too close, the closest most;
- **alignment**: steer towards their average heading;
- **cohesion**: steer towards their average position.

Mine adds two more: the pointer attracts or scatters the flock, and every bird keeps its speed between a minimum and a maximum. Each frame, every boid gathers sums over its neighbours, turns them into an acceleration, and moves.

## The problem is finding the neighbours

Done naively, every boid checks every other boid. That's N² distance checks a frame: a billion for 32,768 boids, and 10^12 for a million. The naive version still exists, as `stepNaive` in the shader. It's useful for small flocks and as the reference the fast version is tested against.

The fast version uses a grid. If each cell is at least the perception radius wide, everything a boid can see is in its own cell or one of the eight around it. (There are always at least three cells in each direction. The world wraps around, so on a grid two cells wide the cells around a boid would include the same cells twice, and it would count its neighbours twice.) The catch is that the boids move every frame, so the grid has to be rebuilt every frame, on the GPU, from scratch.

## Building the grid: a counting sort in four passes

Each pass is a compute shader with one thread per boid:

1. **Count.** Each boid works out its cell and does `atomicAdd(&cellCounts[cell], 1)`. The value `atomicAdd` returns is the count *before* the add, so it doubles as the boid's rank within its cell: the first boid to arrive gets 0, the next 1, and so on.
2. **Scan.** An exclusive prefix sum over the counts gives each cell's start: where its boids will begin in the sorted array. This is a Blelloch scan. Each workgroup scans a block of 512 counts in shared memory, the block totals are scanned the same way, and the totals are added back.
3. **Scatter.** Each boid copies itself to `sorted[cellStarts[cell] + rank]`. Now every cell's boids sit next to each other, with no comparisons made: a counting sort.
4. **Step.** Each boid reads its own cell and the eight around it (just ranges of the sorted array) and writes its new position and velocity.

```wgsl
let start = cellStarts[cell];
let end = start + atomicLoad(&cellCounts[cell]);
for (var j = start; j < end && sums.count < enough; j++) {
    if (j != i) {
        visit(&sums, me, sorted[j]);
    }
}
```

The step writes its output in sorted order too. Boids that are close in the world end up close in memory, so on the next frame the threads of a workgroup read mostly the same cells, which helps the cache. Two boid buffers take turns being input and output.

## Crowds

A grid alone isn't enough at a million boids. On a screen-sized world, that's hundreds of boids per cell, and thousands of candidates for each boid to check.

Real starlings don't track the whole flock either. Field studies (Ballerini et al., 2008) found each bird responds to a fixed number of neighbours, around six or seven, however dense the flock. So each boid stops looking once it has seen enough neighbours (48 here). That makes a dense crowd cost no more per boid than a sparse one.

Stopping early has a catch: in a crowd, the order you look in decides whom you see. If every boid scanned the top-left cell first, cohesion would pull the whole flock towards the top left. So each boid looks in its own cell first, then goes round the eight around it, starting from a different cell depending on its index. The bias averages out over the flock.

## Memory

A boid is 16 bytes: a position and a velocity, two `vec2f`. Each boid also needs a slot in the sorted copy and two `u32`s (its cell and its rank), so 56 bytes in all, or 56 MiB at a million. The buffers start with room for 32,768 boids and double when the flock slider asks for more. A phone showing a small flock doesn't pay for a big one.

## Drawing it

The render pass reads the boids straight from the same storage buffer. Each bird is one instanced triangle, placed and turned by the vertex shader, and there is no vertex buffer at all. The boids never travel back to the CPU. The pass also draws the fog bank behind the birds and resolves 4× multisampling.

## Measuring it

The Boids page shows the GPU time of each pass (count, scan, scatter, neighbours, render), measured with WebGPU's `timestamp-query`. The timestamps are copied into a ring of three `MAP_READ` buffers and read a frame or two later, so measuring never makes the CPU wait for the GPU. Browsers that don't support timestamp queries just don't show the readout.

The neighbour pass dominates, as you'd expect. The scan is close to free: there are only a few thousand cells. Open the page and drag the flock size up to see how your GPU copes.

## Testing GPU code

The rules also exist as plain Kotlin: a CPU reference that does the same arithmetic in the same order as the shader. The tests run the shaders on a real WebGPU device in headless Chrome, using SwiftShader as the GPU, and compare:

- one naive step matches the reference **boid by boid**;
- one grid step finds the **same neighbours** as the reference. The output order differs, so each result is matched to the nearest expected boid;
- speeds stay between the limits after many steps;
- a flock that outgrows its buffers gets bigger ones, and still matches.
