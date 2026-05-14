package com.cdodi.webgpu

import com.cdodi.webgpu.bindings.*
import com.cdodi.webgpu.core.gpu
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.JsAny
import kotlin.js.toJsNumber

@JsFun("(canvas) => canvas.getContext('webgpu')")
private external fun getWebGPUContext(canvas: JsAny): JsAny?

/**
 * Initialises WebGPU, builds a render pipeline, and draws a coloured
 * "Hello World" triangle to the `#webgpu-canvas` element.
 */
suspend fun helloWebGPU() {
    // --- canvas setup ---
    val canvas = document.getElementById("webgpu-canvas") as? HTMLCanvasElement
        ?: error("Canvas element #webgpu-canvas not found")

    val ratio = window.devicePixelRatio
    canvas.width = (canvas.clientWidth * ratio).toInt()
    canvas.height = (canvas.clientHeight * ratio).toInt()

    val context = getWebGPUContext(canvas)?.unsafeCast<GPUCanvasContext>()
        ?: error("Failed to obtain WebGPU canvas context")

    // --- device setup ---
    val gpu = gpu() ?: error("WebGPU is not supported in this browser")
    val adapter = gpu.requestAdapterSuspend()
    val device = adapter.requestDeviceSuspend()
    val format = gpu.getPreferredCanvasFormat()

    context.configure(GPUCanvasConfiguration(device = device, format = format))

    // --- shader module ---
    val shaderModule = device.createShaderModule(
        GPUShaderModuleDescriptor(code = HELLO_TRIANGLE_WGSL, label = "hello-triangle")
    )

    // --- render pipeline ---
    val pipeline = device.createRenderPipeline(
        GPURenderPipelineDescriptor(
            layout = GPUAutoLayoutModeEntries.auto,
            vertex = GPUVertexState(
                module = shaderModule,
                entryPoint = "vs_main",
            ),
            fragment = GPUFragmentState(
                module = shaderModule,
                entryPoint = "fs_main",
                targets = listOf(GPUColorTargetState(format = format)),
            ),
            primitive = GPUPrimitiveState(
                topology = GPUPrimitiveTopologyEntries.`triangle-list`,
            ),
            label = "hello-triangle-pipeline",
        )
    )

    // --- draw one frame ---
    render(device, context, pipeline)
}

private fun render(device: GPUDevice, context: GPUCanvasContext, pipeline: GPURenderPipeline) {
    val encoder = device.createCommandEncoder()

    val colorAttachment = GPURenderPassColorAttachment(
        view = context.getCurrentTexture().createView(),
        clearValue = GPUColorDict(r = 0.15, g = 0.15, b = 0.15, a = 1.0),
        loadOp = GPULoadOpEntries.clear,
        storeOp = GPUStoreOpEntries.store,
    )

    val pass = encoder.beginRenderPass(
        GPURenderPassDescriptor(colorAttachments = listOf(colorAttachment))
    )
    pass.setPipeline(pipeline)
    pass.draw(3.toJsNumber())
    pass.end()

    device.queue.submit(listOf(encoder.finish()).toJsArray())
}

/**
 * Minimal WGSL shader: a hard-coded RGB triangle covering the viewport centre.
 * Vertex positions and colours are baked into the shader — no vertex buffers needed.
 */
private const val HELLO_TRIANGLE_WGSL = """
struct VSOut {
    @builtin(position) pos: vec4<f32>,
    @location(0)       color: vec3<f32>,
};

@vertex
fn vs_main(@builtin(vertex_index) vid: u32) -> VSOut {
    // three vertices of a centred triangle
    var positions = array<vec2<f32>, 3>(
        vec2<f32>( 0.0,  0.5),   // top
        vec2<f32>(-0.5, -0.5),   // bottom-left
        vec2<f32>( 0.5, -0.5),   // bottom-right
    );
    var colors = array<vec3<f32>, 3>(
        vec3<f32>(1.0, 0.2, 0.2),   // red
        vec3<f32>(0.2, 1.0, 0.2),   // green
        vec3<f32>(0.2, 0.2, 1.0),   // blue
    );

    var out: VSOut;
    out.pos   = vec4<f32>(positions[vid], 0.0, 1.0);
    out.color = colors[vid];
    return out;
}

@fragment
fn fs_main(in: VSOut) -> @location(0) vec4<f32> {
    return vec4<f32>(in.color, 1.0);
}
"""
