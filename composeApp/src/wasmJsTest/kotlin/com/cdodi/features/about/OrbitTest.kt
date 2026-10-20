package com.cdodi.features.about

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrbitTest {

    @Test
    fun aDragMovesTheTargetAndTheTreeFollowsWithWeight() {
        val orbit = Orbit()
        orbit.turn(0.5f)
        assertEquals(0.5f, orbit.target)
        assertEquals(0f, orbit.yaw, "nothing moves until the clock does")
        orbit.step(Orbit.SETTLE)
        // One time constant: about 63 % of the way.
        assertEquals(0.5f * (1f - exp(-1f)), orbit.yaw, 1e-5f)
        repeat(60) { orbit.step(1f / 60) }
        assertTrue(abs(orbit.yaw - 0.5f) < 0.02f, "after a second it's all but there: ${orbit.yaw}")
    }

    @Test
    fun itTurnsOnlyWithinTheArc() {
        val orbit = Orbit()
        orbit.turn(5f)
        assertEquals(Orbit.ARC, orbit.target)
        orbit.turn(-10f)
        assertEquals(-Orbit.ARC, orbit.target)
        orbit.turn(0.25f)
        assertEquals(-Orbit.ARC + 0.25f, orbit.target, 1e-6f, "back from the edge at once, no stored overshoot")
    }

    @Test
    fun aPausedClockHoldsIt() {
        val orbit = Orbit()
        orbit.turn(0.8f)
        orbit.step(0f)
        assertEquals(0f, orbit.yaw)
    }

    @Test
    fun theStepDoesNotDependOnTheFrameRate() {
        // Thirty frames or a hundred and twenty over the same half second land in the same place.
        val slow = Orbit().apply { turn(1f); repeat(15) { step(1f / 30) } }
        val fast = Orbit().apply { turn(1f); repeat(60) { step(1f / 120) } }
        assertEquals(slow.yaw, fast.yaw, 1e-4f)
    }
}
