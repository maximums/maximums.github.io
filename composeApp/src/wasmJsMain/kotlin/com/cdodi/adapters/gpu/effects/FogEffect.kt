package com.cdodi.adapters.gpu.effects

import blog.composeapp.generated.resources.Res
import com.cdodi.adapters.compose.SHADER_TIME_PERIOD
import com.cdodi.adapters.gpu.FullscreenPass
import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.time.Frame
import com.cdodi.webgpu.bindings.GPUAddressMode
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUFilterMode
import com.cdodi.webgpu.bindings.GPUSampler
import com.cdodi.webgpu.bindings.GPUSamplerDescriptor
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.bindings.clampToEdge
import com.cdodi.webgpu.bindings.linear
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.runtime.Float32Staging

/**
 * The fog of "Breath on the window": `fog-roll` covers the scene being left, `fog-hold` breathes while the next scene
 * gets ready, `fog-clear` parts over the scene being entered. Played backwards, each one runs the other way over the
 * other scene, so going back undoes the transition.
 */
class FogEffect : GpuEffectRenderer {

    companion object {
        val IDS = setOf(GpuEffectIds.FOG_ROLL, GpuEffectIds.FOG_HOLD, GpuEffectIds.FOG_CLEAR)

        private const val ROLL = 0f
        private const val HOLD = 1f
        private const val CLEAR = 2f
        private const val LEAVING = 0f
        private const val ENTERING = 1f
    }

    override val ids: Set<String> = IDS

    private lateinit var gpu: GpuContext
    private lateinit var resources: ResourceScope
    private lateinit var pass: FullscreenPass
    private lateinit var uniforms: GPUBuffer
    private lateinit var sampler: GPUSampler

    // The bind group names the two scene textures, so it is rebuilt only when they are (after a resize).
    private var bindGroup: GPUBindGroup? = null
    private var boundFrom: GPUTextureView? = null
    private var boundTo: GPUTextureView? = null

    // struct Uniforms { resolution: vec2f, time: f32, amount: f32, mode: f32, side: f32, _pad: vec2f }
    private val values = FloatArray(8)
    private val staging = Float32Staging(values.size)

    override suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) {
        this.gpu = gpu
        val code = Res.readBytes("files/shaders/effects/fog.wgsl").decodeToString()
        pass = FullscreenPass.create(gpu, code, format, label = "fog")
        resources = gpu.resourceScope()
        uniforms = resources.buffer(
            GPUBufferDescriptor(size = (values.size * Float.SIZE_BYTES).toDouble(), usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "fog uniforms")
        )
        sampler = gpu.device.createSampler(
            GPUSamplerDescriptor(
                addressModeU = GPUAddressMode.clampToEdge,
                addressModeV = GPUAddressMode.clampToEdge,
                magFilter = GPUFilterMode.linear,
                minFilter = GPUFilterMode.linear,
            )
        )
    }

    override fun resize(width: Int, height: Int) {
        values[0] = width.toFloat()
        values[1] = height.toFloat()
    }

    override fun encode(encoder: GPUCommandEncoder, target: GPUTextureView, from: GPUTextureView, to: GPUTextureView, state: EffectState, frame: Frame) {
        // Progress is already mirrored when the effect plays backwards; what changes is which scene is behind it.
        val (mode, amount, side) = when (state.effect.id) {
            GpuEffectIds.FOG_ROLL -> Triple(ROLL, state.progress ?: 1f, if (state.reversed) ENTERING else LEAVING)
            GpuEffectIds.FOG_HOLD -> Triple(HOLD, 1f, if (state.reversed) ENTERING else LEAVING)
            else -> Triple(CLEAR, state.progress ?: 1f, if (state.reversed) LEAVING else ENTERING)
        }
        values[2] = (frame.elapsed % SHADER_TIME_PERIOD).toFloat()
        values[3] = amount
        values[4] = mode
        values[5] = side
        gpu.queue.writeBuffer(uniforms, 0.0, staging.fill(values))
        pass.encode(encoder, target, bindGroupFor(from, to))
    }

    private fun bindGroupFor(from: GPUTextureView, to: GPUTextureView): GPUBindGroup {
        bindGroup?.let { if (from === boundFrom && to === boundTo) return it }
        return gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = pass.bindGroupLayout,
                entries = listOf(
                    GPUBindGroupEntry(binding = 0, resource = GPUBufferBinding(uniforms)),
                    GPUBindGroupEntry(binding = 1, resource = from),
                    GPUBindGroupEntry(binding = 2, resource = to),
                    GPUBindGroupEntry(binding = 3, resource = sampler),
                ),
                label = "fog",
            )
        ).also {
            bindGroup = it
            boundFrom = from
            boundTo = to
        }
    }

    override fun close() {
        if (::resources.isInitialized) resources.close()
    }
}
