package com.cdodi.webgpu

import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.resource.Float32Staging
import org.khronos.webgl.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun keysOf(obj: JsAny): String = js("Object.keys(obj).sort().join(',')")

/** WebGPU's generated dictionaries on the shared runtime, and the staging arrays for uploads. (The runtime's own tests are in :webidl-runtime.) */
class BindingsTest {

    @Test
    fun dictionaryFactoriesLeaveUnsetMembersOut() {
        // WebIDL only skips `undefined`; a key set to null would be converted (and rejected for enum members).
        val descriptor = GPUBufferDescriptor(size = 16.0, usage = 1)

        assertEquals("size,usage", keysOf(descriptor))
    }

    @Test
    fun dictionaryFactoriesSetGivenOptionalMembers() {
        val descriptor = GPUBufferDescriptor(size = 16.0, usage = 1, label = "vertices", mappedAtCreation = true)

        assertEquals("label,mappedAtCreation,size,usage", keysOf(descriptor))
    }

    @Test
    fun stagingFillsTheSameTypedArrayInPlace() {
        val staging = Float32Staging(capacity = 4)
        val first = staging.fill(floatArrayOf(1f, 2f))
        val second = staging.fill(floatArrayOf(3f, 4f, 5f))

        assertSame(first, second)
        assertEquals(listOf(3f, 4f, 5f, 0f), List(4) { second[it] })
        assertTrue(runCatching { staging.fill(FloatArray(5)) }.isFailure, "overfilling must fail")
    }
}
