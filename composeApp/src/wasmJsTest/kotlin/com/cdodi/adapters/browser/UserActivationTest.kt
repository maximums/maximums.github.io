package com.cdodi.adapters.browser

import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlin.test.Test
import kotlin.test.assertEquals

private fun eventTarget(): EventTarget = js("new EventTarget()")

class UserActivationTest {

    private val target = eventTarget()
    private var activated = false
    private var calls = 0

    private fun dispatch(type: String) {
        target.dispatchEvent(Event(type))
    }

    @Test
    fun aTouchPutDownIsLetPassAndTheLiftStartsIt() {
        awaitUserActivation(target, { activated }) { calls++ }
        dispatch("pointerdown") // a finger put down: no activation yet
        assertEquals(0, calls)
        activated = true
        dispatch("pointerup")
        assertEquals(1, calls)
    }

    @Test
    fun itAnswersOnceAndLeavesNoListenerBehind() {
        activated = true
        awaitUserActivation(target, { activated }) { calls++ }
        dispatch("keydown")
        dispatch("keydown")
        dispatch("touchend")
        assertEquals(1, calls)
    }

    @Test
    fun closingStopsTheWait() {
        activated = true
        awaitUserActivation(target, { activated }) { calls++ }.close()
        dispatch("pointerdown")
        dispatch("touchend")
        assertEquals(0, calls)
    }
}
