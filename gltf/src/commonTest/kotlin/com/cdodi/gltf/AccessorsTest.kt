package com.cdodi.gltf

import com.cdodi.gltf.glb.Glb
import com.cdodi.gltf.read.Accessors
import com.cdodi.gltf.schema.Document
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccessorsTest {

    /** Accessors over [build]'s binary data, with the given buffer views and accessors (JSON arrays' contents). */
    private fun accessors(build: TestGlb, bufferViews: String, accessors: String): Accessors {
        val json = """{"asset":{"version":"2.0"},"buffers":[{"byteLength":${build.binLength}}],"bufferViews":[$bufferViews],"accessors":[$accessors]}"""
        val glb = Glb.read(build.build(json))
        return Accessors(Json { ignoreUnknownKeys = true }.decodeFromString<Document>(glb.json), glb.bin)
    }

    @Test
    fun interleavedAttributesAreReadAStrideApart() {
        val glb = TestGlb()
        // Two vertices, each a position then a normal: 24 bytes apart.
        val at = glb.floats(1f, 2f, 3f, 0f, 1f, 0f, 4f, 5f, 6f, 0f, 0f, 1f)
        val read = accessors(
            glb,
            """{"buffer":0,"byteOffset":$at,"byteLength":48,"byteStride":24}""",
            """{"bufferView":0,"componentType":5126,"count":2,"type":"VEC3"},{"bufferView":0,"byteOffset":12,"componentType":5126,"count":2,"type":"VEC3"}""",
        )
        assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), read.floats(0, "VEC3", "positions"))
        assertContentEquals(floatArrayOf(0f, 1f, 0f, 0f, 0f, 1f), read.floats(1, "VEC3", "normals"))
    }

    @Test
    fun integersAreConvertedTheSpecsWay() {
        val glb = TestGlb()
        val bytes = glb.int8s(0, 255, 51, 0)
        val signedBytes = glb.int8s(127, -127, -128, 0)
        val shorts = glb.int16s(0, 65535)
        val signedShorts = glb.int16s(32767, -32768)
        val read = accessors(
            glb,
            """{"buffer":0,"byteOffset":$bytes,"byteLength":4},{"buffer":0,"byteOffset":$signedBytes,"byteLength":4},
               {"buffer":0,"byteOffset":$shorts,"byteLength":4},{"buffer":0,"byteOffset":$signedShorts,"byteLength":4}""",
            """{"bufferView":0,"componentType":5121,"normalized":true,"count":1,"type":"VEC3"},
               {"bufferView":1,"componentType":5120,"normalized":true,"count":1,"type":"VEC3"},
               {"bufferView":2,"componentType":5123,"normalized":true,"count":1,"type":"VEC2"},
               {"bufferView":3,"componentType":5122,"normalized":true,"count":1,"type":"VEC2"},
               {"bufferView":3,"componentType":5122,"count":1,"type":"VEC2"}""",
        )
        assertContentEquals(floatArrayOf(0f, 1f, 0.2f), read.floats(0, "VEC3", "unsigned bytes"))
        assertContentEquals(floatArrayOf(1f, -1f, -1f), read.floats(1, "VEC3", "signed bytes"), "−128 clamps to −1")
        assertContentEquals(floatArrayOf(0f, 1f), read.floats(2, "VEC2", "unsigned shorts"))
        assertContentEquals(floatArrayOf(1f, -1f), read.floats(3, "VEC2", "signed shorts"))
        assertContentEquals(floatArrayOf(32767f, -32768f), read.floats(4, "VEC2", "quantized"), "not normalized: the values themselves")
    }

    @Test
    fun anAccessorWithoutABufferViewIsZeros() {
        val read = accessors(TestGlb(), "", """{"componentType":5126,"count":2,"type":"VEC2"}""")
        assertContentEquals(FloatArray(4), read.floats(0, "VEC2", "zeros"))
    }

    @Test
    fun indicesOfEveryWidthNameRealVertices() {
        val glb = TestGlb()
        val bytes = glb.int8s(0, 1, 2)
        val shorts = glb.int16s(2, 1, 0)
        val ints = glb.int32s(1, 2, 0)
        val read = accessors(
            glb,
            """{"buffer":0,"byteOffset":$bytes,"byteLength":3},{"buffer":0,"byteOffset":$shorts,"byteLength":6},{"buffer":0,"byteOffset":$ints,"byteLength":12}""",
            """{"bufferView":0,"componentType":5121,"count":3,"type":"SCALAR"},{"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"},
               {"bufferView":2,"componentType":5125,"count":3,"type":"SCALAR"},{"bufferView":2,"componentType":5126,"count":3,"type":"SCALAR"}""",
        )
        assertContentEquals(intArrayOf(0, 1, 2), read.indices(0, 3, "bytes"))
        assertContentEquals(intArrayOf(2, 1, 0), read.indices(1, 3, "shorts"))
        assertContentEquals(intArrayOf(1, 2, 0), read.indices(2, 3, "ints"))
        assertRefused("names vertex 2, of 2") { read.indices(0, 2, "too few vertices") }
        assertRefused("indices are unsigned") { read.indices(3, 3, "floats") }
    }

    @Test
    fun brokenAccessorsAreRefusedBeforeAByteIsRead() {
        val glb = TestGlb()
        val at = glb.floats(1f, 2f, 3f)
        val read = accessors(
            glb,
            """{"buffer":0,"byteOffset":$at,"byteLength":12},{"buffer":0,"byteOffset":$at,"byteLength":400},{"buffer":1,"byteLength":12}""",
            """{"bufferView":0,"componentType":5126,"count":2,"type":"VEC3"},{"bufferView":0,"componentType":5126,"count":1,"type":"VEC3","sparse":{"count":1}},
               {"bufferView":0,"componentType":5126,"count":1,"type":"VEC2"},{"bufferView":1,"componentType":5126,"count":1,"type":"VEC3"},
               {"bufferView":2,"componentType":5126,"count":1,"type":"VEC3"},{"bufferView":0,"componentType":5126,"count":1,"type":"MAT3"}""",
        )
        assertRefused("runs past buffer view 0") { read.floats(0, "VEC3", "too many") }
        assertRefused("sparse") { read.floats(1, "VEC3", "sparse") }
        assertRefused("is VEC2, expected VEC3") { read.floats(2, "VEC3", "wrong shape") }
        assertRefused("runs past the binary chunk") { read.floats(3, "VEC3", "view too long") }
        assertRefused("doesn't exist") { read.floats(4, "VEC3", "missing buffer") }
        assertRefused("Accessor 9 (absent) doesn't exist") { read.floats(9, "VEC3", "absent") }
    }

    private fun assertRefused(reason: String, block: () -> Unit) {
        val e = assertFailsWith<GltfException> { block() }
        assertTrue(reason in e.message!!, "expected \"$reason\" in \"${e.message}\"")
    }
}
