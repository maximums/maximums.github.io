package com.cdodi.core.navigation.transition

import com.cdodi.core.navigation.effect.AnimatedEffect
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.navigation.signal.Signal
import kotlin.math.round
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Property-based tests: the laws that make composing transitions safe, checked on randomly generated transition trees
 * (fixed seeds, so failures reproduce). Behavioural checks run both sides on the same clock and signals and compare
 * the timeline of active effects and the moment each finishes.
 */
class TransitionLawsTest {

    private val signalNames = listOf("a", "b", "c")

    /** When each signal gets raised in a behavioural run (seconds). */
    private val raisedAt = mapOf(Signal("a") to 0.30, Signal("b") to 0.75, Signal("c") to 99.0)

    private fun Random.transition(depth: Int = 3): Transition {
        if (depth == 0 || nextInt(3) == 0) return leaf()
        val a = transition(depth - 1)
        val b = transition(depth - 1)
        return when (nextInt(3)) {
            0 -> a + b
            1 -> a with b
            else -> a or b
        }
    }

    private var effectCounter = 0

    private fun Random.leaf(): Transition = when (nextInt(6)) {
        0 -> Transition.None
        1 -> transition(whenever(Signal(signalNames.random(this)), timeout = (100 + nextInt(10) * 100).milliseconds)) { play(effect()) }
        else -> transition(after((nextInt(1, 20) * 50).milliseconds)) { play(effect()) }
    }

    private fun effect() = AnimatedEffect("e${effectCounter++}")

    private data class Snapshot(val effects: Set<Pair<String, Float?>>)

    /** Runs in 10 ms steps: the active effects after each step, and the step at which it finished. */
    private fun timeline(transition: Transition): Pair<List<Snapshot>, Int?> {
        val signals = MutableSignals()
        val runner = TransitionRunner(transition, signals)
        val snapshots = mutableListOf<Snapshot>()
        var time = 0.0
        for (step in 0 until 600) {
            raisedAt.forEach { (signal, at) -> if (time >= at) signals.raise(signal) }
            runner.advance(if (step == 0) 0.0 else 0.01)
            time += if (step == 0) 0.0 else 0.01
            snapshots += Snapshot(runner.effects().map { it.effect.id to it.progress?.let { p -> round(p * 1000) / 1000 } }.toSet())
            if (runner.isDone) return snapshots to step
        }
        return snapshots to null
    }

    private fun forAll(seeds: IntRange = 1..300, check: (Random) -> Unit) = seeds.forEach { seed -> check(Random(seed)) }

    @Test
    fun operatorsAreAssociative() = forAll { random ->
        val (a, b, c) = List(3) { random.transition() }

        assertEquals((a + b) + c, a + (b + c))
        assertEquals((a with b) with c, a with (b with c))
        assertEquals((a or b) or c, a or (b or c))
    }

    @Test
    fun noneIsTheIdentityOfSequenceAndParallel() = forAll { random ->
        val a = random.transition()

        assertEquals(a, a + Transition.None)
        assertEquals(a, Transition.None + a)
        assertEquals(a, a with Transition.None)
        assertEquals(a, Transition.None with a)
    }

    @Test
    fun reversingTwiceGivesTheOriginal() = forAll { random ->
        val a = random.transition()

        assertEquals(a, a.reversed().reversed())
    }

    @Test
    fun parallelAndRaceAreCommutativeInBehaviour() = forAll(1..150) { random ->
        val a = random.transition(depth = 2)
        val b = random.transition(depth = 2)

        assertEquals(timeline(a with b), timeline(b with a), "with: $a / $b")
        assertEquals(timeline(a or b), timeline(b or a), "or: $a / $b")
    }

    @Test
    fun associativityHoldsInBehaviourToo() = forAll(1..150) { random ->
        val (a, b, c) = List(3) { random.transition(depth = 2) }

        assertEquals(timeline((a + b) + c), timeline(a + (b + c)))
    }

    @Test
    fun timeBasedTransitionsTakeAsLongReversed() = forAll(1..150) { random ->
        fun Random.timeOnly(depth: Int = 3): Transition =
            if (depth == 0 || nextInt(3) == 0) transition(after((nextInt(1, 20) * 50).milliseconds)) { play(effect()) }
            else when (nextInt(3)) {
                0 -> timeOnly(depth - 1) + timeOnly(depth - 1)
                1 -> timeOnly(depth - 1) with timeOnly(depth - 1)
                else -> timeOnly(depth - 1) or timeOnly(depth - 1)
            }

        val a = random.timeOnly()

        assertEquals(timeline(a).second, timeline(a.reversed()).second, "$a")
    }
}
