package com.cdodi.gltf

import com.cdodi.math.Bounds
import com.cdodi.math.Mat4
import com.cdodi.math.Vec3

/**
 * A model as a renderer needs it, read from a `.glb` by [Gltf.read]: no JSON indices to follow, no byte layouts to
 * decode. Nothing in it is a GPU or browser object: the vertex arrays go to buffers as they are, and the [images] are
 * still encoded (PNG, JPEG), for the browser to decode.
 *
 * Every mesh lists where it's drawn: one [Mesh.instances] matrix per node that shows it, and per instance of
 * `EXT_mesh_gpu_instancing`. A mesh drawn 958 times is stored once, and drawn in one instanced call.
 */
class Model(val meshes: List<Mesh>, val materials: List<Material>, val images: List<Image>, val bounds: Bounds?) {

    class Mesh(val name: String?, val primitives: List<Primitive>, val instances: List<Mat4>)

    /**
     * Triangles, with their vertex attributes as flat arrays: [positions] and [normals] three floats a vertex,
     * [tangents] four (the fourth is the bitangent's sign), [texCoords] two. Integer attributes (quantized files) are
     * already converted. [indices] is null when the triangles are listed in vertex order. [material] indexes
     * [Model.materials]; null means the spec's default material ([Material.DEFAULT]).
     */
    class Primitive(
        val positions: FloatArray,
        val normals: FloatArray?,
        val tangents: FloatArray?,
        val texCoords: FloatArray?,
        val indices: IntArray?,
        val material: Int?,
        val bounds: Bounds?,
    ) {
        val vertexCount: Int get() = positions.size / 3
        val indexCount: Int get() = indices?.size ?: vertexCount
    }

    /** glTF's metallic-roughness material. Colour factors are linear; the base colour texture is sRGB. */
    class Material(
        val name: String?,
        val baseColorFactor: FloatArray,
        val baseColorTexture: TextureRef?,
        val metallicFactor: Float,
        val roughnessFactor: Float,
        val metallicRoughnessTexture: TextureRef?,
        val normalTexture: TextureRef?,
        val normalScale: Float,
        val occlusionTexture: TextureRef?,
        val occlusionStrength: Float,
        val emissiveFactor: Vec3,
        val emissiveTexture: TextureRef?,
        val alphaMode: AlphaMode,
        val alphaCutoff: Float,
        val doubleSided: Boolean,
    ) {
        companion object {
            /** The spec's material for primitives that name none: white, fully rough and fully metallic, one-sided. */
            val DEFAULT = Material(
                name = null, baseColorFactor = floatArrayOf(1f, 1f, 1f, 1f), baseColorTexture = null, metallicFactor = 1f, roughnessFactor = 1f,
                metallicRoughnessTexture = null, normalTexture = null, normalScale = 1f, occlusionTexture = null, occlusionStrength = 1f,
                emissiveFactor = Vec3.ZERO, emissiveTexture = null, alphaMode = AlphaMode.Opaque, alphaCutoff = 0.5f, doubleSided = false,
            )
        }
    }

    enum class AlphaMode { Opaque, Mask, Blend }

    /** A texture as a material uses it: which of [Model.images], sampled how, with which set of texture coordinates. */
    class TextureRef(val image: Int, val sampler: Sampler, val texCoord: Int)

    /** How a texture is sampled. [mipmaps] is the filter between mip levels, or null for a texture sampled without them. */
    data class Sampler(val magnify: Filter, val minify: Filter, val mipmaps: Filter?, val wrapS: Wrap, val wrapT: Wrap) {
        companion object {
            /** For textures that name no sampler, or no filters: smooth, with mipmaps, repeating (the spec's wrap). */
            val DEFAULT = Sampler(Filter.Linear, Filter.Linear, Filter.Linear, Wrap.Repeat, Wrap.Repeat)
        }
    }

    enum class Filter { Nearest, Linear }

    enum class Wrap { Repeat, MirroredRepeat, ClampToEdge }

    /** An encoded image and its MIME type (`image/png`, `image/jpeg`). */
    class Image(val bytes: ByteArray, val mimeType: String, val name: String?)
}
