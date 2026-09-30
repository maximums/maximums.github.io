package com.cdodi.core.navigation

import com.cdodi.core.navigation.NavResult.Reason
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.navigation.graph.Interrupt
import com.cdodi.core.navigation.graph.NavGraph
import com.cdodi.core.navigation.signal.Signals
import com.cdodi.core.navigation.transition.Transition
import com.cdodi.core.navigation.transition.TransitionRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The navigation state machine: `Idle(at)` → `Transitioning(from, to)` → `Idle(to)`.
 *
 * [navigate], [back] and [forward] change the [BackStack] at once, and the screen follows along the graph's edges.
 * If a transition ends somewhere other than the current entry (the target changed while it played), the next leg
 * starts from there straight away, with whatever time the frame had left. A navigation that arrives mid-transition
 * is handled by the running edge's [Interrupt] policy. A navigation is checked when it is asked for, from where the
 * screen will be when its leg starts; a refused one leaves everything as it was.
 *
 * It knows no framework and no clock: [advance] it with the transitions clock's seconds in the Navigation phase.
 *
 * @param reducedMotion asked whenever a leg starts; while it says yes, the leg plays the graph's
 * [NavGraph.reducedMotion] transition instead of the edge's.
 */
class Navigator(
    private val graph: NavGraph,
    private val signals: Signals,
    start: Destination = graph.start,
    private val reducedMotion: () -> Boolean = { false },
) {

    /** One transition between two destinations, in the direction of travel. */
    private class Leg(val from: Destination, val to: Destination, val runner: TransitionRunner, val onInterrupt: Interrupt) {
        fun reversed() = Leg(to, from, runner.reversedFromHere(), onInterrupt)
    }

    private var history = BackStack(listOf(start))
    private var at: Destination = start // where the screen is while idle
    private var leg: Leg? = null
    private val mutableState = MutableStateFlow<NavState>(NavState.Idle(start, history))

    val state: StateFlow<NavState> = mutableState.asStateFlow()

    init {
        require(start.route in graph) { "the start destination $start is not in the graph" }
    }

    fun navigate(to: Destination): NavResult =
        if (to == history.current) NavResult.AlreadyThere else change(history.push(to))

    fun back(): NavResult = if (history.canGoBack) change(history.back()) else NavResult.Refused(Reason.EndOfHistory)

    fun forward(): NavResult = if (history.canGoForward) change(history.forward()) else NavResult.Refused(Reason.EndOfHistory)

    fun advance(seconds: Double) {
        val running = leg ?: return
        val leftover = running.runner.advance(seconds)
        if (running.runner.isDone) arrive(running.to, leftover)
        publish()
    }

    private fun change(next: BackStack): NavResult {
        val target = next.current
        if (target.route !in graph) return NavResult.Refused(Reason.UnknownDestination)
        val running = leg
        val origin = when {
            running == null -> at
            target == running.to -> running.to // already on its way there
            running.onInterrupt == Interrupt.Reverse -> running.from
            else -> running.to
        }
        refusal(origin, target)?.let { return it }

        history = next
        when {
            running == null -> start(at, target, seconds = 0.0)
            target == running.to || running.onInterrupt == Interrupt.Queue -> Unit // continues on arrival
            running.onInterrupt == Interrupt.Replace -> arrive(running.to, leftover = 0.0)
            else -> play(running.reversed(), seconds = 0.0)
        }
        publish()
        return NavResult.Accepted
    }

    private fun refusal(from: Destination, to: Destination): NavResult.Refused? {
        if (from == to) return null
        val edge = graph.edgeFor(from.route, to.route)
        return when {
            edge == null -> if (from.route == to.route) null else NavResult.Refused(Reason.NoEdge)
            edge.guard?.allows(from, to) == false -> NavResult.Refused(Reason.Guard)
            else -> null
        }
    }

    private fun start(from: Destination, to: Destination, seconds: Double) {
        val edge = graph.edgeFor(from.route, to.route)
        val transition = when {
            // Only happens when just the arguments change (`Life(B3/S23)` → `Life(B36/S23)`): switch at once.
            edge == null -> Transition.None
            reducedMotion() -> graph.reducedMotion ?: edge.transition
            else -> edge.transition
        }
        play(Leg(from, to, TransitionRunner(transition, signals), edge?.onInterrupt ?: Interrupt.Replace), seconds)
    }

    /** Makes [next] the running leg. Advancing it, even by zero, starts its first segment, so its effects show at once. */
    private fun play(next: Leg, seconds: Double) {
        leg = next
        val leftover = next.runner.advance(seconds)
        if (next.runner.isDone) arrive(next.to, leftover)
    }

    private fun arrive(destination: Destination, leftover: Double) {
        leg = null
        at = destination
        if (history.current != destination) start(destination, history.current, leftover)
    }

    private fun publish() {
        val running = leg
        mutableState.value = if (running == null) {
            NavState.Idle(at, history)
        } else {
            NavState.Transitioning(running.from, running.to, running.runner.activeEffects(), running.runner.progress, history)
        }
    }
}
