package com.cdodi.gltf

/**
 * Builds `.glb` files in memory: append the binary data first (each call returns where its block starts, 4-byte
 * aligned), then wrap it with the JSON that points into it.
 */
internal class TestGlb {

    private val bin = ArrayList<Byte>()

    val binLength: Int get() = bin.size

    fun floats(vararg values: Float): Int = block { values.forEach { int32(it.toRawBits()) } }

    fun int32s(vararg values: Int): Int = block { values.forEach { int32(it) } }

    fun int16s(vararg values: Int): Int = block { values.forEach { bin += (it and 0xFF).toByte(); bin += (it shr 8 and 0xFF).toByte() } }

    fun int8s(vararg values: Int): Int = block { values.forEach { bin += it.toByte() } }

    fun bytes(values: ByteArray): Int = block { values.forEach { bin += it } }

    /** The `.glb`: [json], the binary data, and any [extra] chunks (type, data) between them. */
    fun build(json: String, extra: List<Pair<String, ByteArray>> = emptyList()): ByteArray {
        val chunks = listOf("JSON" to json.encodeToByteArray()) + extra + if (bin.isEmpty()) emptyList() else listOf("BIN\u0000" to bin.toByteArray())
        return container(chunks)
    }

    private inline fun block(write: () -> Unit): Int {
        while (bin.size % 4 != 0) bin += 0
        val at = bin.size
        write()
        return at
    }

    private fun int32(value: Int) {
        for (shift in 0 until 32 step 8) bin += (value shr shift and 0xFF).toByte()
    }

    companion object {
        /** A container of [chunks] as they are, each padded to 4 bytes (the JSON with spaces, the rest with zeros). */
        fun container(chunks: List<Pair<String, ByteArray>>, version: Int = 2): ByteArray {
            val out = ArrayList<Byte>()
            fun int32(value: Int) {
                for (shift in 0 until 32 step 8) out += (value shr shift and 0xFF).toByte()
            }
            "glTF".encodeToByteArray().forEach { out += it }
            int32(version)
            int32(0) // the length, filled in below
            for ((type, data) in chunks) {
                val padding = (4 - data.size % 4) % 4
                int32(data.size + padding)
                type.encodeToByteArray().forEach { out += it }
                data.forEach { out += it }
                repeat(padding) { out += if (type == "JSON") 0x20 else 0 }
            }
            val bytes = out.toByteArray()
            val length = bytes.size
            for (i in 0 until 4) bytes[8 + i] = (length shr (8 * i) and 0xFF).toByte()
            return bytes
        }
    }
}
