---
name: webgpu
description: WebGPU/WGSL guidance for Kotlin/Wasm — initialization, render/compute pipelines, shader authoring, debugging, and performance using this project's generated bindings.
---

# WebGPU Skill

Use this skill when designing, implementing, or debugging WebGPU features in this project. All GPU code uses the **generated Kotlin/Wasm bindings** (`com.cdodi.webgpu.bindings.*`) and runtime helpers in `webGpuRuntime`.

## What this skill covers

- WebGPU initialization, device setup, and canvas configuration via Kotlin/Wasm.
- Compute pipelines, workgroup sizing, and storage buffer layout.
- Render pipelines, render passes, MSAA, and post-processing patterns.
- GPU/CPU synchronization and safe readback strategies.
- Performance and debugging practices.
- Architecture patterns: modular passes, phase-based simulation, ping-pong buffers.

## Core principles

- **Fail fast** when WebGPU is unavailable — this project requires it for the canvas path.
- Avoid full GPU readbacks in hot paths; use **localized queries** or small readback buffers.
- Structure simulation with **phases** (state → apply → integrate → constrain → correct) to keep WGSL cohesive.
- Use **spatial grids** or tiled buffers for neighbor queries and high particle counts.
- Build **modular passes** so render and compute stages stay composable and testable.

## Workflow

When asked to build a WebGPU feature:

1. Confirm the feature's resource layout (buffers, textures, bind groups).
2. Sketch the pipeline graph (compute vs render passes) and dependencies.
3. Provide minimal working code using the generated bindings, then scale up.
4. Document WGSL struct alignment (16-byte) in comments.

## Deliverable checklist

- Clean WebGPU init and error handling using `requestAdapterSuspend()` / `requestDeviceSuspend()`.
- Buffer layout with alignment notes (16-byte struct alignment for WGSL).
- Pass graph with clear read/write ownership (ping-pong textures/buffers if needed).
- Resource cleanup: `.destroy()` textures/buffers when no longer needed.
- Call out readback and when it is safe.

## References

- [`reference.md`](../webgpu/reference.md) — Kotlin/Wasm API quick reference (device setup, buffers, pipelines, dispatch)
- [`patterns.md`](../webgpu/patterns.md) — Simulation phases, ping-pong, workgroups, debugging, pitfalls
- [`templates.md`](../webgpu/templates.md) — Ready-to-use WGSL shader templates for this project
