package com.cdodi.core.navigation.transition

import com.cdodi.core.navigation.effect.AnimatedEffect
import com.cdodi.core.navigation.effect.ShaderEffect
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.navigation.signal.Signal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TransitionTest {

    private val wipe = AnimatedEffect("wipe")
    private val fog = ShaderEffect("fog")
    private val signals = MutableSignals()
    private val ready = Signal("ready")

    private fun TransitionRunner.progressOf(id: String): Float? = activeEffects().single { it.effect.id == id }.progress

    @Test
    fun timeBasedProgressFollowsTheEasing() {
        val linear = TransitionRunner(transition(after(1.seconds)) { play(wipe) }, signals)
        val settled = TransitionRunner(transition(after(1.seconds, Easing.Settle)) { play(wipe) }, signals)

        linear.advance(0.5)
        settled.advance(0.5)

        assertEquals(0.5f, linear.progressOf("wipe")!!, 1e-4f)
        assertTrue(settled.progressOf("wipe")!! > 0.5f, "Settle front-loads the movement")
    }

    @Test
    fun timeCarriesIntoTheNextSegment() {
        val runner = TransitionRunner(transition(after(100.milliseconds)) { play(wipe) } + transition(after(100.milliseconds)) { play(fog) }, signals)

        runner.advance(0.15)

        assertEquals(listOf("fog"), runner.activeEffects().map { it.effect.id })
        assertEquals(0.5f, runner.progressOf("fog")!!, 1e-4f)
    }

    @Test
    fun anEffectSeesOnlyItsOwnSegmentsProgress() {
        val fogAlone = TransitionRunner(transition(after(1.seconds)) { play(fog) }, signals)
        val fogSecond = TransitionRunner(transition(after(2.seconds)) { play(wipe) } + transition(after(1.seconds)) { play(fog) }, signals)

        fogAlone.advance(0.25)
        fogSecond.advance(2.25)

        assertEquals(fogAlone.progressOf("fog"), fogSecond.progressOf("fog"))
    }

    @Test
    fun parallelEndsWithItsSlowestPart() {
        val runner = TransitionRunner(transition(after(1.seconds)) { play(wipe) } with transition(after(2.seconds)) { play(fog) }, signals)

        runner.advance(1.5)
        assertEquals(listOf("fog"), runner.activeEffects().map { it.effect.id })
        assertFalse(runner.isDone)

        runner.advance(0.5)
        assertTrue(runner.isDone)
    }

    @Test
    fun raceEndsWithItsFastestPartAndStopsTheOther() {
        val runner = TransitionRunner(transition(after(1.seconds)) { play(wipe) } or transition(after(2.seconds)) { play(fog) }, signals)

        runner.advance(1.0)

        assertTrue(runner.isDone)
        assertTrue(runner.activeEffects().isEmpty())
    }

    @Test
    fun waitingOnASignalHasNoProgressUntilItIsRaised() {
        val runner = TransitionRunner(transition(whenever(ready)) { play(fog) }, signals)

        runner.advance(5.0)
        assertNull(runner.progressOf("fog"))
        assertEquals(5.0, runner.activeEffects().single().elapsed, 1e-9)

        signals.raise(ready)
        runner.advance(0.016)
        assertTrue(runner.isDone)
    }

    @Test
    fun aTimeoutEndsTheWait() {
        val runner = TransitionRunner(transition(whenever(ready, timeout = 3.seconds)) { play(fog) }, signals)

        runner.advance(2.9)
        assertFalse(runner.isDone)
        runner.advance(0.2)
        assertTrue(runner.isDone)
    }

    @Test
    fun atLeastKeepsAQuickSceneWaiting() {
        val holdFog = transition(atLeast(600.milliseconds) and whenever(ready, timeout = 3.seconds)) { play(fog) }
        val runner = TransitionRunner(holdFog, signals)
        signals.raise(ready) // the scene was ready immediately

        runner.advance(0.5)
        assertFalse(runner.isDone, "the fog still holds for its 600 ms")
        runner.advance(0.1)
        assertTrue(runner.isDone)
    }

    @Test
    fun reversedPlaysEffectsFromOneToZero() {
        val runner = TransitionRunner(transition(after(1.seconds)) { play(wipe) }.reversed(), signals)

        runner.advance(0.25)

        assertEquals(0.75f, runner.progressOf("wipe")!!, 1e-4f)
    }

    @Test
    fun reversedSequencesPlayTheirPartsBackwards() {
        val forward = transition(after(1.seconds)) { play(wipe) } + transition(after(1.seconds)) { play(fog) }
        val runner = TransitionRunner(forward.reversed(), signals)

        runner.advance(0.25)

        assertEquals(listOf("fog"), runner.activeEffects().map { it.effect.id })
        assertEquals(0.75f, runner.progressOf("fog")!!, 1e-4f)
    }

    @Test
    fun reversingMidwayPlaysBackWhatRanFromTheCurrentPoint() {
        val runner = TransitionRunner(transition(after(1.seconds)) { play(wipe) } + transition(after(1.seconds)) { play(fog) }, signals)
        runner.advance(1.4) // wipe done, fog at 0.4

        val back = runner.reversedFromHere()
        back.advance(0.1)
        assertEquals(0.3f, back.progressOf("fog")!!, 1e-4f)

        back.advance(0.4) // fog back to 0, then 0.1 s into the wipe played backwards
        assertEquals(0.9f, back.progressOf("wipe")!!, 1e-4f)

        back.advance(0.9)
        assertTrue(back.isDone)
    }

    @Test
    fun reversingBeforeAnythingPlayedPlaysNothing() {
        val runner = TransitionRunner(transition(after(1.seconds)) { play(wipe) }, signals)

        val back = runner.reversedFromHere()
        back.advance(0.0)

        assertTrue(back.isDone)
    }

    @Test
    fun repeatUntilLoopsUntilTheSignalAtTheEndOfARound() {
        val runner = TransitionRunner(transition(after(1.seconds)) { play(fog) }.repeatUntil(ready), signals)

        runner.advance(2.5) // two full rounds, then half of a third
        assertEquals(0.5f, runner.progressOf("fog")!!, 1e-4f)

        signals.raise(ready)
        runner.advance(0.5)
        assertTrue(runner.isDone)
    }

    @Test
    fun delayedAndWithTimeoutAreBuiltFromTheOperators() {
        val delayed = TransitionRunner(transition(after(1.seconds)) { play(fog) }.delayed(500.milliseconds), signals)
        delayed.advance(0.75)
        assertEquals(0.25f, delayed.progressOf("fog")!!, 1e-4f)

        val capped = TransitionRunner(transition(whenever(ready)) { play(fog) }.withTimeout(2.seconds), signals)
        capped.advance(2.0)
        assertTrue(capped.isDone)
    }

    @Test
    fun advanceReturnsTheTimeItDidNotNeed() {
        val runner = TransitionRunner(transition(after(1.seconds)) { play(wipe) }, signals)

        assertEquals(0.0, runner.advance(0.4), 1e-9)
        assertEquals(0.1, runner.advance(0.7), 1e-9)
    }

    @Test
    fun overallProgressIsKnownWhileOnlyTimeDecides() {
        // 1 s, then the longer of 1 s and 2 s: 3 s in all.
        val timed = TransitionRunner(
            transition(after(1.seconds)) { play(wipe) } + (transition(after(1.seconds)) { play(fog) } with transition(after(2.seconds)) { play(wipe) }),
            signals,
        )
        val waiting = TransitionRunner(transition(after(1.seconds)) { play(wipe) } + transition(whenever(ready)) { play(fog) }, signals)

        timed.advance(1.5)
        waiting.advance(0.5)

        assertEquals(0.5f, timed.progress!!, 1e-4f)
        assertNull(waiting.progress)
    }

    @Test
    fun cubicBezierHitsItsEndsAndIsMonotonic() {
        val values = (0..100).map { Easing.Settle.transform(it / 100f) }

        assertEquals(0f, values.first())
        assertEquals(1f, values.last())
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }
}
