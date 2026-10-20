package com.cdodi.gltf

import com.cdodi.gltf.glb.Glb
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlbTest {

    @Test
    fun theContainerSplitsIntoJsonAndBinary() {
        val glb = TestGlb().apply { int8s(1, 2, 3, 4, 5) }.build("""{"asset":{"version":"2.0"}}""")
        val read = Glb.read(glb)
        assertEquals("""{"asset":{"version":"2.0"}}""", read.json.trimEnd(), "padded with spaces")
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 0, 0, 0), read.bin, "padded with zeros")
    }

    @Test
    fun theBinaryChunkIsOptional() {
        assertNull(Glb.read(TestGlb().build("{}")).bin)
    }

    @Test
    fun chunksOfOtherTypesAreSkipped() {
        val glb = TestGlb().apply { int8s(9, 9, 9, 9) }.build("{}", extra = listOf("XTRA" to byteArrayOf(7, 7, 7, 7)))
        assertContentEquals(byteArrayOf(9, 9, 9, 9), Glb.read(glb).bin)
    }

    @Test
    fun brokenContainersAreRefusedWithAReason() {
        val good = TestGlb().build("{}")
        fun refused(bytes: ByteArray, reason: String) {
            val e = assertFailsWith<GltfException> { Glb.read(bytes) }
            assertTrue(reason in e.message!!, "expected \"$reason\" in \"${e.message}\"")
        }
        refused(good.copyOf().also { it[0] = 'x'.code.toByte() }, "doesn't start with \"glTF\"")
        refused(TestGlb.container(listOf("JSON" to "{}".encodeToByteArray()), version = 1), "version 1")
        refused(good + byteArrayOf(0, 0, 0, 0), "the file has")
        refused(TestGlb.container(listOf("BIN\u0000" to ByteArray(4), "JSON" to "{}".encodeToByteArray())), "first chunk must be the JSON")
        refused(good.copyOf().also { it[12] = 100 }, "runs past the end")
        refused(ByteArray(8), "Too short")
    }
}
