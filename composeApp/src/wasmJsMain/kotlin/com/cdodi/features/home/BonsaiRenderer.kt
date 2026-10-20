package com.cdodi.features.home

import blog.composeapp.generated.resources.Res
import com.cdodi.adapters.gpu.GpuProfiler
import com.cdodi.adapters.gpu.GpuTheme
import com.cdodi.adapters.gpu.GpuWidgetRenderer
import com.cdodi.adapters.gpu.GpuWidgets
import com.cdodi.adapters.gpu.WidgetFrame
import com.cdodi.adapters.gpu.model.GpuModel
import com.cdodi.adapters.gpu.model.Mipmaps
import com.cdodi.adapters.gpu.model.ModelTarget
import com.cdodi.gltf.Gltf
import com.cdodi.math.Bounds
import com.cdodi.math.Vec3
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
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUCompareFunction
import com.cdodi.webgpu.bindings.GPUCullMode
import com.cdodi.webgpu.bindings.GPUDepthStencilState
import com.cdodi.webgpu.bindings.GPUFragmentState
import com.cdodi.webgpu.bindings.GPUIndexFormat
import com.cdodi.webgpu.bindings.GPUMultisampleState
import com.cdodi.webgpu.bindings.GPUPrimitiveState
import com.cdodi.webgpu.bindings.GPURenderPassDescriptor
import com.cdodi.webgpu.bindings.GPURenderPassEncoder
import com.cdodi.webgpu.bindings.GPURenderPipeline
import com.cdodi.webgpu.bindings.GPURenderPipelineDescriptor
import com.cdodi.webgpu.bindings.GPUShaderModule
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUVertexAttribute
import com.cdodi.webgpu.bindings.GPUVertexBufferLayout
import com.cdodi.webgpu.bindings.GPUVertexFormat
import com.cdodi.webgpu.bindings.GPUVertexState
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.float32x2
import com.cdodi.webgpu.bindings.float32x3
import com.cdodi.webgpu.bindings.float32x4
import com.cdodi.webgpu.bindings.less
import com.cdodi.webgpu.bindings.none
import com.cdodi.webgpu.bindings.one
import com.cdodi.webgpu.bindings.oneMinusSrcAlpha
import com.cdodi.webgpu.bindings.uint32
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.Float32Staging
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.shader.compileShader
import kotlin.math.roundToInt

/**
 * The maple on Home (`files/models/maple-bonsai.glb`, read by `:gltf`), a GPU-backed widget. Each frame it's drawn in
 * 3D into a target the size of its widget (`home/bonsai.wgsl`'s model pass: the theme's lamp on its edges, the mist
 * towards its back), and that target is laid over the street in the background's final pass, faded with its page.
 * The trunk and the pot are one draw each; the 958 leaves are one instanced draw.
 *
 * It loads after the street is up. Until then, or if it can't load, there's simply no tree.
 */
class BonsaiRenderer(private val theme: GpuTheme, private val profiler: GpuProfiler? = null) : GpuWidgetRenderer {

    override val kind = GpuWidgets.Kind.Bonsai

    /** Which way round the tree is seen, radians from its front: the orbit's (step 4); for now, the front. */
    var yaw = 0f

    private lateinit var gpu: GpuContext
    private lateinit var resources: ResourceScope
    private lateinit var model: GpuModel
    private lateinit var bounds: Bounds
    private lateinit var target: ModelTarget
    private lateinit var modelPipeline: GPURenderPipeline
    private lateinit var compositePipeline: GPURenderPipeline
    private lateinit var cameraBuffer: GPUBuffer
    private lateinit var placementBuffer: GPUBuffer
    private lateinit var themeGroup: GPUBindGroup
    private lateinit var meshGroups: List<GPUBindGroup>
    private lateinit var materialGroups: List<GPUBindGroup>
    private var compositeGroup: GPUBindGroup? = null

    // struct Camera { viewProjection: mat4x4f, view: mat4x4f, eye: vec4f, lamp: vec4f, depth: vec4f }
    private val cameraValues = FloatArray(44)
    private val cameraStaging = Float32Staging(cameraValues.size)
    // struct Placement { rect: vec4f, frame: vec4f }
    private val placementValues = FloatArray(8)
    private val placementStaging = Float32Staging(placementValues.size)

