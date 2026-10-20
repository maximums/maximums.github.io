package com.cdodi.gltf.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The glTF 2.0 JSON document, as the spec names it (https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html).
 * Only what the reader uses is declared; everything else (extras, cameras, animations, extensions it doesn't use) is
 * skipped when decoding. Defaults are the spec's. Indices into the top-level arrays stay plain Ints: the reader checks
 * them when it follows them.
 */

@Serializable
class Document(
    val asset: Asset,
    val extensionsUsed: List<String> = emptyList(),
    val extensionsRequired: List<String> = emptyList(),
    val scene: Int? = null,
    val scenes: List<Scene> = emptyList(),
    val nodes: List<Node> = emptyList(),
    val meshes: List<Mesh> = emptyList(),
    val accessors: List<Accessor> = emptyList(),
    val bufferViews: List<BufferView> = emptyList(),
    val buffers: List<Buffer> = emptyList(),
    val materials: List<Material> = emptyList(),
    val textures: List<Texture> = emptyList(),
    val images: List<Image> = emptyList(),
    val samplers: List<Sampler> = emptyList(),
)

@Serializable
class Asset(val version: String, val minVersion: String? = null, val generator: String? = null, val copyright: String? = null)

@Serializable
class Scene(val nodes: List<Int> = emptyList(), val name: String? = null)

/** A node: a transform ([matrix], or [translation], [rotation] and [scale]), children, and maybe a mesh to draw. */
@Serializable
class Node(
    val name: String? = null,
    val children: List<Int> = emptyList(),
    val mesh: Int? = null,
    val matrix: List<Float>? = null,
    val translation: List<Float>? = null,
    val rotation: List<Float>? = null,
    val scale: List<Float>? = null,
    val extensions: NodeExtensions? = null,
)

@Serializable
class NodeExtensions(
    @SerialName("EXT_mesh_gpu_instancing") val meshGpuInstancing: MeshGpuInstancing? = null,
)

/**
 * `EXT_mesh_gpu_instancing`: the node's mesh drawn many times, once per instance. Each attribute (`TRANSLATION`,
 * `ROTATION`, `SCALE`) is an accessor with one element per instance, applied inside the node's own transform.
 */
@Serializable
class MeshGpuInstancing(val attributes: Map<String, Int> = emptyMap())

@Serializable
class Mesh(val primitives: List<Primitive>, val name: String? = null)

@Serializable
class Primitive(
    val attributes: Map<String, Int>,
    val indices: Int? = null,
    val material: Int? = null,
    val mode: Int = Mode.TRIANGLES,
) {
    object Mode {
        const val TRIANGLES = 4
    }
}

/** A typed view into a buffer view: [count] elements of [type] (`VEC3`…), each component a [componentType]. */
@Serializable
class Accessor(
    val bufferView: Int? = null,
    val byteOffset: Int = 0,
    val componentType: Int,
    val normalized: Boolean = false,
    val count: Int,
    val type: String,
    val min: List<Float>? = null,
    val max: List<Float>? = null,
    val sparse: Sparse? = null,
    val name: String? = null,
) {
    /** Sparse storage: only its presence matters, since the reader refuses it. */
    @Serializable
    class Sparse(val count: Int)

    object ComponentType {
        const val BYTE = 5120
        const val UNSIGNED_BYTE = 5121
        const val SHORT = 5122
        const val UNSIGNED_SHORT = 5123
        const val UNSIGNED_INT = 5125
        const val FLOAT = 5126
    }
}

@Serializable
class BufferView(val buffer: Int, val byteOffset: Int = 0, val byteLength: Int, val byteStride: Int? = null)

/** A buffer: in a `.glb`, the first one has no [uri] and is the binary chunk. */
@Serializable
class Buffer(val byteLength: Int, val uri: String? = null)

@Serializable
class Material(
    val name: String? = null,
    val pbrMetallicRoughness: PbrMetallicRoughness = PbrMetallicRoughness(),
    val normalTexture: NormalTextureInfo? = null,
    val occlusionTexture: OcclusionTextureInfo? = null,
    val emissiveTexture: TextureInfo? = null,
    val emissiveFactor: List<Float> = listOf(0f, 0f, 0f),
    val alphaMode: String = "OPAQUE",
    val alphaCutoff: Float = 0.5f,
    val doubleSided: Boolean = false,
)

@Serializable
class PbrMetallicRoughness(
    val baseColorFactor: List<Float> = listOf(1f, 1f, 1f, 1f),
    val baseColorTexture: TextureInfo? = null,
    val metallicFactor: Float = 1f,
    val roughnessFactor: Float = 1f,
    val metallicRoughnessTexture: TextureInfo? = null,
)

@Serializable
class TextureInfo(val index: Int, val texCoord: Int = 0)

@Serializable
class NormalTextureInfo(val index: Int, val texCoord: Int = 0, val scale: Float = 1f)

@Serializable
class OcclusionTextureInfo(val index: Int, val texCoord: Int = 0, val strength: Float = 1f)

@Serializable
class Texture(val sampler: Int? = null, val source: Int? = null)

/** An image: in a `.glb`, a buffer view holding a PNG or JPEG, with its [mimeType]. */
@Serializable
class Image(val bufferView: Int? = null, val mimeType: String? = null, val uri: String? = null, val name: String? = null)

/** Filters and wrapping as WebGL's numbers (`9729` is LINEAR); an absent filter is the renderer's choice. */
@Serializable
class Sampler(val magFilter: Int? = null, val minFilter: Int? = null, val wrapS: Int = 10497, val wrapT: Int = 10497)
