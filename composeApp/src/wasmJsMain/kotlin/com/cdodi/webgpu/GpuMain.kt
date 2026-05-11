package com.cdodi.webgpu

import com.cdodi.webgpu.bindings.GPUAutoLayoutModeEntries
import com.cdodi.webgpu.bindings.GPUShaderModuleDescriptor
import com.cdodi.webgpu.bindings.GPUCanvasContext
import com.cdodi.webgpu.bindings.GPUCanvasConfiguration
import com.cdodi.webgpu.bindings.GPUColorTargetState
import com.cdodi.webgpu.bindings.GPUFragmentState
import com.cdodi.webgpu.bindings.GPULoadOpEntries
import com.cdodi.webgpu.bindings.GPUMultisampleState
import com.cdodi.webgpu.bindings.GPURenderPassColorAttachment
import com.cdodi.webgpu.bindings.GPURenderPassDescriptor
import com.cdodi.webgpu.bindings.GPURenderPipelineDescriptor
import com.cdodi.webgpu.bindings.GPUStoreOpEntries
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.GPUVertexState
import com.cdodi.webgpu.bindings.requestAdapterSuspend
import com.cdodi.webgpu.bindings.requestDeviceSuspend
import com.cdodi.webgpu.core.gpu
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.toJsArray

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
suspend fun prepareWebGPUCanvas() {
    val canvas = document.getElementById("webgpu-canvas") as? HTMLCanvasElement ?: return
    val ratio = window.devicePixelRatio
    canvas.width = (canvas.clientWidth * ratio).toInt()
    canvas.height = (canvas.clientHeight * ratio).toInt()
    val context = canvas.getContext("webgpu") as? GPUCanvasContext ?: return
    val gpu = gpu() ?: return
    val adapter = gpu.requestAdapterSuspend()
    val device = adapter.requestDeviceSuspend()
    val canvasFormat = gpu.getPreferredCanvasFormat()
    val config = GPUCanvasConfiguration(
        device = device,
        format = canvasFormat
    )
    context.configure(config)

    val shaderModuleDescriptor = GPUShaderModuleDescriptor(code = myShader)
    val shaderModule = device.createShaderModule(shaderModuleDescriptor)
    val vertex = GPUVertexState(module = shaderModule)
    val colorTarget = GPUColorTargetState(
        format = canvasFormat,
        blend = null,
        writeMask = null
    )
    val fragment = GPUFragmentState(
        targets = listOf(colorTarget),
        module = shaderModule
    )
    val pipelineDescriptor = GPURenderPipelineDescriptor(
        vertex = vertex,
        layout = GPUAutoLayoutModeEntries.auto,
        fragment = fragment,
        multisample = GPUMultisampleState(count = 4)
    )
    val pipeline = device.createRenderPipeline(pipelineDescriptor)
    val msaaTextureDescriptor = GPUTextureDescriptor(
        size = arrayOf(
            canvas.width.toJsNumber(),
            canvas.height.toJsNumber()
        ).toJsArray(),
        sampleCount = 4,
        format = canvasFormat,
        usage = GPUTextureUsage.RENDER_ATTACHMENT
    )
    val msaaTexture = device.createTexture(msaaTextureDescriptor)
    val msaaTextureView = msaaTexture.createView()
    val renderPassColorAttachment = GPURenderPassColorAttachment(
        view = msaaTextureView,
        resolveTarget = context.getCurrentTexture().createView(),
        loadOp = GPULoadOpEntries.clear,
        storeOp = GPUStoreOpEntries.store
    )
    val descriptor = GPURenderPassDescriptor(listOf(renderPassColorAttachment))
    val encoder = device.createCommandEncoder()
    val pass = encoder.beginRenderPass(descriptor)

    pass.setPipeline(pipeline)
    pass.draw(3.toJsNumber())
    pass.end()

    val buffer = encoder.finish()
    device.queue.submit(arrayOf(buffer).toJsArray())
}

// language=WGSL
private const val myShader = """
@vertex fn vs(@builtin(vertex_index) vertexIndex : u32) -> @builtin(position) vec4f {
    let pos = array(
      vec2f( 0.0,  0.5),  // top center
      vec2f(-0.5, -0.5),  // bottom left
      vec2f( 0.5, -0.5)   // bottom right
    );

    return vec4f(pos[vertexIndex], 0.0, 1.0);
}
 
@fragment fn fs() -> @location(0) vec4f {
    return vec4f(1.0, 0.0, 0.0, 1.0);
}
"""