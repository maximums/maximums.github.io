package com.cdodi.gltf

import com.cdodi.gltf.glb.Glb
import com.cdodi.gltf.read.ModelReader
import com.cdodi.gltf.schema.Document
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads glTF 2.0 models from `.glb` files.
 *
 * A deliberate subset: the `.glb` container with its images inside it, triangles, the core metallic-roughness material,
 * and `EXT_mesh_gpu_instancing`. A file that *requires* anything else is refused with the extensions named, rather
 * than drawn wrong; extensions it only *uses* (a clearcoat, say) are ignored. No animation, skins, morph targets,
 * cameras, sparse accessors, or compressed meshes and textures.
 */
object Gltf {

    /**
     * The extensions a file may require. `KHR_mesh_quantization` only allows integer vertex attributes, which the
     * accessors read anyway.
     */
    val SUPPORTED_EXTENSIONS = setOf("EXT_mesh_gpu_instancing", "KHR_mesh_quantization")

    private val json = Json { ignoreUnknownKeys = true }

    /** The model in [glb], a `.glb` file's bytes. Throws [GltfException], saying what's wrong and where. */
    fun read(glb: ByteArray): Model {
        val container = Glb.read(glb)
        val document = try {
            json.decodeFromString<Document>(container.json)
        } catch (e: SerializationException) {
            throw GltfException("The JSON isn't a glTF document: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw GltfException("The JSON isn't a glTF document: ${e.message}", e)
        }
        val major = document.asset.version.substringBefore('.')
        if (major != "2") throw GltfException("A glTF ${document.asset.version} file; only 2.x is read")
        val unsupported = document.extensionsRequired - SUPPORTED_EXTENSIONS
        if (unsupported.isNotEmpty()) throw GltfException("The file requires extensions this reader doesn't support: ${unsupported.joinToString()}")
        return ModelReader(document, container.bin).read()
    }
}
