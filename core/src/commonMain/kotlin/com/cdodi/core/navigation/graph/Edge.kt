package com.cdodi.core.navigation.graph

import com.cdodi.core.navigation.transition.Transition

/**
 * Which destinations one end of an [Edge] applies to: a [Route] (exactly that route), [AnyOf] a set of routes, or
 * [AnyDestination]. They are ranked by [specificity] in that order.
 */
sealed interface EdgeMatcher {
    val specificity: Int

    fun matches(route: Route<*>): Boolean

    data class AnyOf(val routes: Set<Route<*>>) : EdgeMatcher {
        override val specificity: Int get() = 1

        override fun matches(route: Route<*>): Boolean = route in routes

        override fun toString(): String = routes.joinToString(prefix = "anyOf(", postfix = ")")
    }

    data object AnyDestination : EdgeMatcher {
        override val specificity: Int get() = 0

        override fun matches(route: Route<*>): Boolean = true

        override fun toString(): String = "any"
    }
}

/** The routes a matcher names explicitly; [EdgeMatcher.AnyDestination] names none. */
internal val EdgeMatcher.namedRoutes: Set<Route<*>>
    get() = when (this) {
        is Route<*> -> setOf(this)
        is EdgeMatcher.AnyOf -> routes
        EdgeMatcher.AnyDestination -> emptySet()
    }

/** What happens when a new navigation arrives while this edge's transition is still playing. */
enum class Interrupt {
    /** Let the transition finish, then go on to the newest target. */
    Queue,

    /** Stop the transition as if it had finished, then go on from its destination. */
    Replace,

    /** Play what already ran backwards from the current point; if the new target is somewhere else, go on from there. */
    Reverse,
}

/** Decides whether a navigation along an edge is allowed right now (`allowIf { from, to -> webGpuAvailable }`). */
fun interface EdgeGuard {
    fun allows(from: Destination, to: Destination): Boolean
}

/** A directed connection between destinations, with the [transition] played along it. */
class Edge(
    val from: EdgeMatcher,
    val to: EdgeMatcher,
    val transition: Transition,
    val onInterrupt: Interrupt = Interrupt.Queue,
    val guard: EdgeGuard? = null,
) {
    fun matches(from: Route<*>, to: Route<*>): Boolean = this.from.matches(from) && this.to.matches(to)

    /**
     * At least as specific as [other] at both ends and more specific at one. Like overload resolution: an edge is
     * chosen only if it dominates every other candidate, so `Home → any` against `any → Boids` is ambiguous.
     */
    fun dominates(other: Edge): Boolean =
        from.specificity >= other.from.specificity && to.specificity >= other.to.specificity &&
            (from.specificity > other.from.specificity || to.specificity > other.to.specificity)

    override fun toString(): String = "$from → $to"
}
