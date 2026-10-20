package com.cdodi.adapters.gpu.model

import blog.composeapp.generated.resources.Res
import com.cdodi.webgpu.bindings.GPUAutoLayoutMode
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUColorDict
import com.cdodi.webgpu.bindings.GPUColorTargetState
import com.cdodi.webgpu.bindings.GPUFilterMode
import com.cdodi.webgpu.bindings.GPUFragmentState
import com.cdodi.webgpu.bindings.GPULoadOp
import com.cdodi.webgpu.bindings.GPURenderPassColorAttachment
import com.cdodi.webgpu.bindings.GPURenderPassDescriptor
import com.cdodi.webgpu.bindings.GPURenderPipeline
import com.cdodi.webgpu.bindings.GPURenderPipelineDescriptor
import com.cdodi.webgpu.bindings.GPUSamplerDescriptor
import com.cdodi.webgpu.bindings.GPUShaderModule
import com.cdodi.webgpu.bindings.GPUStoreOp
import com.cdodi.webgpu.bindings.GPUTexture
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.GPUTextureViewDescriptor
import com.cdodi.webgpu.bindings.GPUVertexState
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.clear
import com.cdodi.webgpu.bindings.linear
import com.cdodi.webgpu.bindings.store
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.shader.compileShader
import kotlin.math.max

/**
 * Fills a texture's smaller mip levels from its largest. WebGPU has no `generateMipmap`, so each level is a render
 * pass (`common/mipmap.wgsl`) that averages 2×2 blocks of the level above. An sRGB texture is averaged as light, not as
 * stored numbers, so a mipmapped colour texture doesn't darken in the distance.
 */
class Mipmaps private constructor(private val gpu: GpuContext, private val module: GPUShaderModule) {

    private val sampler = gpu.device.createSampler(GPUSamplerDescriptor(magFilter = GPUFilterMode.linear, minFilter = GPUFilterMode.linear, label = "mipmaps"))

    /** One pipeline per texture format, made the first time a format is seen. */
    private val pipelines = HashMap<String, GPURenderPipeline>()

    /**
     * Writes levels 1 until [levels] of [texture] (in [format]) from its level 0, in a submission of its own. The
     * texture needs [USAGE] as well as whatever it's for.
     */
    fun generate(texture: GPUTexture, format: GPUTextureFormat, levels: Int) {
        if (levels < 2) return
        val pipeline = pipelines.getOrPut(format.toString()) {
            gpu.device.createRenderPipeline(
                GPURenderPipelineDescriptor(
                    layout = GPUAutoLayoutMode.auto,
                    vertex = GPUVertexState(module = module, entryPoint = "vs"),
                    fragment = GPUFragmentState(module = module, entryPoint = "fs", targets = listOf(GPUColorTargetState(format = format))),
                    label = "mipmaps $format",
                )
            )
        }
        val encoder = gpu.device.createCommandEncoder()
        for (level in 1 until levels) {
            val above = texture.createView(GPUTextureViewDescriptor(baseMipLevel = level - 1, mipLevelCount = 1))
            val group = gpu.device.createBindGroup(
                GPUBindGroupDescriptor(layout = pipeline.getBindGroupLayout(0), entries = listOf(GPUBindGroupEntry(0, above), GPUBindGroupEntry(1, sampler)))
            )
            encoder.beginRenderPass(
                GPURenderPassDescriptor(
                    colorAttachments = listOf(
                        GPURenderPassColorAttachment(
                            view = texture.createView(GPUTextureViewDescriptor(baseMipLevel = level, mipLevelCount = 1)),
                            clearValue = GPUColorDict(0.0, 0.0, 0.0, 0.0),
                            loadOp = GPULoadOp.clear,
                            storeOp = GPUStoreOp.store,
                        )
                    ),
                    label = "mip level $level",
                )
            ).apply {
                setPipeline(pipeline)
                setBindGroup(0, group)
                draw(3)
                end()
            }
        }
        gpu.queue.submit(listOf(encoder.finish()).toJsArray())
    }

    companion object {
        /** The usages [generate] needs: each level is sampled, and drawn into. */
        const val USAGE = GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.RENDER_ATTACHMENT

        /** How many levels a [width] × [height] texture has, down to 1 × 1: 11 for 1024 × 1024. */
        fun levelsFor(width: Int, height: Int): Int = 32 - max(width, height).countLeadingZeroBits()

        suspend fun create(gpu: GpuContext): Mipmaps =
            Mipmaps(gpu, gpu.compileShader(Res.readBytes("files/shaders/common/mipmap.wgsl").decodeToString(), label = "mipmaps"))
    }
}
