package com.cdodi.core.bus

import com.cdodi.core.time.DefaultHeartbeat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifecycleBusTest {

    private val heartbeat = DefaultHeartbeat()
    private val lifecycle = DefaultLifecycleBus()
    private var now = 0L

    private fun frame(seconds: Double = 0.05) {
        now += (seconds * 1e9).toLong()
        heartbeat.tick(now)
    }

    @Test
    fun eventsUpdateTheState() {
        lifecycle.send(LifecycleEvent.Background)
        lifecycle.send(LifecycleEvent.ReducedMotionChanged(reduced = true))
        assertEquals(Lifecycle(isVisible = false, prefersReducedMotion = true), lifecycle.state.value)

        lifecycle.send(LifecycleEvent.Foreground)
        assertEquals(Lifecycle(isVisible = true, prefersReducedMotion = true), lifecycle.state.value)
    }

    @Test
    fun aHiddenPagePausesTheHeartbeatAndTimeDoesNotJumpWhenItComesBack() {
        heartbeat.pauseWhile(lifecycle) { !it.isVisible }
        frame()
        frame() // 0.05

        lifecycle.send(LifecycleEvent.Background)
        frame() // this frame's time still counts, then the pause starts: 0.10
        assertTrue(heartbeat.isPaused)
        frame(30.0)

        lifecycle.send(LifecycleEvent.Foreground)
        frame(30.0) // the first frame back still sees the pause
        assertFalse(heartbeat.isPaused)
        assertEquals(0.10, heartbeat.elapsed, 1e-9)

        frame()
        assertEquals(0.15, heartbeat.elapsed, 1e-9)
    }

    @Test
    fun aPauseSetBySomeoneElseIsLeftAlone() {
        heartbeat.pauseWhile(lifecycle) { !it.isVisible }
        heartbeat.isPaused = true // e.g. a debug toggle

        lifecycle.send(LifecycleEvent.Background)
        frame()
        lifecycle.send(LifecycleEvent.Foreground)
        frame()

        assertTrue(heartbeat.isPaused)
    }

    @Test
    fun reducedMotionFreezesOnlyTheClocksThatAsk() {
        val ambient = heartbeat.child("ambient")
        val transitions = heartbeat.child("transitions")
        ambient.pauseWhile(lifecycle) { it.prefersReducedMotion }
        lifecycle.send(LifecycleEvent.ReducedMotionChanged(reduced = true))

        frame()
        frame()
        frame()

        assertEquals(0.0, ambient.elapsed)
        assertEquals(0.10, transitions.elapsed, 1e-9)
    }
}
