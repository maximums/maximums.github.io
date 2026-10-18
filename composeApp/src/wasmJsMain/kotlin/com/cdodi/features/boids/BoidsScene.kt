package com.cdodi.features.boids

import blog.composeapp.generated.resources.Res
import com.cdodi.adapters.compose.SHADER_TIME_PERIOD
import com.cdodi.adapters.gpu.GpuProfiler
import com.cdodi.adapters.gpu.Scene
import com.cdodi.adapters.input.InputState
import com.cdodi.core.time.Clock
import com.cdodi.core.time.Frame
import com.cdodi.webgpu.bindings.GPUAutoLayoutMode
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUColorDict
import com.cdodi.webgpu.bindings.GPUColorTargetState
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUExtent3DDict
import com.cdodi.webgpu.bindings.GPUFragmentState
import com.cdodi.webgpu.bindings.GPULoadOp
import com.cdodi.webgpu.bindings.GPUMultisampleState
import com.cdodi.webgpu.bindings.GPURenderPassColorAttachment
import com.cdodi.webgpu.bindings.GPURenderPassDescriptor
import com.cdodi.webgpu.bindings.GPURenderPipeline
import com.cdodi.webgpu.bindings.GPURenderPipelineDescriptor
import com.cdodi.webgpu.bindings.GPUShaderModule
import com.cdodi.webgpu.bindings.GPUStoreOp
import com.cdodi.webgpu.bindings.GPUTexture
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.bindings.GPUVertexState
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.clear
import com.cdodi.webgpu.bindings.discard
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.runtime.Float32Staging
import com.cdodi.webgpu.shader.compileShader
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The murmuration as a scene: a [BoidsSimulation] stepped on the boids clock through the spatial grid, drawn as dark
 * birds against a lit fog bank, with 4× multisampling so the small triangles don't shimmer.
 *
 * Input, while the pointer isn't on a widget: the primary button draws the flock to the pointer, the secondary button
 * scatters it away, and the wheel sets how far that reaches.
 */
