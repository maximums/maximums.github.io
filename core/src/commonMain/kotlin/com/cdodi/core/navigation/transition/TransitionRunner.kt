package com.cdodi.core.navigation.transition

import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.signal.Signals
import kotlin.time.Duration.Companion.seconds

/**
 * Plays a [Transition]: [advance] it with the seconds that passed on the transitions clock, then read
 * [activeEffects]. Each effect sees only *its own* segment's progress, whatever it was combined with, which is what
 * makes transitions reusable.
 *
 * Time carries across segments: when a segment completes in the middle of a frame, the rest of that frame already
 * counts for the next one.
 */
class TransitionRunner private constructor(private val root: Node, private val signals: Signals) {

    constructor(transition: Transition, signals: Signals) : this(nodeOf(transition), signals)

    val isDone: Boolean get() = root.isDone

    fun advance(seconds: Double) {
        root.advance(seconds.coerceAtLeast(0.0), signals)
    }

    fun activeEffects(): List<EffectState> = root.active()

    /**
     * A runner that plays back what already ran, backwards, starting from the current point: the segments that ran in
     * reverse order, the current one mirrored from its current progress. Used by `Interrupt.Reverse`.
     */
    fun reversedFromHere(): TransitionRunner = TransitionRunner(root.reversedFromHere(), signals)

    companion object {
        private fun nodeOf(transition: Transition): Node = when (transition) {
            Transition.None -> Node.NoneNode()
            is Transition.Basic -> Node.BasicNode(transition)
            is Transition.Sequence -> Node.SequenceNode(transition.parts.map(::nodeOf))
            is Transition.Parallel -> Node.ParallelNode(transition.parts.map(::nodeOf))
            is Transition.Race -> Node.RaceNode(transition.parts.map(::nodeOf))
            is Transition.RepeatUntil -> Node.RepeatNode(transition) { nodeOf(transition.inner) }
        }
    }

    private sealed class Node {
        abstract val isDone: Boolean

        /** Advances by [seconds] and returns the part of it not needed because this node completed. */
        abstract fun advance(seconds: Double, signals: Signals): Double

        abstract fun active(): List<EffectState>

        abstract fun reversedFromHere(): Node

        class NoneNode : Node() {
            override val isDone = true
            override fun advance(seconds: Double, signals: Signals) = seconds
            override fun active() = emptyList<EffectState>()
            override fun reversedFromHere(): Node = this
        }

        class BasicNode(private val basic: Transition.Basic, private var elapsed: Double = 0.0) : Node() {
            override var isDone = false
                private set
            private var started = false

            override fun advance(seconds: Double, signals: Signals): Double {
                if (isDone) return seconds
                started = true
                val until = elapsed + seconds
                val metAt = basic.completion.metAt(elapsed, until, signals)
                return if (metAt == null) {
                    elapsed = until
                    0.0
                } else {
                    isDone = true
                    elapsed = metAt
                    until - metAt
                }
            }

            override fun active(): List<EffectState> {
                if (!started || isDone) return emptyList()
                val progress = basic.completion.progress(elapsed, isDone = false)?.let { if (basic.reversed) 1f - it else it }
                return basic.effects.map { EffectState(it, progress, elapsed) }
            }

            override fun reversedFromHere(): Node {
                if (!started) return NoneNode() // never played, so there is nothing to play back
                val total = basic.completion.fixedSeconds()
                // Time-based: mirror the position. Waiting on a condition: play back over the time already spent.
                val back = if (total != null) {
                    Transition.Basic(basic.completion, basic.effects, reversed = !basic.reversed) to (total - elapsed.coerceAtMost(total))
                } else {
                    Transition.Basic(atLeast(elapsed.seconds), basic.effects, reversed = !basic.reversed) to 0.0
                }
                return BasicNode(back.first, back.second)
            }
        }

        class SequenceNode(private val parts: List<Node>) : Node() {
            private var index = 0
            override val isDone get() = index >= parts.size

            override fun advance(seconds: Double, signals: Signals): Double {
                var remaining = seconds
                while (index < parts.size) {
                    remaining = parts[index].advance(remaining, signals)
                    if (!parts[index].isDone) return 0.0
                    index++
                }
                return remaining
            }

            override fun active() = parts.getOrNull(index)?.active().orEmpty()

            override fun reversedFromHere(): Node {
                val ran = parts.subList(0, (index + 1).coerceAtMost(parts.size))
                return SequenceNode(ran.asReversed().map { it.reversedFromHere() })
            }
        }

        class ParallelNode(private val parts: List<Node>) : Node() {
            override val isDone get() = parts.all { it.isDone }

            override fun advance(seconds: Double, signals: Signals): Double =
                parts.minOf { it.advance(seconds, signals) }

            override fun active() = parts.flatMap { it.active() }

            override fun reversedFromHere(): Node = ParallelNode(parts.map { it.reversedFromHere() })
        }

        class RaceNode(private val parts: List<Node>) : Node() {
            override var isDone = false
                private set

            override fun advance(seconds: Double, signals: Signals): Double {
                if (isDone) return seconds
                val leftovers = parts.map { it.advance(seconds, signals) }
                val finished = parts.indices.filter { parts[it].isDone }
                if (finished.isEmpty()) return 0.0
                isDone = true
                return finished.maxOf { leftovers[it] }
            }

            override fun active() = if (isDone) emptyList() else parts.flatMap { it.active() }

            override fun reversedFromHere(): Node = RaceNode(parts.map { it.reversedFromHere() })
        }

        class RepeatNode(private val repeat: Transition.RepeatUntil, private val fresh: () -> Node) : Node() {
            private var current = fresh()
            override var isDone = false
                private set

            override fun advance(seconds: Double, signals: Signals): Double {
                var remaining = seconds
                while (!isDone) {
                    val before = remaining
                    remaining = current.advance(remaining, signals)
                    if (!current.isDone) return 0.0
                    if (signals.isRaised(repeat.signal)) isDone = true else current = fresh()
                    // Out of time, or a round that takes no time at all (which would otherwise spin forever).
                    if (!isDone && (remaining <= 0.0 || remaining == before)) return 0.0
                }
                return remaining
            }

            override fun active() = if (isDone) emptyList() else current.active()

            override fun reversedFromHere(): Node = current.reversedFromHere()
        }
    }
}

/** Total seconds if the rule is purely time-based, else null. */
private fun Completion.fixedSeconds(): Double? = when (this) {
    is Completion.After -> duration.inWholeNanoseconds / 1e9
    is Completion.AtLeast -> duration.inWholeNanoseconds / 1e9
    is Completion.Whenever -> null
    is Completion.All -> rules.map { it.fixedSeconds() }.takeIf { all -> all.none { it == null } }?.maxOf { it!! }
    is Completion.AnyOf -> rules.map { it.fixedSeconds() }.takeIf { all -> all.none { it == null } }?.minOf { it!! }
}
