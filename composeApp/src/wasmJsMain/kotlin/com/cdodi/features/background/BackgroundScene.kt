package com.cdodi.features.background

import blog.composeapp.generated.resources.Res
import com.cdodi.adapters.compose.SHADER_TIME_PERIOD
import com.cdodi.adapters.gpu.FullscreenPass
import com.cdodi.adapters.gpu.Scene
import com.cdodi.adapters.input.InputState
import com.cdodi.core.time.Clock
import com.cdodi.core.time.Frame
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.runtime.Float32Staging

/**
 * The rainy street behind every page: the bokeh shader, now on WebGPU. It runs on the ambient clock, so it freezes on
 * a still frame when the user prefers reduced motion.
 */
class BackgroundScene(override val clock: Clock) : Scene {

    private lateinit var gpu: GpuContext
    private lateinit var resources: ResourceScope
    private lateinit var pass: FullscreenPass
    private lateinit var uniforms: GPUBuffer
    private lateinit var bindGroup: GPUBindGroup

    // struct Uniforms { resolution: vec2f, time: f32, _pad: f32 }
    private val values = FloatArray(4)
    private val staging = Float32Staging(values.size)

    override suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) {
        this.gpu = gpu
        val code = Res.readBytes("files/shaders/background/bokeh.wgsl").decodeToString()
        pass = FullscreenPass.create(gpu, code, format, label = "background")
        resources = gpu.resourceScope()
        uniforms = resources.buffer(
            GPUBufferDescriptor(size = (values.size * Float.SIZE_BYTES).toDouble(), usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "background uniforms")
        )
        bindGroup = gpu.device.createBindGroup(
            GPUBindGroupDescriptor(layout = pass.bindGroupLayout, entries = listOf(GPUBindGroupEntry(binding = 0, resource = GPUBufferBinding(uniforms))))
        )
    }

    override fun resize(width: Int, height: Int) {
        values[0] = width.toFloat()
        values[1] = height.toFloat()
    }

    override fun update(frame: Frame, input: InputState) {
        values[2] = (frame.elapsed % SHADER_TIME_PERIOD).toFloat()
    }

    override fun encode(encoder: GPUCommandEncoder, target: GPUTextureView) {
        gpu.queue.writeBuffer(uniforms, 0.0, staging.fill(values))
        pass.encode(encoder, target, bindGroup)
    }

    override fun close() {
        if (::resources.isInitialized) resources.close()
    }
}
