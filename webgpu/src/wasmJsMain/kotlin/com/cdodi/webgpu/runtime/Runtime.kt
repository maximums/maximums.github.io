package com.cdodi.webgpu.runtime

import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.set
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise

/*
 * The small runtime that generated bindings call into. Everything else in :webgpu is either generated or builds on these.
 */

/**
 * A fresh JS object typed as a dictionary, filled by [config]. Generated dictionary factories only assign the members
 * that were given: WebIDL skips a member only when it is `undefined`, and a `null` would be converted (and for an enum
 * member rejected with a TypeError).
 */
inline fun <T : JsAny> createJsObject(config: T.() -> Unit = {}): T = createEmptyJsObject().unsafeCast<T>().apply(config)

@PublishedApi
internal fun createEmptyJsObject(): JsAny = js("({})")

/** A rejected JS promise. [reason] is the original JS value (often a DOMException or a GPUError), kept as it is. */
class JsPromiseRejection(val reason: JsAny?) : Exception(describe(reason)) {
    /** The JS error's `name`, e.g. "OperationError" or "TypeError"; null if the reason has none. */
    val name: String? get() = jsErrorName(reason)
}

private fun jsErrorName(error: JsAny?): String? = js("(error != null && error.name != null) ? String(error.name) : null")

private fun jsErrorMessage(error: JsAny?): String? = js("(error != null && error.message != null) ? String(error.message) : null")

private fun jsToString(value: JsAny?): String = js("String(value)")

private fun describe(reason: JsAny?): String {
    val name = jsErrorName(reason)
    val message = jsErrorMessage(reason)
    return when {
        name != null && message != null -> "$name: $message"
        message != null -> message
        else -> jsToString(reason)
    }
}

/**
 * Awaits a JS promise. Unlike kotlinx-coroutines' `Promise.await()`, a rejection keeps the original JS error:
 * it is thrown as [JsPromiseRejection] with the JS value in [JsPromiseRejection.reason].
 *
 * Cancelling the coroutine stops waiting; the JS promise itself cannot be cancelled and simply settles unobserved.
 */
suspend fun <T : JsAny?> Promise<T>.await(): T = suspendCancellableCoroutine { continuation ->
    then<JsAny?>(
        { value -> continuation.resume(value); null },
        { reason -> continuation.resumeWithException(JsPromiseRejection(reason)); null },
    )
}

/**
 * A reusable [Float32Array] for uploading Kotlin data (uniforms, small edits) without allocating a new typed array
 * every frame. Kotlin arrays live in the Wasm GC heap, so the copy is element by element; keep bulk data on the GPU.
 */
class Float32Staging(capacity: Int) {
    val array = Float32Array(capacity)

    /** Copies [values] (from index 0) into the start of [array] and returns [array]. */
    fun fill(values: FloatArray, count: Int = values.size): Float32Array {
        require(count <= array.length) { "Staging capacity ${array.length} is smaller than $count values" }
        for (i in 0 until count) array[i] = values[i]
        return array
    }
}

/** Like [Float32Staging], for unsigned 32-bit data (indices, counters, flags). */
class Uint32Staging(capacity: Int) {
    val array = Uint32Array(capacity)

    fun fill(values: IntArray, count: Int = values.size): Uint32Array {
        require(count <= array.length) { "Staging capacity ${array.length} is smaller than $count values" }
        for (i in 0 until count) array[i] = values[i]
        return array
    }
}
