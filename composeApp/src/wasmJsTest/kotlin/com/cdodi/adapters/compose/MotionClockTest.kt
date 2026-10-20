package com.cdodi.adapters.compose

import com.cdodi.core.navigation.transition.Easing
import com.cdodi.core.time.DefaultHeartbeat
import com.cdodi.design.theme.Motion
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the design system meets the app's time: its MotionClock port on the heartbeat, its Settle beside the core's. */
class MotionClockTest {

    @Test
    fun uiMotionRunsOnTheHeartbeatsUiClock() {
        val heartbeat = DefaultHeartbeat()
        val ui = heartbeat.child("ui")
        val seconds = mutableListOf<Float>()
        val frames = ui.asMotionClock().everyFrame { seconds += it }
        heartbeat.tick(0L)
        heartbeat.tick(16_000_000L)
        ui.timeScale = 0.5f // a slowed clock slows the motion on it
        heartbeat.tick(32_000_000L)
        frames.close()
        heartbeat.tick(48_000_000L)
        assertEquals(listOf(0f, 0.016f, 0.008f), seconds.map { (it * 1000).toInt() / 1000f })
    }

    @Test
    fun theUiSettlesAsTheTransitionsDo() {
        // DESIGN.md's Settle twice: Compose's, in the design system, and the core's, for transitions. The design
        // system can't depend on the core, so this keeps the two in step.
        for (step in 0..20) {
            val t = step / 20f
            assertEquals(Easing.Settle.transform(t), Motion.Settle.transform(t), 0.002f, "at $t")
        }
    }
}
