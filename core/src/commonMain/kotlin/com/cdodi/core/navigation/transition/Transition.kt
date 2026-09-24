package com.cdodi.core.navigation.transition

import com.cdodi.core.navigation.effect.Effect
import com.cdodi.core.navigation.signal.Signal
import kotlin.time.Duration

/**
 * A transition is a value. Basic transitions (one completion rule plus effects) are combined with operators, and the
 * result is again a transition that can go on an edge or be combined further:
 *
 * ```
 * val breathOnTheWindow = wipe + fogRoll + holdFog + clearing
 * ```
 *
 * - `a + b`: sequence, `b` starts when `a` completes
 * - `a with b`: parallel, completes when both have
 * - `a or b`: race, completes when the first one does and cancels the other
 *
 * Operators flatten as they build (`(a + b) + c` and `a + (b + c)` are the same `Sequence(a, b, c)`), and [None] is the
 * identity of `+` and `with`.
 */
sealed interface Transition {

    /** Completes immediately and plays nothing. */
    data object None : Transition

    /** @param reversed effects play backwards (progress runs from 1 to 0). */
    data class Basic(
        val completion: Completion,
        val effects: List<Effect> = emptyList(),
        val reversed: Boolean = false,
    ) : Transition

    data class Sequence(val parts: List<Transition>) : Transition

    data class Parallel(val parts: List<Transition>) : Transition

    data class Race(val parts: List<Transition>) : Transition

    /** Plays [inner] again and again; ends when [signal] is raised at the end of a round. */
    data class RepeatUntil(val inner: Transition, val signal: Signal) : Transition
}

class TransitionBuilder internal constructor() {
    internal val effects = mutableListOf<Effect>()

    fun play(effect: Effect) {
        effects += effect
    }
}

fun transition(completion: Completion, block: TransitionBuilder.() -> Unit = {}): Transition =
    Transition.Basic(completion, TransitionBuilder().apply(block).effects.toList())

operator fun Transition.plus(other: Transition): Transition = when {
    this == Transition.None -> other
    other == Transition.None -> this
    else -> Transition.Sequence(partsOf<Transition.Sequence>(this) { it.parts } + partsOf<Transition.Sequence>(other) { it.parts })
}

infix fun Transition.with(other: Transition): Transition = when {
    this == Transition.None -> other
    other == Transition.None -> this
    else -> Transition.Parallel(partsOf<Transition.Parallel>(this) { it.parts } + partsOf<Transition.Parallel>(other) { it.parts })
}

infix fun Transition.or(other: Transition): Transition =
    Transition.Race(partsOf<Transition.Race>(this) { it.parts } + partsOf<Transition.Race>(other) { it.parts })

/** Waits [delay] (playing nothing) before this transition. */
fun Transition.delayed(delay: Duration): Transition = transition(after(delay)) + this

/** Completes after [timeout] at the latest. */
fun Transition.withTimeout(timeout: Duration): Transition = this or transition(after(timeout))

fun Transition.repeatUntil(signal: Signal): Transition = Transition.RepeatUntil(this, signal)

/**
 * The same transition played backwards: sequences in reverse order, every effect from 1 to 0.
 * `reversed().reversed()` is the original.
 */
fun Transition.reversed(): Transition = when (this) {
    Transition.None -> this
    is Transition.Basic -> copy(reversed = !reversed)
    is Transition.Sequence -> Transition.Sequence(parts.asReversed().map { it.reversed() })
    is Transition.Parallel -> Transition.Parallel(parts.map { it.reversed() })
    is Transition.Race -> Transition.Race(parts.map { it.reversed() })
    is Transition.RepeatUntil -> copy(inner = inner.reversed())
}

private inline fun <reified T : Transition> partsOf(transition: Transition, parts: (T) -> List<Transition>): List<Transition> =
    if (transition is T) parts(transition) else listOf(transition)
