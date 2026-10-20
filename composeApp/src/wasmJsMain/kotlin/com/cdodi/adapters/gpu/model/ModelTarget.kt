package com.cdodi.adapters.gpu.model

import com.cdodi.webgpu.bindings.GPUColorDict
import com.cdodi.webgpu.bindings.GPUExtent3DDict
import com.cdodi.webgpu.bindings.GPULoadOp
import com.cdodi.webgpu.bindings.GPURenderPassColorAttachment
import com.cdodi.webgpu.bindings.GPURenderPassDepthStencilAttachment
import com.cdodi.webgpu.bindings.GPUStoreOp
import com.cdodi.webgpu.bindings.GPUTexture
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.bindings.clear
import com.cdodi.webgpu.bindings.depth24plus
import com.cdodi.webgpu.bindings.discard
import com.cdodi.webgpu.context.GpuContext

/**
 * Where a model is drawn before it's laid over the scene: colour multisampled [SAMPLES] times, with depth, resolved
 * into [resolved], which the composite reads. It's the size of the widget the model is drawn in ([fit]), not of the
 * screen. It clears to transparent, so after the resolve each pixel holds the model's colour times its coverage: ready
 * for premultiplied blending, edges antialiased.
 */
class ModelTarget(private val gpu: GpuContext, val format: GPUTextureFormat) : AutoCloseable {

    var width = 0
        private set
    var height = 0
        private set

    private var textures: List<GPUTexture> = emptyList()
    private lateinit var multisampled: GPUTextureView
    private lateinit var depth: GPUTextureView

    /** The drawn model, one sample per pixel: what the composite samples. */
    lateinit var resolved: GPUTextureView
        private set

    /** Makes the textures [width] × [height] unless they are; true when they were made again (and [resolved] with them). */
    fun fit(width: Int, height: Int): Boolean {
        if (width == this.width && height == this.height) return false
        textures.forEach { it.destroy() }
        fun texture(format: GPUTextureFormat, samples: Int, usage: Int, label: String) = gpu.device.createTexture(
            GPUTextureDescriptor(size = GPUExtent3DDict(width = width, height = height), sampleCount = samples, format = format, usage = usage, label = label)
        )
        val colour = texture(format, SAMPLES, GPUTextureUsage.RENDER_ATTACHMENT, "model, multisampled")
        val depthTexture = texture(DEPTH_FORMAT, SAMPLES, GPUTextureUsage.RENDER_ATTACHMENT, "model depth")
        val single = texture(format, 1, GPUTextureUsage.RENDER_ATTACHMENT or GPUTextureUsage.TEXTURE_BINDING, "model")
        textures = listOf(colour, depthTexture, single)
        multisampled = colour.createView()
        depth = depthTexture.createView()
        resolved = single.createView()
        this.width = width
        this.height = height
        return true
    }

    /** Clears to transparent and resolves into [resolved]; the multisampled colour is thrown away after. */
    fun colorAttachment() = GPURenderPassColorAttachment(
        view = multisampled,
        resolveTarget = resolved,
        clearValue = GPUColorDict(0.0, 0.0, 0.0, 0.0),
        loadOp = GPULoadOp.clear,
        storeOp = GPUStoreOp.discard,
    )

    /** Clears to the far plane; depth is only needed during the pass. */
    fun depthAttachment() = GPURenderPassDepthStencilAttachment(view = depth, depthClearValue = 1f, depthLoadOp = GPULoadOp.clear, depthStoreOp = GPUStoreOp.discard)

    override fun close() {
        textures.forEach { it.destroy() }
        textures = emptyList()
        width = 0
        height = 0
    }

    companion object {
        const val SAMPLES = 4
        val DEPTH_FORMAT: GPUTextureFormat get() = GPUTextureFormat.depth24plus
    }
}
