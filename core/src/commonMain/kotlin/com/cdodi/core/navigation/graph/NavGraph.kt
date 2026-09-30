package com.cdodi.core.navigation.graph

import com.cdodi.core.navigation.transition.Transition
import com.cdodi.core.navigation.transition.reversed

@DslMarker
annotation class NavGraphDsl

/**
 * Destinations (the nodes) connected by edges that carry transitions. Built with [navGraph], which rejects a graph
 * with structural problems; [requireRenderable] checks the effects once the adapters have registered their renderers.
 */
class NavGraph internal constructor(
    val start: Destination,
    val routes: List<Route<*>>,
    val edges: List<Edge>,
    /** Played instead of every edge's transition while the user prefers reduced motion; null keeps the edges' own. */
    val reducedMotion: Transition?,
) {
    operator fun contains(route: Route<*>): Boolean = route in routes

    fun route(path: String): Route<*>? = routes.firstOrNull { it.path == path }

    /** The matching edges that no other matching edge dominates: exactly one in a valid graph, several if ambiguous. */
    fun candidates(from: Route<*>, to: Route<*>): List<Edge> {
        val matching = edges.filter { it.matches(from, to) }
        return matching.filter { edge -> matching.none { it.dominates(edge) } }
    }

    /** The edge played when going from [from] to [to], or null if none applies. */
    fun edgeFor(from: Route<*>, to: Route<*>): Edge? = candidates(from, to).singleOrNull()
}

/**
 * ```
 * val site = navGraph(start = Home) {
 *     destination(Home); destination(About); destination(LifeDestination); destination(Boids)
 *
 *     edge(from = any, to = any, transition = melt)
 *     edge(from = Home, to = anyOf(About, LifeDestination), transition = menuMorph).andBack()
 *     edge(from = any, to = Boids, transition = intoBoids, onInterrupt = Interrupt.Reverse)
 * }
 * ```
 *
 * @throws InvalidNavGraphException listing every structural problem at once.
 */
fun navGraph(start: Destination, block: NavGraphBuilder.() -> Unit): NavGraph {
    val graph = NavGraphBuilder().apply(block).build(start)
    val problems = GraphValidator.structure(graph)
    if (problems.isNotEmpty()) throw InvalidNavGraphException(problems)
    return graph
}

@NavGraphDsl
class NavGraphBuilder internal constructor() {
    private val routes = mutableListOf<Route<*>>()
    private val edges = mutableListOf<Edge>()

    val any: EdgeMatcher get() = EdgeMatcher.AnyDestination

    /** See [NavGraph.reducedMotion]; usually a short cross-fade. */
    var reducedMotion: Transition? = null

    fun anyOf(vararg routes: Route<*>): EdgeMatcher = EdgeMatcher.AnyOf(routes.toSet())

    fun destination(route: Route<*>) {
        routes += route
    }

    fun edge(
        from: EdgeMatcher,
        to: EdgeMatcher,
        transition: Transition,
        onInterrupt: Interrupt = Interrupt.Queue,
        allowIf: EdgeGuard? = null,
    ): Edge = Edge(from, to, transition, onInterrupt, allowIf).also { edges += it }

    /** Adds the way back as well: the opposite edge, playing the transition reversed. The guard is not copied. */
    fun Edge.andBack(): Edge = edge(from = this.to, to = this.from, transition = transition.reversed(), onInterrupt = onInterrupt)

    internal fun build(start: Destination) = NavGraph(start, routes.toList(), edges.toList(), reducedMotion)
}
