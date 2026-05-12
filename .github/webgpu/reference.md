# WebGPU Quick Reference — Kotlin/Wasm

API patterns using this project's generated bindings (`com.cdodi.webgpu.bindings.*`) and runtime helpers (`webGpuRuntime`).

## Device + context setup

```kotlin
val canvas = document.getElementById("webgpu-canvas") as? HTMLCanvasElement ?: return
val ratio = window.devicePixelRatio
canvas.width = (canvas.clientWidth * ratio).toInt()
canvas.height = (canvas.clientHeight * ratio).toInt()

val context = canvas.getContext("webgpu") as? GPUCanvasContext ?: return
val gpu = gpu() ?: return
val adapter = gpu.requestAdapterSuspend()
val device = adapter.requestDeviceSuspend()
val format = gpu.getPreferredCanvasFormat()

context.configure(GPUCanvasConfiguration(device = device, format = format))
```

## Buffer creation

```kotlin
val buffer = device.createBuffer(GPUBufferDescriptor(
    size = byteLength,
    usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_DST,
    mappedAtCreation = false
))
```

## Compute pipeline

```kotlin
val module = device.createShaderModule(GPUShaderModuleDescriptor(code = wgslSource))
val pipeline = device.createComputePipeline(GPUComputePipelineDescriptor(
    layout = GPUAutoLayoutModeEntries.auto,
    compute = GPUProgrammableStage(module = module, entryPoint = "main")
))
```

## Compute dispatch

```kotlin
val encoder = device.createCommandEncoder()
val pass = encoder.beginComputePass()
pass.setPipeline(pipeline)
pass.setBindGroup(0.toJsNumber(), bindGroup)
pass.dispatchWorkgroups(
    workgroupsX.toJsNumber(),
    workgroupsY.toJsNumber(),
    workgroupsZ.toJsNumber()
)
pass.end()
device.queue.submit(arrayOf(encoder.finish()).toJsArray())
```

## Render pass

```kotlin
val colorAttachment = GPURenderPassColorAttachment(
    view = context.getCurrentTexture().createView(),
    clearValue = null,
    loadOp = GPULoadOpEntries.clear,
    storeOp = GPUStoreOpEntries.store
)
val pass = encoder.beginRenderPass(GPURenderPassDescriptor(listOf(colorAttachment)))
```

## MSAA rendering

```kotlin
val msaaTexture = device.createTexture(GPUTextureDescriptor(
    size = arrayOf(canvas.width.toJsNumber(), canvas.height.toJsNumber()).toJsArray(),
    sampleCount = 4,
    format = canvasFormat,
    usage = GPUTextureUsage.RENDER_ATTACHMENT
))
val colorAttachment = GPURenderPassColorAttachment(
    view = msaaTexture.createView(),
    resolveTarget = context.getCurrentTexture().createView(),
    loadOp = GPULoadOpEntries.clear,
    storeOp = GPUStoreOpEntries.store
)
// Remember to call msaaTexture.destroy() when done
```

## Readback (avoid in hot paths)

```kotlin
val readBuffer = device.createBuffer(GPUBufferDescriptor(
    size = byteLength,
    usage = GPUBufferUsage.COPY_DST or GPUBufferUsage.MAP_READ
))
encoder.copyBufferToBuffer(srcBuffer, 0, readBuffer, 0, byteLength)
device.queue.submit(arrayOf(encoder.finish()).toJsArray())
readBuffer.mapAsyncSuspend(GPUMapMode.READ)
val data = readBuffer.getMappedRange()
```

## Render loop

```kotlin
suspend fun renderLoop(device: GPUDevice, context: GPUCanvasContext) {
    while (true) {
        withFrameNanos { frameTimeNanos ->
            val encoder = device.createCommandEncoder()
            // ... encode compute + render passes ...
            device.queue.submit(arrayOf(encoder.finish()).toJsArray())
        }
    }
}
```

## Uniform packing (WGSL side)

Pack small scalars into `vec4` slots for alignment:

```wgsl
struct Params {
  v0: vec4<f32>, // resolution.xy, time, deltaTime
  v1: vec4<f32>, // mouseX, mouseY, frame, unused
};

@group(0) @binding(0) var<uniform> params: Params;
```
