package com.cdodi.gltf.read

import com.cdodi.gltf.GltfException
import com.cdodi.gltf.Model
import com.cdodi.gltf.schema.Document
import com.cdodi.gltf.schema.Primitive
import com.cdodi.gltf.schema.Material
import com.cdodi.gltf.schema.Sampler
import com.cdodi.math.Bounds
import com.cdodi.math.Mat4
import com.cdodi.math.Quat
import com.cdodi.math.Vec3

/** Builds a [Model] from a decoded [document] and the `.glb`'s binary chunk. */
internal class ModelReader(private val document: Document, private val bin: ByteArray?) {

    private val accessors = Accessors(document, bin)

    fun read(): Model {
        // Where each mesh is drawn: a matrix per node showing it, times each GPU instance's own transform.
        val instancesByMesh = LinkedHashMap<Int, MutableList<Mat4>>()
        for ((index, world) in worldMatrices(document)) {
            val node = document.nodes[index]
            val mesh = node.mesh ?: continue
            if (mesh !in document.meshes.indices) throw GltfException("Node $index's mesh $mesh doesn't exist")
            instancesByMesh.getOrPut(mesh) { ArrayList() } += instancesOf(index, world)
        }
        val meshes = instancesByMesh.map { (index, instances) ->
            val mesh = document.meshes[index]
            Model.Mesh(mesh.name, mesh.primitives.mapIndexed { p, primitive -> primitive(primitive, "mesh $index, primitive $p") }, instances)
        }
        val bounds = meshes.asSequence()
            .flatMap { mesh -> mesh.primitives.asSequence().mapNotNull { it.bounds }.flatMap { box -> mesh.instances.asSequence().map(box::transformed) } }
            .reduceOrNull(Bounds::union)
        return Model(meshes, document.materials.mapIndexed(::material), document.images.indices.map(::image), bounds)
    }

    /** Node [index]'s instances: its world matrix, or one per element of its `EXT_mesh_gpu_instancing` attributes. */
    private fun instancesOf(index: Int, world: Mat4): List<Mat4> {
        val attributes = document.nodes[index].extensions?.meshGpuInstancing?.attributes ?: return listOf(world)
        val use = "node $index's instances"
        val counts = attributes.values.map { accessors.count(it, use) }.distinct()
        if (counts.size != 1) throw GltfException("Node $index's instance attributes have different counts: $counts")
        val translations = attributes["TRANSLATION"]?.let { accessors.floats(it, "VEC3", "$use, TRANSLATION") }
        val rotations = attributes["ROTATION"]?.let { accessors.floats(it, "VEC4", "$use, ROTATION") }
        val scales = attributes["SCALE"]?.let { accessors.floats(it, "VEC3", "$use, SCALE") }
        return List(counts.single()) { i ->
            val t = translations?.let { Vec3(it[i * 3], it[i * 3 + 1], it[i * 3 + 2]) } ?: Vec3.ZERO
            val r = rotations?.let { Quat(it[i * 4], it[i * 4 + 1], it[i * 4 + 2], it[i * 4 + 3]).normalized() } ?: Quat.IDENTITY
            val s = scales?.let { Vec3(it[i * 3], it[i * 3 + 1], it[i * 3 + 2]) } ?: Vec3.ONE
            world * Mat4.trs(t, r, s)
        }
    }

    private fun primitive(primitive: Primitive, where: String): Model.Primitive {
        if (primitive.mode != Primitive.Mode.TRIANGLES) throw GltfException("$where draws mode ${primitive.mode}; only triangles (4) are read")
        val positionsAt = primitive.attributes["POSITION"] ?: throw GltfException("$where has no POSITION")
        val positions = accessors.floats(positionsAt, "VEC3", "$where, POSITION")
        val vertices = positions.size / 3
        fun attribute(name: String, type: String): FloatArray? = primitive.attributes[name]?.let { at ->
            accessors.floats(at, type, "$where, $name").also {
                val count = it.size / type.last().digitToInt()
                if (count != vertices) throw GltfException("$where's $name has $count elements, POSITION has $vertices")
            }
        }
        val material = primitive.material
        if (material != null && material !in document.materials.indices) throw GltfException("$where's material $material doesn't exist")
        val indices = primitive.indices?.let { accessors.indices(it, vertices, "$where, indices") }
        val count = indices?.size ?: vertices
        if (count % 3 != 0) throw GltfException("$where lists $count vertices, which isn't a whole number of triangles")
        val bounds = accessors.declaredRange(positionsAt)?.let { (min, max) -> Bounds(Vec3(min[0], min[1], min[2]), Vec3(max[0], max[1], max[2])) }
            ?: Bounds.around(positions)
        return Model.Primitive(
            positions = positions,
            normals = attribute("NORMAL", "VEC3"),
            tangents = attribute("TANGENT", "VEC4"),
            texCoords = attribute("TEXCOORD_0", "VEC2"),
            indices = indices,
            material = material,
            bounds = bounds,
        )
    }