    override suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) {
        this.gpu = gpu
        resources = gpu.resourceScope()
        val read = Gltf.read(Res.readBytes(MODEL))
        val missing = read.meshes.flatMap { it.primitives }.any { it.normals == null || it.tangents == null || it.texCoords == null }
        check(!missing) { "$MODEL: every primitive needs normals, tangents and texture coordinates" }
        val materials = read.materials.ifEmpty { error("$MODEL has no material") }
        check(materials.all { it.baseColorTexture != null && it.normalTexture != null }) { "$MODEL: its materials need a base colour and a normal texture" }
        bounds = read.bounds ?: error("$MODEL draws nothing")
        model = GpuModel.upload(gpu, read, Mipmaps.create(gpu), "maple").also { model -> resources.own(model::close) }
        target = ModelTarget(gpu, format).also { target -> resources.own(target::close) }

        val module = gpu.compileShader(theme.shader(Res.readBytes(SHADER).decodeToString()), label = "maple")
        modelPipeline = modelPipeline(module, format)
        compositePipeline = compositePipeline(module, format)
        cameraBuffer = resources.buffer(GPUBufferDescriptor(size = cameraValues.size * 4.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "maple camera"))
        placementBuffer = resources.buffer(GPUBufferDescriptor(size = placementValues.size * 4.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "maple placement"))
        themeGroup = theme.bindGroup(modelPipeline.getBindGroupLayout(GpuTheme.GROUP))
        meshGroups = model.meshes.map { mesh ->
            gpu.device.createBindGroup(
                GPUBindGroupDescriptor(
                    layout = modelPipeline.getBindGroupLayout(0),
                    entries = listOf(GPUBindGroupEntry(0, GPUBufferBinding(cameraBuffer)), GPUBindGroupEntry(1, GPUBufferBinding(mesh.instances))),
                )
            )
        }
        materialGroups = model.materials.map { material ->
            val base = material.baseColorTexture!!
            gpu.device.createBindGroup(
                GPUBindGroupDescriptor(
                    layout = modelPipeline.getBindGroupLayout(MATERIAL_GROUP),
                    entries = listOf(
                        GPUBindGroupEntry(0, model.texture(base, colour = true)),
                        GPUBindGroupEntry(1, model.texture(material.normalTexture!!, colour = false)),
                        GPUBindGroupEntry(2, model.sampler(base)),
                    ),
                )
            )
        }
    }

    override fun encodeOffscreen(encoder: GPUCommandEncoder, widgets: List<GpuWidgets.Widget>, frame: WidgetFrame) {
        val widget = widgets.first()
        val width = widget.width.roundToInt().coerceIn(1, MAX_SIZE)
        val height = widget.height.roundToInt().coerceIn(1, MAX_SIZE)
        if (target.fit(width, height)) compositeGroup = null

        val view = BonsaiView.of(bounds, width.toFloat() / height, yaw)
        view.viewProjection.values.copyInto(cameraValues, 0)
        view.view.values.copyInto(cameraValues, 16)
        cameraValues[32] = view.eye.x
        cameraValues[33] = view.eye.y
        cameraValues[34] = view.eye.z
        cameraValues[36] = LAMP.x
        cameraValues[37] = LAMP.y
        cameraValues[38] = LAMP.z
        cameraValues[40] = view.nearest
        cameraValues[41] = view.farthest
        cameraValues[42] = bounds.min.y
        cameraValues[43] = bounds.max.y
        gpu.queue.writeBuffer(cameraBuffer, 0.0, cameraStaging.fill(cameraValues))

        encoder.beginRenderPass(
            GPURenderPassDescriptor(
                colorAttachments = listOf(target.colorAttachment()),
                depthStencilAttachment = target.depthAttachment(),
                timestampWrites = profiler?.span("maple")?.render(),
                label = "maple",
            )
        ).apply {
            setPipeline(modelPipeline)
            setBindGroup(GpuTheme.GROUP, themeGroup)
            model.meshes.forEachIndexed { m, mesh ->
                setBindGroup(0, meshGroups[m])
                for (primitive in mesh.primitives) {
                    setBindGroup(MATERIAL_GROUP, materialGroups[primitive.material ?: 0])
                    setVertexBuffer(0, primitive.positions)
                    setVertexBuffer(1, primitive.normals)
                    setVertexBuffer(2, primitive.tangents)
                    setVertexBuffer(3, primitive.texCoords)
                    val indices = primitive.indices
                    if (indices != null) {
                        setIndexBuffer(indices, GPUIndexFormat.uint32)
                        drawIndexed(primitive.count, mesh.instanceCount)
                    } else {
                        draw(primitive.count, mesh.instanceCount)
                    }
                }
            }
            end()
        }
    }

    override fun draw(pass: GPURenderPassEncoder, widgets: List<GpuWidgets.Widget>, frame: WidgetFrame) {
        val widget = widgets.first()
        placementValues[0] = widget.left
        placementValues[1] = widget.top
        placementValues[2] = target.width.toFloat()
        placementValues[3] = target.height.toFloat()
        placementValues[4] = frame.width.toFloat()
        placementValues[5] = frame.height.toFloat()
        placementValues[6] = widget.visibility()
        gpu.queue.writeBuffer(placementBuffer, 0.0, placementStaging.fill(placementValues))
        val group = compositeGroup ?: gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = compositePipeline.getBindGroupLayout(0),
                entries = listOf(GPUBindGroupEntry(2, GPUBufferBinding(placementBuffer)), GPUBindGroupEntry(3, target.resolved)),
            )
        ).also { compositeGroup = it }
        pass.setPipeline(compositePipeline)
        pass.setBindGroup(0, group)
        pass.draw(6)
    }

    override fun close() {
        if (::resources.isInitialized) resources.close()
    }

    /** Lit, multisampled and depth-tested; nothing culled, since leaves are seen from both sides. */
    private fun modelPipeline(module: GPUShaderModule, format: GPUTextureFormat): GPURenderPipeline {
        fun buffer(location: Int, vertexFormat: GPUVertexFormat, floats: Int) =
            GPUVertexBufferLayout(arrayStride = floats * 4.0, attributes = listOf(GPUVertexAttribute(format = vertexFormat, offset = 0.0, shaderLocation = location)))
        return gpu.device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                layout = GPUAutoLayoutMode.auto,
                vertex = GPUVertexState(
                    module = module,
                    entryPoint = "vsModel",
                    buffers = listOf(
                        buffer(0, GPUVertexFormat.float32x3, 3),
                        buffer(1, GPUVertexFormat.float32x3, 3),
                        buffer(2, GPUVertexFormat.float32x4, 4),
                        buffer(3, GPUVertexFormat.float32x2, 2),
                    ),
                ),
                fragment = GPUFragmentState(module = module, entryPoint = "fsModel", targets = listOf(GPUColorTargetState(format = format))),
                primitive = GPUPrimitiveState(cullMode = GPUCullMode.none),
                depthStencil = GPUDepthStencilState(format = ModelTarget.DEPTH_FORMAT, depthWriteEnabled = true, depthCompare = GPUCompareFunction.less),
                multisample = GPUMultisampleState(count = ModelTarget.SAMPLES),
                label = "maple",
            )
        )
    }

    /** The drawn target over the street: premultiplied (the target holds colour times coverage). */
    private fun compositePipeline(module: GPUShaderModule, format: GPUTextureFormat): GPURenderPipeline {
        val premultiplied = GPUBlendComponent(srcFactor = GPUBlendFactor.one, dstFactor = GPUBlendFactor.oneMinusSrcAlpha)
        return gpu.device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                layout = GPUAutoLayoutMode.auto,
                vertex = GPUVertexState(module = module, entryPoint = "vsComposite"),
                fragment = GPUFragmentState(
                    module = module,
                    entryPoint = "fsComposite",
                    targets = listOf(GPUColorTargetState(format = format, blend = GPUBlendState(color = premultiplied, alpha = premultiplied))),
                ),
                label = "maple composite",
            )
        )
    }

    private companion object {
        const val MODEL = "files/models/maple-bonsai.glb"
        const val SHADER = "files/shaders/home/bonsai.wgsl"
        const val MATERIAL_GROUP = 2

        /** The largest target the tree is drawn into, a side: a widget bigger than that is drawn at most this sharp. */
        const val MAX_SIZE = 2048

        /** Towards the street's lamp, in the tree's space: to the right, above, and in front. */
        val LAMP = Vec3(0.6f, 0.55f, 0.5f).normalized()
    }
}