class BoidsScene(
    override val clock: Clock,
    private val controller: BoidsController,
    private val profiler: GpuProfiler? = null,
) : Scene {

    private lateinit var gpu: GpuContext
    private lateinit var resources: ResourceScope
    private lateinit var simulation: BoidsSimulation
    private lateinit var fogPipeline: GPURenderPipeline
    private lateinit var birdPipeline: GPURenderPipeline
    private lateinit var viewBuffer: GPUBuffer
    private lateinit var fogGroup: GPUBindGroup
    private lateinit var format: GPUTextureFormat
    private val birdGroups = mutableMapOf<GPUBuffer, GPUBindGroup>()

    private var multisampled: GPUTexture? = null
    private var multisampledView: GPUTextureView? = null
    private var width = 0
    private var height = 0

    private var params = BoidsParams()
    private var dt = 0f
    private var pointerRadius = BoidsParams().pointerRadius

    // struct View { world: vec2f, birdSize, time, lamp: vec2f, _pad: vec2f }
    private val view = FloatArray(8)
    private val viewStaging = Float32Staging(view.size)

    override suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) {
        this.gpu = gpu
        this.format = format
        resources = gpu.resourceScope()
        simulation = BoidsSimulation.create(
            gpu,
            Res.readBytes("files/shaders/boids/step.wgsl").decodeToString(),
            Res.readBytes("files/shaders/common/scan.wgsl").decodeToString(),
            capacity = INITIAL_CAPACITY,
        )
        val module = gpu.compileShader(Res.readBytes("files/shaders/boids/render.wgsl").decodeToString(), label = "boids render")
        fogPipeline = pipeline(module, "fogVertex", "fogFragment", "fog bank")
        birdPipeline = pipeline(module, "birdVertex", "birdFragment", "birds")
        viewBuffer = resources.buffer(
            GPUBufferDescriptor(size = (view.size * Float.SIZE_BYTES).toDouble(), usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "boids view")
        )
        fogGroup = gpu.device.createBindGroup(
            GPUBindGroupDescriptor(layout = fogPipeline.getBindGroupLayout(0), entries = listOf(GPUBindGroupEntry(0, GPUBufferBinding(viewBuffer))))
        )
    }

    override fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        multisampled?.destroy()
        multisampled = gpu.device.createTexture(
            GPUTextureDescriptor(
                size = GPUExtent3DDict(width = width, height = height),
                sampleCount = SAMPLES,
                format = format,
                usage = GPUTextureUsage.RENDER_ATTACHMENT,
                label = "boids multisampled",
            )
        ).also { multisampledView = it.createView() }
    }

    override fun update(frame: Frame, input: InputState) {
        val status = controller.status.value
        val world = status.params.copy(worldWidth = width.toFloat(), worldHeight = height.toFloat())
        if (controller.scatterRequested || (simulation.count == 0 && width > 0)) {
            controller.scatterRequested = false
            val capacity = simulation.capacity
            simulation.ensureCapacity(status.count)
            if (simulation.capacity != capacity) birdGroups.clear() // they bound the buffers growing replaced
            simulation.scatter(status.count, world)
        }

        if (input.wheel != 0f) pointerRadius = (pointerRadius * exp(-input.wheel * 0.0015f)).coerceIn(40f, 600f)
        val pointerMode = when {
            !input.hasPointer -> 0f
            input.isPressed -> 1f
            input.isPanPressed -> -1f
            else -> 0f
        }
        params = world.copy(pointerX = input.pointerX, pointerY = input.pointerY, pointerRadius = pointerRadius, pointerMode = pointerMode)
        // Paused, the clock's dt is zero and the flock holds still. A long frame is cut short rather than flung.
        dt = min(frame.dt, 1f / 30f)
        view[3] = (frame.elapsed % SHADER_TIME_PERIOD).toFloat()
    }

    override fun encode(encoder: GPUCommandEncoder, target: GPUTextureView) {
        if (dt > 0f) simulation.encodeGridStep(encoder, params, dt, profiler)
        controller.reportGrid(simulation.grid)

        view[0] = width.toFloat()
        view[1] = height.toFloat()
        view[2] = max(6f, min(width, height) / 110f)
        // The lamp is low on the right: birds heading down and right catch its light.
        view[4] = 0.6f / sqrt(0.6f * 0.6f + 0.8f * 0.8f)
        view[5] = 0.8f / sqrt(0.6f * 0.6f + 0.8f * 0.8f)
        gpu.queue.writeBuffer(viewBuffer, 0.0, viewStaging.fill(view))

        encoder.beginRenderPass(
            GPURenderPassDescriptor(
                colorAttachments = listOf(
                    GPURenderPassColorAttachment(
                        view = multisampledView!!,
                        resolveTarget = target,
                        clearValue = GPUColorDict(r = 0.0, g = 0.0, b = 0.0, a = 1.0),
                        loadOp = GPULoadOp.clear,
                        storeOp = GPUStoreOp.discard,
                    )
                ),
                timestampWrites = profiler?.span("draw")?.render(),
                label = "murmuration",
            )
        ).apply {
            setPipeline(fogPipeline)
            setBindGroup(0, fogGroup)
            draw(3)
            setPipeline(birdPipeline)
            setBindGroup(0, birdGroupFor(simulation.current))
            draw(3, simulation.count)
            end()
        }
    }

    override fun close() {
        multisampled?.destroy()
        if (::simulation.isInitialized) simulation.close()
        if (::resources.isInitialized) resources.close()
    }

    private fun pipeline(module: GPUShaderModule, vertex: String, fragment: String, label: String) = gpu.device.createRenderPipeline(
        GPURenderPipelineDescriptor(
            layout = GPUAutoLayoutMode.auto,
            vertex = GPUVertexState(module = module, entryPoint = vertex),
            fragment = GPUFragmentState(module = module, entryPoint = fragment, targets = listOf(GPUColorTargetState(format = format))),
            multisample = GPUMultisampleState(count = SAMPLES),
            label = label,
        )
    )

    private fun birdGroupFor(boids: GPUBuffer): GPUBindGroup = birdGroups.getOrPut(boids) {
        gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = birdPipeline.getBindGroupLayout(0),
                entries = listOf(GPUBindGroupEntry(0, GPUBufferBinding(viewBuffer)), GPUBindGroupEntry(1, GPUBufferBinding(boids))),
                label = "birds",
            )
        )
    }

    private companion object {
        const val SAMPLES = 4

        /** Buffers for this many boids to begin with; a bigger flock grows them. */
        const val INITIAL_CAPACITY = 32_768
    }
}
