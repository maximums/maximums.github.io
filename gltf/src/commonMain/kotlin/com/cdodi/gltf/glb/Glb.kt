package com.cdodi.gltf.glb

import com.cdodi.gltf.GltfException

/**
 * A `.glb` file taken apart: the glTF document's [json] and the binary chunk ([bin]) its buffers point into.
 *
 * The container (the spec's "GLB File Format Specification"): a 12-byte header (`glTF`, version 2, total length), then
 * chunks, each a length, a type and the data, padded to 4 bytes. The first chunk is the JSON; a `BIN` chunk may follow;
 * chunks of other types are skipped, as the spec asks. Everything is little-endian.
 */
class Glb(val json: String, val bin: ByteArray?) {

    companion object {
        private const val MAGIC = 0x46546C67 // "glTF"
        private const val JSON = 0x4E4F534A // "JSON"
        private const val BIN = 0x004E4942 // "BIN\0"

        fun read(bytes: ByteArray): Glb {
            if (bytes.size < 20) throw GltfException("Too short for a .glb: ${bytes.size} bytes")
            if (bytes.int32(0) != MAGIC) throw GltfException("Not a .glb: it doesn't start with \"glTF\"")
            val version = bytes.int32(4)
            if (version != 2) throw GltfException("A version $version .glb; only version 2 is read")
            val length = bytes.int32(8)
            if (length != bytes.size) throw GltfException("The header says $length bytes, the file has ${bytes.size}")

            var json: String? = null
            var bin: ByteArray? = null
            var at = 12
            while (at < length) {
                if (at + 8 > length) throw GltfException("A chunk header runs past the end, at byte $at")
                val chunkLength = bytes.int32(at)
                val type = bytes.int32(at + 4)
                val start = at + 8
                if (chunkLength < 0 || start + chunkLength > length) throw GltfException("A chunk of $chunkLength bytes runs past the end, at byte $at")
                when {
                    at == 12 && type != JSON -> throw GltfException("The first chunk must be the JSON")
                    type == JSON -> json = bytes.decodeToString(start, start + chunkLength)
                    type == BIN && bin == null -> bin = bytes.copyOfRange(start, start + chunkLength)
                }
                at = start + chunkLength
            }
            return Glb(json ?: throw GltfException("No JSON chunk"), bin)
        }
    }
}

/** The little-endian 32-bit integer at [at]. */
internal fun ByteArray.int32(at: Int): Int =
    (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8) or ((this[at + 2].toInt() and 0xFF) shl 16) or (this[at + 3].toInt() shl 24)

/** The little-endian unsigned 16-bit integer at [at]. */
internal fun ByteArray.uint16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

/** The little-endian signed 16-bit integer at [at]. */
internal fun ByteArray.int16(at: Int): Int = uint16(at).toShort().toInt()

/** The little-endian 32-bit float at [at]. */
internal fun ByteArray.float32(at: Int): Float = Float.fromBits(int32(at))
