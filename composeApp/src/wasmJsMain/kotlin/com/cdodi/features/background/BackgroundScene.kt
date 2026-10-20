package com.cdodi.features.background

import blog.composeapp.generated.resources.Res
import com.cdodi.adapters.compose.SHADER_TIME_PERIOD
import com.cdodi.adapters.gpu.FullscreenPass
import com.cdodi.adapters.gpu.GpuProfiler
import com.cdodi.adapters.gpu.GpuTheme
import com.cdodi.adapters.gpu.GpuWidgets
import com.cdodi.adapters.gpu.Scene
import com.cdodi.adapters.input.InputState
import com.cdodi.core.time.Clock
import com.cdodi.core.time.Frame
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
import com.cdodi.webgpu.resource.Float32Staging
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.shader.compileShader
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil
import kotlin.math.max

/**
 * The rainy street behind the pages (the design canvas's background study), on the ambient clock: it freezes on a
 * still frame when the user prefers reduced motion.
 *
 * - The street itself (sky, lights and their reflections, fog: `street.wgsl`) is soft, so it's drawn at the viewer's
 *   [quality] (a quarter, half or all of the pixels) into an offscreen texture.
 * - One full-resolution pass scales it up and adds what must stay sharp: the rain, the vignette, the dither
 *   (`rain.wgsl`).
 * - Then the [widgets] that UI elements report, also at full resolution: the sphere on the About page.
 */
