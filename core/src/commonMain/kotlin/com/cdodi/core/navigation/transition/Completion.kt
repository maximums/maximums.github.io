package com.cdodi.core.navigation.transition

import com.cdodi.core.navigation.signal.Signal
import com.cdodi.core.navigation.signal.Signals
import kotlin.time.Duration

/**
 * When one basic transition ends. Time-based rules report progress; a condition-based rule reports none while waiting.
 * `and` / `or` combine rules *inside one* transition; to combine whole transitions use `+`, `with` and `or` on Transition.
 */
sealed interface Completion {

    /** Ends after [duration]; progress follows [easing]. */
    data class After(val duration: Duration, val easing: Easing = Easing.Linear) : Completion

    /** Ends no earlier than [duration]; meant for `atLeast(600.ms) and whenever(ready)`. Linear progress. */
    data class AtLeast(val duration: Duration) : Completion

    /** Ends when [signal] is raised, or after [timeout] if given. No progress while waiting. */
    data class Whenever(val signal: Signal, val timeout: Duration? = null) : Completion

    data class All(val rules: List<Completion>) : Completion

    data class AnyOf(val rules: List<Completion>) : Completion
}

fun after(duration: Duration, easing: Easing = Easing.Linear): Completion = Completion.After(duration, easing)

fun atLeast(duration: Duration): Completion = Completion.AtLeast(duration)

fun whenever(signal: Signal, timeout: Duration? = null): Completion = Completion.Whenever(signal, timeout)

infix fun Completion.and(other: Completion): Completion =
    Completion.All((this as? Completion.All)?.rules.orEmpty().ifEmpty { listOf(this) } + ((other as? Completion.All)?.rules ?: listOf(other)))

infix fun Completion.or(other: Completion): Completion =
    Completion.AnyOf((this as? Completion.AnyOf)?.rules.orEmpty().ifEmpty { listOf(this) } + ((other as? Completion.AnyOf)?.rules ?: listOf(other)))

/**
 * If this rule is met by [until] (seconds since the segment started), the time it was met, else null.
 * A condition found raised is treated as met at [from], the start of the frame, so no frame is lost.
 */
internal fun Completion.metAt(from: Double, until: Double, signals: Signals): Double? = when (this) {
    is Completion.After -> duration.seconds.takeIf { until >= it }?.coerceAtLeast(from)
    is Completion.AtLeast -> duration.seconds.takeIf { until >= it }?.coerceAtLeast(from)
    is Completion.Whenever -> when {
        signals.isRaised(signal) -> from
        timeout != null && until >= timeout.seconds -> timeout.seconds.coerceAtLeast(from)
        else -> null
    }
    is Completion.All -> rules.map { it.metAt(from, until, signals) }.takeIf { times -> times.all { it != null } }?.maxOf { it!! }
    is Completion.AnyOf -> rules.mapNotNull { it.metAt(from, until, signals) }.minOrNull()
}

/** Progress in `[0, 1]` at [elapsed] seconds, or null if it depends on a condition that is still pending. */
internal fun Completion.progress(elapsed: Double, isDone: Boolean): Float? = when {
    isDone -> 1f
    this is Completion.After -> easing.transform(fraction(elapsed, duration))
    this is Completion.AtLeast -> fraction(elapsed, duration)
    this is Completion.Whenever -> null
    this is Completion.All -> rules.map { it.progress(elapsed, false) }.takeIf { all -> all.none { it == null } }?.minOf { it!! }
    this is Completion.AnyOf -> rules.mapNotNull { it.progress(elapsed, false) }.maxOrNull()
    else -> null
}

private val Duration.seconds: Double get() = inWholeNanoseconds / 1e9

private fun fraction(elapsed: Double, duration: Duration): Float =
    if (duration <= Duration.ZERO) 1f else (elapsed / duration.seconds).coerceIn(0.0, 1.0).toFloat()
