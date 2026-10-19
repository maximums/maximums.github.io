package com.cdodi.webidl.runtime

import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private fun resolvedWith(value: String): Promise<JsString> = js("Promise.resolve(value)")

private fun typeError(message: String): JsAny = js("new TypeError(message)")

private fun rejectedWith(reason: JsAny): Promise<JsAny?> = js("Promise.reject(reason)")

private fun keysOf(obj: JsAny): String = js("Object.keys(obj).sort().join(',')")

/** A dictionary as generated code declares one: members that may or may not be set. */
private external interface Options : JsAny {
    var label: String?
    var count: Int?
}

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
    fun aJsObjectHasOnlyTheMembersItWasGiven() {
        // WebIDL only skips `undefined`: a member never assigned must not exist at all, not even as null.
        assertEquals("", keysOf(createJsObject<Options>()))
        assertEquals("label", keysOf(createJsObject<Options> { label = "rain" }))
    }
}
