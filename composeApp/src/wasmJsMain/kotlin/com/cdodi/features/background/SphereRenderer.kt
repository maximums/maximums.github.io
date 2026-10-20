package com.cdodi.features.background

import blog.composeapp.generated.resources.Res
import com.cdodi.adapters.gpu.GpuWidgetRenderer
import com.cdodi.adapters.gpu.GpuWidgets
import com.cdodi.adapters.gpu.WidgetFrame
import com.cdodi.webgpu.bindings.GPUAutoLayoutMode
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBlendComponent
import com.cdodi.webgpu.bindings.GPUBlendFactor
import com.cdodi.webgpu.bindings.GPUBlendState
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUColorTargetState
import com.cdodi.webgpu.bindings.GPUFragmentState
import com.cdodi.webgpu.bindings.GPURenderPassEncoder
import com.cdodi.webgpu.bindings.GPURenderPipeline
import com.cdodi.webgpu.bindings.GPURenderPipelineDescriptor
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUVertexState
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.one
import com.cdodi.webgpu.bindings.oneMinusSrcAlpha
import com.cdodi.webgpu.bindings.srcAlpha
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.Float32Staging
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.shader.compileShader

/**
 * The lit sphere behind the About page (`background/sphere.wgsl`): one quad per sphere, under its element, its light
 * circling on the scene's clock.
 */
class SphereRenderer : GpuWidgetRenderer {

    override val kind = GpuWidgets.Kind.Sphere

    private lateinit var gpu: GpuContext
    private lateinit var resources: ResourceScope
    private lateinit var pipeline: GPURenderPipeline
    private lateinit var frameBuffer: GPUBuffer
    private lateinit var sphereBuffer: GPUBuffer
    private lateinit var group: GPUBindGroup

    // struct Frame { resolution: vec2f, time: f32, _pad: f32 }
    private val frameValues = FloatArray(4)
    private val frameStaging = Float32Staging(frameValues.size)
    // struct Widget { rect: vec4f, style: vec4f }
    private val sphereValues = FloatArray(MAX_SPHERES * 8)
    private val sphereStaging = Float32Staging(sphereValues.size)

    override suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) {
        this.gpu = gpu
        resources = gpu.resourceScope()
        val module = gpu.compileShader(Res.readBytes("files/shaders/background/sphere.wgsl").decodeToString(), label = "sphere")
        pipeline = gpu.device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                layout = GPUAutoLayoutMode.auto,
                vertex = GPUVertexState(module = module, entryPoint = "vs"),
                fragment = GPUFragmentState(
                    module = module,
                    entryPoint = "fs",
                    targets = listOf(
                        GPUColorTargetState(
                            format = format,
                            blend = GPUBlendState(
                                color = GPUBlendComponent(srcFactor = GPUBlendFactor.srcAlpha, dstFactor = GPUBlendFactor.oneMinusSrcAlpha),
                                alpha = GPUBlendComponent(srcFactor = GPUBlendFactor.one, dstFactor = GPUBlendFactor.oneMinusSrcAlpha),
                            ),
                        )
                    ),
                ),
                label = "sphere",
            )
        )
        frameBuffer = resources.buffer(GPUBufferDescriptor(size = frameValues.size * 4.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "sphere frame"))
        sphereBuffer = resources.buffer(GPUBufferDescriptor(size = sphereValues.size * 4.0, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_DST, label = "spheres"))
        group = gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(0),
                entries = listOf(GPUBindGroupEntry(0, GPUBufferBinding(frameBuffer)), GPUBindGroupEntry(1, GPUBufferBinding(sphereBuffer))),
            )
        )
    }

    override fun draw(pass: GPURenderPassEncoder, widgets: List<GpuWidgets.Widget>, frame: WidgetFrame) {
        val count = minOf(widgets.size, MAX_SPHERES)
        for (i in 0 until count) {
            val widget = widgets[i]
            val at = i * 8
            sphereValues[at] = widget.left
            sphereValues[at + 1] = widget.top
            sphereValues[at + 2] = widget.width
            sphereValues[at + 3] = widget.height
            sphereValues[at + 4] = widget.visibility()
        }
        frameValues[0] = frame.width.toFloat()
        frameValues[1] = frame.height.toFloat()
        frameValues[2] = frame.time
        gpu.queue.writeBuffer(frameBuffer, 0.0, frameStaging.fill(frameValues))
        gpu.queue.writeBuffer(sphereBuffer, 0.0, sphereStaging.fill(sphereValues, count * 8))
        pass.setPipeline(pipeline)
        pass.setBindGroup(0, group)
        pass.draw(6, count)
    }

    override fun close() {
        if (::resources.isInitialized) resources.close()
    }

    private companion object {
        const val MAX_SPHERES = 16
    }
}
