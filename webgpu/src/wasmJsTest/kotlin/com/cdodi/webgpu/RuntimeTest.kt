package com.cdodi.webgpu

import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.runtime.Float32Staging
import com.cdodi.webgpu.runtime.JsPromiseRejection
import com.cdodi.webgpu.runtime.await
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.get
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun resolvedWith(value: String): Promise<JsString> = js("Promise.resolve(value)")

private fun typeError(message: String): JsAny = js("new TypeError(message)")

private fun rejectedWith(reason: JsAny): Promise<JsAny?> = js("Promise.reject(reason)")

private fun keysOf(obj: JsAny): String = js("Object.keys(obj).sort().join(',')")

class RuntimeTest {

    @Test
    fun awaitReturnsTheResolvedValue() = runTest {
        assertEquals("done", resolvedWith("done").await().toString())
    }

    @Test
    fun rejectionKeepsTheOriginalJsError() = runTest {
        val error = typeError("boom")

        val rejection = assertFailsWith<JsPromiseRejection> { rejectedWith(error).await() }

        assertSame(error, rejection.reason)
        assertEquals("TypeError", rejection.name)
        assertEquals("TypeError: boom", rejection.message)
    }

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
