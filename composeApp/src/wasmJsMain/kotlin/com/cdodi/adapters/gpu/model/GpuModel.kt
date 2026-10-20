package com.cdodi.adapters.gpu.model

import com.cdodi.gltf.Model
import com.cdodi.math.Bounds
import com.cdodi.webgpu.bindings.GPUAddressMode
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUFilterMode
import com.cdodi.webgpu.bindings.GPUMipmapFilterMode
import com.cdodi.webgpu.bindings.GPUSampler
import com.cdodi.webgpu.bindings.GPUSamplerDescriptor
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.bindings.GPUTextureViewDescriptor
import com.cdodi.webgpu.bindings.clampToEdge
import com.cdodi.webgpu.bindings.linear
import com.cdodi.webgpu.bindings.mirrorRepeat
import com.cdodi.webgpu.bindings.nearest
import com.cdodi.webgpu.bindings.repeat
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.Float32Staging
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.Uint32Staging
import com.cdodi.webgpu.resource.resourceScope

/**
 * A [Model] on the GPU. Each primitive's vertex attributes are vertex buffers and its indices an index buffer; each
 * mesh's instance matrices are a storage buffer, one `mat4x4f` per instance, read by `instance_index`; each texture is
 * decoded by the browser, uploaded and mipmapped. It all belongs to one resource scope, destroyed by [close].
 *
 * Colour textures (base colour, emissive) are stored as sRGB, so shaders sample light rather than stored numbers; data
 * textures (normals, metallic-roughness, occlusion) as they are. An image used both ways is uploaded both ways.
 */
class GpuModel private constructor(
    val meshes: List<Mesh>,
    val materials: List<Model.Material>,
    val bounds: Bounds?,
    private val views: Map<Pair<Int, Boolean>, GPUTextureView>,
    private val samplers: Map<Model.Sampler, GPUSampler>,
    private val resources: ResourceScope,
) : AutoCloseable {

    class Mesh(val primitives: List<Primitive>, val instances: GPUBuffer, val instanceCount: Int)

    /**
     * One primitive's buffers: [positions] and [normals] are `float32x3`, [tangents] `float32x4`, [texCoords]
     * `float32x2`; null where the model has none. [indices] are `uint32`, or null to draw [count] vertices in order.
     */
    class Primitive(
        val positions: GPUBuffer,
        val normals: GPUBuffer?,
        val tangents: GPUBuffer?,
        val texCoords: GPUBuffer?,
        val indices: GPUBuffer?,
        val count: Int,
        val material: Int?,
    )

    /** The texture [ref] names, as uploaded for its use: [colour] for a base colour or emissive texture. */
    fun texture(ref: Model.TextureRef, colour: Boolean): GPUTextureView = views.getValue(ref.image to colour)

    /** The sampler [ref] names. */
    fun sampler(ref: Model.TextureRef): GPUSampler = samplers.getValue(ref.sampler)

    override fun close() = resources.close()

    companion object {
        /** Uploads [model]: its buffers at once, its images as the browser decodes them (so this suspends). */
        suspend fun upload(gpu: GpuContext, model: Model, mipmaps: Mipmaps, label: String): GpuModel {
            val resources = gpu.resourceScope()
            try {
                val meshes = model.meshes.mapIndexed { m, mesh ->
                    val primitives = mesh.primitives.mapIndexed { p, primitive ->
                        val name = "$label mesh $m primitive $p"
                        Primitive(
                            positions = vertices(gpu, resources, primitive.positions, "$name positions"),
                            normals = primitive.normals?.let { vertices(gpu, resources, it, "$name normals") },
                            tangents = primitive.tangents?.let { vertices(gpu, resources, it, "$name tangents") },
                            texCoords = primitive.texCoords?.let { vertices(gpu, resources, it, "$name texture coordinates") },
                            indices = primitive.indices?.let { indices(gpu, resources, it, "$name indices") },
                            count = primitive.indexCount,
                            material = primitive.material,
                        )
                    }
                    val matrices = FloatArray(mesh.instances.size * 16).also { out -> mesh.instances.forEachIndexed { i, matrix -> matrix.values.copyInto(out, i * 16) } }
                    val instances = resources.buffer(GPUBufferDescriptor(size = matrices.size * 4.0, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_DST, label = "$label mesh $m instances"))
                    gpu.queue.writeBuffer(instances, 0.0, Float32Staging(matrices.size).fill(matrices))
                    Mesh(primitives, instances, mesh.instances.size)
                }

                // Each image once per way it's used: decoded as colour, as data, or both.
                val uses = model.materials.flatMap { material ->
                    listOfNotNull(material.baseColorTexture, material.emissiveTexture).map { it to true } +
                        listOfNotNull(material.normalTexture, material.metallicRoughnessTexture, material.occlusionTexture).map { it to false }
                }
                val views = uses.map { (ref, colour) -> ref.image to colour }.distinct().associateWith { (image, colour) ->
                    val texture = uploadImage(gpu, mipmaps, model.images[image], colour, "$label image $image${if (colour) " (sRGB)" else ""}")
                    resources.own(texture::destroy)
                    texture.createView(GPUTextureViewDescriptor())
                }
                val samplers = uses.map { it.first.sampler }.distinct().associateWith { sampler(gpu, it) }
                return GpuModel(meshes, model.materials, model.bounds, views, samplers, resources)
            } catch (e: Throwable) {
                resources.close()
                throw e
            }
        }

        private fun vertices(gpu: GpuContext, resources: ResourceScope, values: FloatArray, label: String): GPUBuffer {
            val buffer = resources.buffer(GPUBufferDescriptor(size = values.size * 4.0, usage = GPUBufferUsage.VERTEX or GPUBufferUsage.COPY_DST, label = label))
            gpu.queue.writeBuffer(buffer, 0.0, Float32Staging(values.size).fill(values))
            return buffer
        }

        private fun indices(gpu: GpuContext, resources: ResourceScope, values: IntArray, label: String): GPUBuffer {
            val buffer = resources.buffer(GPUBufferDescriptor(size = values.size * 4.0, usage = GPUBufferUsage.INDEX or GPUBufferUsage.COPY_DST, label = label))
            gpu.queue.writeBuffer(buffer, 0.0, Uint32Staging(values.size).fill(values))
            return buffer
        }

        private fun sampler(gpu: GpuContext, sampler: Model.Sampler): GPUSampler {
            fun filter(f: Model.Filter) = if (f == Model.Filter.Nearest) GPUFilterMode.nearest else GPUFilterMode.linear
            fun wrap(w: Model.Wrap) = when (w) {
                Model.Wrap.Repeat -> GPUAddressMode.repeat
                Model.Wrap.MirroredRepeat -> GPUAddressMode.mirrorRepeat
                Model.Wrap.ClampToEdge -> GPUAddressMode.clampToEdge
            }
            return gpu.device.createSampler(
                GPUSamplerDescriptor(
                    addressModeU = wrap(sampler.wrapS),
                    addressModeV = wrap(sampler.wrapT),
                    magFilter = filter(sampler.magnify),
                    minFilter = filter(sampler.minify),
                    mipmapFilter = if (sampler.mipmaps == Model.Filter.Nearest) GPUMipmapFilterMode.nearest else GPUMipmapFilterMode.linear,
                    // Without mipmaps in the sampler, stay on level 0.
                    lodMaxClamp = if (sampler.mipmaps == null) 0f else null,
                )
            )
        }
    }
}
