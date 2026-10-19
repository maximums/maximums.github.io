package com.cdodi.adapters.gpu

import blog.composeapp.generated.resources.Res
import com.cdodi.webgpu.bindings.GPUAddressMode
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
import com.cdodi.webgpu.bindings.GPUColorDict
import com.cdodi.webgpu.bindings.GPUColorTargetState
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUExtent3DDict
import com.cdodi.webgpu.bindings.GPUFilterMode
import com.cdodi.webgpu.bindings.GPUFragmentState
import com.cdodi.webgpu.bindings.GPULoadOp
import com.cdodi.webgpu.bindings.GPURenderPassColorAttachment
import com.cdodi.webgpu.bindings.GPURenderPassDescriptor
import com.cdodi.webgpu.bindings.GPURenderPassTimestampWrites
import com.cdodi.webgpu.bindings.GPURenderPipeline
import com.cdodi.webgpu.bindings.GPURenderPipelineDescriptor
import com.cdodi.webgpu.bindings.GPUSampler
import com.cdodi.webgpu.bindings.GPUSamplerDescriptor
import com.cdodi.webgpu.bindings.GPUShaderModule
import com.cdodi.webgpu.bindings.GPUStoreOp
import com.cdodi.webgpu.bindings.GPUTexture
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.bindings.GPUVertexState
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.clampToEdge
import com.cdodi.webgpu.bindings.clear
import com.cdodi.webgpu.bindings.linear
import com.cdodi.webgpu.bindings.one
import com.cdodi.webgpu.bindings.oneMinusSrcAlpha
import com.cdodi.webgpu.bindings.srcAlpha
import com.cdodi.webgpu.bindings.store
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.resource.Float32Staging
import com.cdodi.webgpu.shader.compileShader
import kotlin.math.max

/**
 * The glass over the scenes (DESIGN.md §5). Compose can't see the WebGPU canvas, so it can't blur what's behind a
 * pane; it reports each pane ([GpuWidgets.glass]) and this draws the part of the glass that needs the scene:
 *
 * 1. The scenes draw into [frameView] instead of the canvas.
 * 2. A dual Kawase blur takes the frame down [levels] half-size steps and back up to half size.
 * 3. One pass copies the frame to the canvas, then draws every pane from the blurred frame, desaturated, as blurred as
 *    the pane is fogged (a cleared pane shows the street sharp). Compose draws the fog, condensation and hairline on
 *    top of that.
 *
 * When no pane needs the street blurred, there's nothing to do: [collect] says so, and the scenes draw straight into
 * the canvas.
 */
class GlassCompositor(private val widgets: GpuWidgets) : AutoCloseable {

    private lateinit var gpu: GpuContext
    private lateinit var format: GPUTextureFormat
    private lateinit var resources: ResourceScope
    private lateinit var down: GPURenderPipeline
    private lateinit var up: GPURenderPipeline
    private lateinit var copy: GPURenderPipeline
    private lateinit var panePipeline: GPURenderPipeline
    private lateinit var sampler: GPUSampler
    private lateinit var view: GPUBuffer
    private lateinit var paneBuffer: GPUBuffer

    var isReady = false
        private set
    private var isClosed = false

    private var targets: Targets? = null

    // struct Pane { a: vec4f, b: vec4f, style: vec4f } in panes.wgsl
    private val paneValues = FloatArray(MAX_PANES * 12)
    private val paneStaging = Float32Staging(paneValues.size)
    private val corners = FloatArray(8)
    private val viewValues = FloatArray(4)
    private val viewStaging = Float32Staging(4)

    /** Where the scenes draw this frame when [collect] found panes. */
    val frameView: GPUTextureView get() = targets!!.frameView

    suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) {
        this.gpu = gpu
        this.format = format
        val blur = gpu.compileShader(Res.readBytes("files/shaders/glass/blur.wgsl").decodeToString(), label = "glass blur")
        val panes = gpu.compileShader(Res.readBytes("files/shaders/glass/panes.wgsl").decodeToString(), label = "glass panes")
        // The host may have closed (its device failed) while the shaders compiled: then there's nothing to set up.
        if (isClosed) return
        resources = gpu.resourceScope()
        down = pipeline(blur, "vs", "down", "glass blur down", blended = false)
        up = pipeline(blur, "vs", "up", "glass blur up", blended = false)
        copy = pipeline(panes, "copyVs", "copyFs", "glass copy", blended = false)
        panePipeline = pipeline(panes, "paneVs", "paneFs", "glass panes", blended = true)
        sampler = gpu.device.createSampler(
            GPUSamplerDescriptor(
                addressModeU = GPUAddressMode.clampToEdge,
                addressModeV = GPUAddressMode.clampToEdge,
                magFilter = GPUFilterMode.linear,
                minFilter = GPUFilterMode.linear,
            )
        )
        view = resources.buffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "glass view"))
        paneBuffer = resources.buffer(GPUBufferDescriptor(size = paneValues.size * 4.0, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_DST, label = "glass panes"))
        isReady = true
    }

    /**
     * Sizes the frame and the blur chain for a [width] × [height] canvas. [levels] half-size steps: more on a dense
     * screen, so the blur is about as wide in CSS pixels (DESIGN.md: blur 18).
     */
    fun resize(width: Int, height: Int, levels: Int) {
        targets?.close()
        targets = if (isReady && width > 0 && height > 0) Targets(width, height, levels) else null
    }

    /** Writes this frame's panes; returns how many need the street blurred (0: draw the scene straight to the canvas). */
    fun collect(): Int {
        if (!isReady || targets == null) return 0
        var count = 0
        for (pane in widgets.glass) {
            if (count == MAX_PANES) break
            val visibility = pane.visibility()
            val blur = pane.blur()
            if (visibility <= 0.001f || blur <= 0.001f || pane.width <= 0f || pane.height <= 0f) continue
            pane.corners(pane.width, pane.height, corners)
            val at = count * 12
            for (i in 0 until 4) {
                paneValues[at + i * 2] = pane.left + corners[i * 2]
                paneValues[at + i * 2 + 1] = pane.top + corners[i * 2 + 1]
            }
            paneValues[at + 8] = blur.coerceIn(0f, 1f)
            paneValues[at + 9] = visibility.coerceIn(0f, 1f)
            paneValues[at + 10] = pane.wipe().coerceIn(0f, 1f)
            paneValues[at + 11] = 0f
            count++
        }
        return count
    }

    /** Blurs the frame and draws it with [count] panes into [target]. */
    fun encode(encoder: GPUCommandEncoder, target: GPUTextureView, count: Int, profiler: GpuProfiler?) {
        val t = targets ?: return
        val span = profiler?.span("glass")
        viewValues[0] = t.width.toFloat()
        viewValues[1] = t.height.toFloat()
        gpu.queue.writeBuffer(view, 0.0, viewStaging.fill(viewValues))
        gpu.queue.writeBuffer(paneBuffer, 0.0, paneStaging.fill(paneValues, count * 12))

        t.passes.forEachIndexed { index, pass ->
            render(encoder, pass.target, if (index == 0) span?.render(first = true, last = false) else null, "glass blur") {
                setPipeline(pass.pipeline)
                setBindGroup(0, pass.group)
                draw(3)
            }
        }
        render(encoder, target, span?.render(first = t.passes.isEmpty(), last = true), "glass composite") {
            setPipeline(copy)
            setBindGroup(0, t.copyGroup)
            draw(3)
            setPipeline(panePipeline)
            setBindGroup(0, t.paneGroup)
            draw(6, count)
        }
    }

    private inline fun render(
        encoder: GPUCommandEncoder,
        target: GPUTextureView,
        timestamps: GPURenderPassTimestampWrites?,
        label: String,
        block: com.cdodi.webgpu.bindings.GPURenderPassEncoder.() -> Unit,
    ) {
        encoder.beginRenderPass(
            GPURenderPassDescriptor(
                colorAttachments = listOf(
                    GPURenderPassColorAttachment(view = target, clearValue = GPUColorDict(0.0, 0.0, 0.0, 1.0), loadOp = GPULoadOp.clear, storeOp = GPUStoreOp.store)
                ),
                timestampWrites = timestamps,
                label = label,
            )
        ).apply {
            block()
            end()
        }
    }

    override fun close() {
        isClosed = true
        targets?.close()
        targets = null
        if (::resources.isInitialized) resources.close()
    }

    private fun pipeline(module: GPUShaderModule, vertex: String, fragment: String, label: String, blended: Boolean): GPURenderPipeline {
        val blend = if (blended) {
            GPUBlendState(
                color = GPUBlendComponent(srcFactor = GPUBlendFactor.srcAlpha, dstFactor = GPUBlendFactor.oneMinusSrcAlpha),
                alpha = GPUBlendComponent(srcFactor = GPUBlendFactor.one, dstFactor = GPUBlendFactor.oneMinusSrcAlpha),
            )
        } else {
            null
        }
        return gpu.device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                layout = GPUAutoLayoutMode.auto,
                vertex = GPUVertexState(module = module, entryPoint = vertex),
                fragment = GPUFragmentState(module = module, entryPoint = fragment, targets = listOf(GPUColorTargetState(format = format, blend = blend))),
                label = label,
            )
        )
    }

    /** One blur step: [pipeline] reading through [group] into [target]. */
    private class Pass(val pipeline: GPURenderPipeline, val group: GPUBindGroup, val target: GPUTextureView)

    /** The frame, the blur chain and their bind groups, for one canvas size. */
    private inner class Targets(val width: Int, val height: Int, levels: Int) : AutoCloseable {
        private val textures = mutableListOf<GPUTexture>()
        private val uniforms = mutableListOf<GPUBuffer>()
        val frameView: GPUTextureView
        val passes = mutableListOf<Pass>()
        val copyGroup: GPUBindGroup
        val paneGroup: GPUBindGroup

        init {
            val frame = texture(width, height, "glass frame")
            frameView = frame.createView()

            // Down: frame → ½ → ¼ → … ; up: back to ½. The last up step's output is the blurred frame.
            val downViews = mutableListOf(frameView)
            val sizes = mutableListOf(width to height)
            for (level in 1..levels) {
                val w = max(1, width shr level)
                val h = max(1, height shr level)
                downViews += texture(w, h, "glass down $level").createView()
                sizes += w to h
                passes += Pass(down, group(down, downViews[level - 1], sizes[level - 1]), downViews[level])
            }
            var source = downViews[levels]
            var sourceSize = sizes[levels]
            for (level in levels - 1 downTo 1) {
                val (w, h) = sizes[level]
                val target = texture(w, h, "glass up $level").createView()
                passes += Pass(up, group(up, source, sourceSize), target)
                source = target
                sourceSize = w to h
            }
            val blurred = source

            copyGroup = gpu.device.createBindGroup(
                GPUBindGroupDescriptor(layout = copy.getBindGroupLayout(0), entries = listOf(GPUBindGroupEntry(0, frameView), GPUBindGroupEntry(1, sampler)))
            )
            paneGroup = gpu.device.createBindGroup(
                GPUBindGroupDescriptor(
                    layout = panePipeline.getBindGroupLayout(0),
                    entries = listOf(
                        GPUBindGroupEntry(0, frameView),
                        GPUBindGroupEntry(1, sampler),
                        GPUBindGroupEntry(2, blurred),
                        GPUBindGroupEntry(3, GPUBufferBinding(view)),
                        GPUBindGroupEntry(4, GPUBufferBinding(paneBuffer)),
                    ),
                )
            )
        }

        /** A bind group reading [source] (of [size]) for one blur step, with its texel size and tap offset. */
        private fun group(pipeline: GPURenderPipeline, source: GPUTextureView, size: Pair<Int, Int>): GPUBindGroup {
            val params = gpu.device.createBuffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "glass blur step"))
            uniforms += params
            val values = floatArrayOf(1f / size.first, 1f / size.second, 1f, 0f)
            gpu.queue.writeBuffer(params, 0.0, Float32Staging(4).fill(values))
            return gpu.device.createBindGroup(
                GPUBindGroupDescriptor(
                    layout = pipeline.getBindGroupLayout(0),
                    entries = listOf(GPUBindGroupEntry(0, source), GPUBindGroupEntry(1, sampler), GPUBindGroupEntry(2, GPUBufferBinding(params))),
                )
            )
        }

        private fun texture(w: Int, h: Int, label: String): GPUTexture = gpu.device.createTexture(
            GPUTextureDescriptor(
                size = GPUExtent3DDict(width = w, height = h),
                format = format,
                usage = GPUTextureUsage.RENDER_ATTACHMENT or GPUTextureUsage.TEXTURE_BINDING,
                label = label,
            )
        ).also { textures += it }

        override fun close() {
            textures.forEach { it.destroy() }
            uniforms.forEach { it.destroy() }
        }
    }

    private companion object {
        const val MAX_PANES = 32
    }
}