    private fun material(index: Int, material: Material): Model.Material {
        val pbr = material.pbrMetallicRoughness
        val where = "material $index"
        if (pbr.baseColorFactor.size != 4) throw GltfException("The $where's baseColorFactor has ${pbr.baseColorFactor.size} values, not 4")
        if (material.emissiveFactor.size != 3) throw GltfException("The $where's emissiveFactor has ${material.emissiveFactor.size} values, not 3")
        return Model.Material(
            name = material.name,
            baseColorFactor = pbr.baseColorFactor.toFloatArray(),
            baseColorTexture = pbr.baseColorTexture?.let { texture(it.index, it.texCoord, where) },
            metallicFactor = pbr.metallicFactor,
            roughnessFactor = pbr.roughnessFactor,
            metallicRoughnessTexture = pbr.metallicRoughnessTexture?.let { texture(it.index, it.texCoord, where) },
            normalTexture = material.normalTexture?.let { texture(it.index, it.texCoord, where) },
            normalScale = material.normalTexture?.scale ?: 1f,
            occlusionTexture = material.occlusionTexture?.let { texture(it.index, it.texCoord, where) },
            occlusionStrength = material.occlusionTexture?.strength ?: 1f,
            emissiveFactor = material.emissiveFactor.let { Vec3(it[0], it[1], it[2]) },
            emissiveTexture = material.emissiveTexture?.let { texture(it.index, it.texCoord, where) },
            alphaMode = when (material.alphaMode) {
                "OPAQUE" -> Model.AlphaMode.Opaque
                "MASK" -> Model.AlphaMode.Mask
                "BLEND" -> Model.AlphaMode.Blend
                else -> throw GltfException("The $where's alphaMode ${material.alphaMode} is none of OPAQUE, MASK, BLEND")
            },
            alphaCutoff = material.alphaCutoff,
            doubleSided = material.doubleSided,
        )
    }

    private fun texture(index: Int, texCoord: Int, where: String): Model.TextureRef {
        val texture = document.textures.getOrNull(index) ?: throw GltfException("The $where's texture $index doesn't exist")
        val image = texture.source ?: throw GltfException("Texture $index has no source image; images given through extensions aren't read")
        if (image !in document.images.indices) throw GltfException("Texture $index's image $image doesn't exist")
        val sampler = texture.sampler?.let { document.samplers.getOrNull(it) ?: throw GltfException("Texture $index's sampler $it doesn't exist") }
        return Model.TextureRef(image, sampler?.let(::sampler) ?: Model.Sampler.DEFAULT, texCoord)
    }

    /** WebGL's filter and wrap numbers, as the renderer's terms. */
    private fun sampler(sampler: Sampler): Model.Sampler {
        fun wrap(mode: Int) = when (mode) {
            CLAMP_TO_EDGE -> Model.Wrap.ClampToEdge
            MIRRORED_REPEAT -> Model.Wrap.MirroredRepeat
            REPEAT -> Model.Wrap.Repeat
            else -> throw GltfException("Unknown wrap mode $mode")
        }
        val minify: Model.Filter
        val mipmaps: Model.Filter?
        when (sampler.minFilter) {
            null -> { minify = Model.Filter.Linear; mipmaps = Model.Filter.Linear }
            NEAREST -> { minify = Model.Filter.Nearest; mipmaps = null }
            LINEAR -> { minify = Model.Filter.Linear; mipmaps = null }
            NEAREST_MIPMAP_NEAREST -> { minify = Model.Filter.Nearest; mipmaps = Model.Filter.Nearest }
            LINEAR_MIPMAP_NEAREST -> { minify = Model.Filter.Linear; mipmaps = Model.Filter.Nearest }
            NEAREST_MIPMAP_LINEAR -> { minify = Model.Filter.Nearest; mipmaps = Model.Filter.Linear }
            LINEAR_MIPMAP_LINEAR -> { minify = Model.Filter.Linear; mipmaps = Model.Filter.Linear }
            else -> throw GltfException("Unknown minification filter ${sampler.minFilter}")
        }
        val magnify = when (sampler.magFilter) {
            null, LINEAR -> Model.Filter.Linear
            NEAREST -> Model.Filter.Nearest
            else -> throw GltfException("Unknown magnification filter ${sampler.magFilter}")
        }
        return Model.Sampler(magnify, minify, mipmaps, wrap(sampler.wrapS), wrap(sampler.wrapT))
    }

    private fun image(index: Int): Model.Image {
        val image = document.images[index]
        val viewIndex = image.bufferView ?: throw GltfException("Image $index is a separate file or a data URI; only images inside the .glb are read")
        val mimeType = image.mimeType ?: throw GltfException("Image $index has no mimeType")
        val view = document.bufferViews.getOrNull(viewIndex) ?: throw GltfException("Image $index's buffer view $viewIndex doesn't exist")
        val bytes = bin ?: throw GltfException("Image $index points into a binary chunk the file doesn't have")
        if (view.buffer != 0 || view.byteOffset < 0 || view.byteOffset + view.byteLength > bytes.size) {
            throw GltfException("Image $index's buffer view $viewIndex runs past the binary chunk")
        }
        return Model.Image(bytes.copyOfRange(view.byteOffset, view.byteOffset + view.byteLength), mimeType, image.name)
    }

    private companion object {
        const val NEAREST = 9728
        const val LINEAR = 9729
        const val NEAREST_MIPMAP_NEAREST = 9984
        const val LINEAR_MIPMAP_NEAREST = 9985
        const val NEAREST_MIPMAP_LINEAR = 9986
        const val LINEAR_MIPMAP_LINEAR = 9987
        const val CLAMP_TO_EDGE = 33071
        const val MIRRORED_REPEAT = 33648
        const val REPEAT = 10497
    }
}