class BackgroundScene(
    override val clock: Clock,
    private val quality: StateFlow<BackgroundQuality>,
    private val widgets: GpuWidgets,
    private val theme: GpuTheme,
    private val profiler: GpuProfiler? = null,
) : Scene {

    private lateinit var gpu: GpuContext
    private lateinit var format: GPUTextureFormat
    private lateinit var resources: ResourceScope
    private lateinit var street: FullscreenPass
    private lateinit var streetUniforms: GPUBuffer
    private lateinit var streetGroup: GPUBindGroup
    private lateinit var rain: GPURenderPipeline
    private lateinit var rainTheme: GPUBindGroup
    private lateinit var widgetPipeline: GPURenderPipeline
    private lateinit var widgetFrame: GPUBuffer
    private lateinit var widgetBuffer: GPUBuffer
    private lateinit var widgetGroup: GPUBindGroup
    private lateinit var sampler: GPUSampler

    private var offscreen: GPUTexture? = null
    private var offscreenView: GPUTextureView? = null
    private var rainGroup: GPUBindGroup? = null
    private var offscreenWidth = 0
    private var offscreenHeight = 0

    private var width = 0
    private var height = 0
    private var time = 0f

    // struct Uniforms { resolution: vec2f, time: f32, _pad: f32 } in street.wgsl, struct Frame the same in rain.wgsl and
    // widgets.wgsl
    private val frameValues = FloatArray(4)
    private val frameStaging = Float32Staging(frameValues.size)
    // struct Widget { rect: vec4f, style: vec4f }
    private val widgetValues = FloatArray(MAX_WIDGETS * 8)
    private val widgetStaging = Float32Staging(widgetValues.size)

    override suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) {
        this.gpu = gpu
        this.format = format
        resources = gpu.resourceScope()
        street = FullscreenPass.create(gpu, Res.readBytes("files/shaders/background/street.wgsl").decodeToString(), format, label = "street", theme = theme)
        streetUniforms = uniform("street uniforms")
        val lights = resources.buffer(
            GPUBufferDescriptor(size = StreetLights.values.size * 4.0, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_DST, label = "street lights")
        )
        gpu.queue.writeBuffer(lights, 0.0, Float32Staging(StreetLights.values.size).fill(StreetLights.values))
        streetGroup = gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = street.bindGroupLayout,
                entries = listOf(GPUBindGroupEntry(0, GPUBufferBinding(streetUniforms)), GPUBindGroupEntry(1, GPUBufferBinding(lights))),
            )
        )

        val rainModule = gpu.compileShader(theme.shader(Res.readBytes("files/shaders/background/rain.wgsl").decodeToString()), label = "rain")
        rain = pipeline(rainModule, "rain", blended = false)
        rainTheme = theme.bindGroup(rain.getBindGroupLayout(GpuTheme.GROUP))
        val widgetModule = gpu.compileShader(Res.readBytes("files/shaders/background/widgets.wgsl").decodeToString(), label = "widgets")
        widgetPipeline = pipeline(widgetModule, "widgets", blended = true)

        widgetFrame = uniform("widget frame")
        widgetBuffer = resources.buffer(
            GPUBufferDescriptor(size = widgetValues.size * 4.0, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_DST, label = "widgets")
        )
        widgetGroup = gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = widgetPipeline.getBindGroupLayout(0),
                entries = listOf(GPUBindGroupEntry(0, GPUBufferBinding(widgetFrame)), GPUBindGroupEntry(1, GPUBufferBinding(widgetBuffer))),
            )
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
        this.width = width
        this.height = height
    }

    override fun update(frame: Frame, input: InputState) {
        time = (frame.elapsed % SHADER_TIME_PERIOD).toFloat()
    }

    override fun encode(encoder: GPUCommandEncoder, target: GPUTextureView) {
        val source = offscreenFor(quality.value.scale)
        val span = profiler?.span("background")

        frameValues[0] = offscreenWidth.toFloat()
        frameValues[1] = offscreenHeight.toFloat()
        frameValues[2] = time
        gpu.queue.writeBuffer(streetUniforms, 0.0, frameStaging.fill(frameValues))
        street.encode(encoder, source, streetGroup, span?.render(first = true, last = false))

        // Where the elements are now: this runs after Compose has laid the frame out.
        val count = collectWidgets()
        frameValues[0] = width.toFloat()
        frameValues[1] = height.toFloat()
        gpu.queue.writeBuffer(widgetFrame, 0.0, frameStaging.fill(frameValues))
        if (count > 0) gpu.queue.writeBuffer(widgetBuffer, 0.0, widgetStaging.fill(widgetValues, count * 8))

        encoder.beginRenderPass(
            GPURenderPassDescriptor(
                colorAttachments = listOf(
                    GPURenderPassColorAttachment(view = target, clearValue = GPUColorDict(0.0, 0.0, 0.0, 1.0), loadOp = GPULoadOp.clear, storeOp = GPUStoreOp.store)
                ),
                timestampWrites = span?.render(first = false, last = true),
                label = "background rain and widgets",
            )
        ).apply {
            setPipeline(rain)
            setBindGroup(0, rainGroup)
            setBindGroup(GpuTheme.GROUP, rainTheme)
            draw(3)
            if (count > 0) {
                setPipeline(widgetPipeline)
                setBindGroup(0, widgetGroup)
                draw(6, count)
            }
            end()
        }
    }

    override fun close() {
        offscreen?.destroy()
        if (::resources.isInitialized) resources.close()
    }

    /** The visible widgets into [widgetValues]; returns how many. */
    private fun collectWidgets(): Int {
        var count = 0
        for (widget in widgets.current) {
            if (count == MAX_WIDGETS) break
            val visibility = widget.visibility()
            if (visibility <= 0.001f || widget.width <= 0f || widget.height <= 0f) continue
            val at = count * 8
            widgetValues[at] = widget.left
            widgetValues[at + 1] = widget.top
            widgetValues[at + 2] = widget.width
            widgetValues[at + 3] = widget.height
            widgetValues[at + 4] = visibility
            count++
        }
        return count
    }

    /** The texture the street renders into at [scale], made again when the scale or the target size changed. */
    private fun offscreenFor(scale: Float): GPUTextureView {
        val w = max(1, ceil(width * scale).toInt())
        val h = max(1, ceil(height * scale).toInt())
        offscreenView?.let { if (w == offscreenWidth && h == offscreenHeight) return it }
        offscreen?.destroy()
        val texture = gpu.device.createTexture(
            GPUTextureDescriptor(
                size = GPUExtent3DDict(width = w, height = h),
                format = format,
                usage = GPUTextureUsage.RENDER_ATTACHMENT or GPUTextureUsage.TEXTURE_BINDING,
                label = "background at ${(scale * 100).toInt()}%",
            )
        )
        val view = texture.createView()
        offscreen = texture
        offscreenView = view
        offscreenWidth = w
        offscreenHeight = h
        rainGroup = gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = rain.getBindGroupLayout(0),
                entries = listOf(GPUBindGroupEntry(0, view), GPUBindGroupEntry(1, sampler), GPUBindGroupEntry(2, GPUBufferBinding(widgetFrame))),
            )
        )
        return view
    }

    private fun uniform(label: String) =
        resources.buffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = label))

    private fun pipeline(module: GPUShaderModule, label: String, blended: Boolean): GPURenderPipeline {
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
                vertex = GPUVertexState(module = module, entryPoint = "vs"),
                fragment = GPUFragmentState(module = module, entryPoint = "fs", targets = listOf(GPUColorTargetState(format = format, blend = blend))),
                label = label,
            )
        )
    }

    private companion object {
        const val MAX_WIDGETS = 16
    }
}
