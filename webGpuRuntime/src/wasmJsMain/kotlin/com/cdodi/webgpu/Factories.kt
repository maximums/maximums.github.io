package com.cdodi.webgpu

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.Promise

@JsFun("() => ({})")
external fun createEmptyJsObject(): JsAny

inline fun <T : JsAny> createJsObject(config: T.() -> Unit = {}): T {
    return createEmptyJsObject().unsafeCast<T>().apply(config)
}

@JsFun("() => []")
private external fun <T : JsAny?> newJsArray(): JsArray<T>

fun <T : JsAny?> List<T>.toJsArray(): JsArray<T> {
    val arr = newJsArray<T>()
    for (i in indices) {
        arr[i] = this[i]
    }
    return arr
}

suspend fun <T : JsAny?> Promise<T>.await(): T = suspendCoroutine { cont ->
    then<JsAny?>(
        { value -> cont.resume(value); null },
        { error -> cont.resumeWithException(RuntimeException(error.toString())); null }
    )
}
