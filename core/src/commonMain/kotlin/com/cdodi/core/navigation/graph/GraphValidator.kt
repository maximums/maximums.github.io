package com.cdodi.core.navigation.graph

import com.cdodi.core.navigation.effect.Effect
import com.cdodi.core.navigation.effect.EffectRegistry
import com.cdodi.core.navigation.transition.Transition
import com.cdodi.core.navigation.transition.allEffects

/** Something wrong with a graph, found at startup rather than halfway through a transition. */
sealed interface GraphProblem {
    val message: String

    data class StartNotRegistered(val start: Destination) : GraphProblem {
        override val message: String get() = "the start destination $start is not registered"
    }

    data class InvalidPath(val route: Route<*>) : GraphProblem {
        override val message: String
            get() = "the path \"${route.path}\" must be non-empty and use only letters, digits, '-', '_', '.', '~' and '/'"
    }

    data class DuplicatePath(val path: String) : GraphProblem {
        override val message: String get() = "more than one destination is registered at \"$path\""
    }

    data class UnknownRoute(val edge: Edge, val route: Route<*>) : GraphProblem {
        override val message: String get() = "the edge $edge names \"${route.path}\", which is not registered"
    }

    data class Unreachable(val route: Route<*>) : GraphProblem {
        override val message: String get() = "no edges lead from the start to \"${route.path}\""
    }

    /** No edge dominates the others, e.g. `Home → any` and `any → Boids` when going from Home to Boids. */
    data class Ambiguous(val from: Route<*>, val to: Route<*>, val edges: List<Edge>) : GraphProblem {
        override val message: String
            get() = "going from $from to $to could take any of ${edges.joinToString()}; add an edge that is more specific than all of them"
    }

    /** @param edge null for the graph's reduced-motion transition. */
    data class NoRenderer(val effect: Effect, val edge: Edge?) : GraphProblem {
        override val message: String
            get() = "${edge?.let { "the edge $it" } ?: "the reduced-motion transition"} plays the ${effect.layer} effect \"${effect.id}\", which nothing renders"
    }
}

class InvalidNavGraphException(val problems: List<GraphProblem>) :
    IllegalStateException(problems.joinToString("\n", prefix = "Invalid navigation graph:\n") { "  - ${it.message}" })

object GraphValidator {

    private val validPath = Regex("[A-Za-z0-9._~-]+(/[A-Za-z0-9._~-]+)*")

    /** Problems in the graph itself: registrations, paths, reachability and ambiguous edges. */
    fun structure(graph: NavGraph): List<GraphProblem> = buildList {
        if (graph.start.route !in graph) add(GraphProblem.StartNotRegistered(graph.start))

        graph.routes.filterNot { validPath.matches(it.path) }.forEach { add(GraphProblem.InvalidPath(it)) }
        graph.routes.groupBy { it.path }.filterValues { it.size > 1 }.keys.forEach { add(GraphProblem.DuplicatePath(it)) }

        for (edge in graph.edges) {
            (edge.from.namedRoutes + edge.to.namedRoutes).filterNot { it in graph }.forEach { add(GraphProblem.UnknownRoute(edge, it)) }
        }

        if (graph.start.route in graph) {
            val reached = reachable(graph)
            graph.routes.filterNot { it in reached }.forEach { add(GraphProblem.Unreachable(it)) }
        }

        // One problem per set of conflicting edges, with the first pair of routes where they conflict as the example.
        // A singleton never navigates to itself, so its pair with itself can't be ambiguous in practice.
        val ambiguities = graph.routes.flatMap { from ->
            graph.routes.filterNot { to -> to == from && to is SingletonDestination }.mapNotNull { to ->
                graph.candidates(from, to).takeIf { it.size > 1 }?.let { GraphProblem.Ambiguous(from, to, it) }
            }
        }
        addAll(ambiguities.distinctBy { it.edges.toSet() })
    }

    /** Effects that no registered renderer can draw. */
    fun effects(graph: NavGraph, registry: EffectRegistry): List<GraphProblem> {
        fun check(transition: Transition?, edge: Edge?) =
            transition?.allEffects().orEmpty().distinct().filterNot(registry::canRender).map { GraphProblem.NoRenderer(it, edge) }
        return graph.edges.flatMap { check(it.transition, it) } + check(graph.reducedMotion, edge = null)
    }

    private fun reachable(graph: NavGraph): Set<Route<*>> {
        val reached = mutableSetOf<Route<*>>(graph.start.route)
        val pending = ArrayDeque(reached)
        while (pending.isNotEmpty()) {
            val from = pending.removeFirst()
            for (edge in graph.edges.filter { it.from.matches(from) }) {
                graph.routes.filter { edge.to.matches(it) && reached.add(it) }.forEach(pending::addLast)
            }
        }
        return reached
    }
}

/** Checks that every effect on every edge has a renderer; call once the adapters have registered theirs. */
fun NavGraph.requireRenderable(registry: EffectRegistry) {
    val problems = GraphValidator.effects(this, registry)
    if (problems.isNotEmpty()) throw InvalidNavGraphException(problems)
}
