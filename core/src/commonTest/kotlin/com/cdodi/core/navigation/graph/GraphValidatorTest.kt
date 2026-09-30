package com.cdodi.core.navigation.graph

import com.cdodi.core.navigation.About
import com.cdodi.core.navigation.Boids
import com.cdodi.core.navigation.Elsewhere
import com.cdodi.core.navigation.Home
import com.cdodi.core.navigation.Life
import com.cdodi.core.navigation.effect.AnimatedEffect
import com.cdodi.core.navigation.effect.EffectRegistry
import com.cdodi.core.navigation.effect.ShaderEffect
import com.cdodi.core.navigation.transition.after
import com.cdodi.core.navigation.transition.plus
import com.cdodi.core.navigation.transition.reversed
import com.cdodi.core.navigation.transition.transition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GraphValidatorTest {

    private val melt = transition(after(1.seconds)) { play(AnimatedEffect("melt")) }
    private val fog = transition(after(1.seconds)) { play(ShaderEffect("fog")) }

    private fun problemsOf(block: () -> Unit): List<GraphProblem> = assertFailsWith<InvalidNavGraphException> { block() }.problems

    @Test
    fun exactBeatsAnyOfWhichBeatsAny() {
        lateinit var exact: Edge
        lateinit var some: Edge
        lateinit var all: Edge
        val graph = navGraph(start = Home) {
            destination(Home); destination(About); destination(Boids)
            all = edge(from = any, to = any, transition = melt)
            some = edge(from = any, to = anyOf(About, Boids), transition = melt)
            exact = edge(from = any, to = Boids, transition = fog)
        }

        assertSame(exact, graph.edgeFor(Home, Boids))
        assertSame(some, graph.edgeFor(Home, About))
        assertSame(all, graph.edgeFor(About, Home))
    }

    @Test
    fun edgesThatNeitherDominatesAreAmbiguous() {
        lateinit var fromHome: Edge
        lateinit var toBoids: Edge

        val problems = problemsOf {
            navGraph(start = Home) {
                destination(Home); destination(Boids)
                fromHome = edge(from = Home, to = any, transition = melt)
                toBoids = edge(from = any, to = Boids, transition = fog)
            }
        }

        assertEquals(listOf(GraphProblem.Ambiguous(Home, Boids, listOf(fromHome, toBoids))), problems)
    }

    @Test
    fun anEdgeMoreSpecificThanBothResolvesTheAmbiguity() {
        lateinit var direct: Edge
        val graph = navGraph(start = Home) {
            destination(Home); destination(Boids)
            edge(from = Home, to = any, transition = melt)
            edge(from = any, to = Boids, transition = fog)
            direct = edge(from = Home, to = Boids, transition = fog)
        }

        assertSame(direct, graph.edgeFor(Home, Boids))
    }

    @Test
    fun everyStructuralProblemIsReportedAtOnce() {
        lateinit var toNowhere: Edge

        val problems = problemsOf {
            navGraph(start = Life()) {
                destination(Home); destination(About); destination(Boids)
                destination(object : SingletonDestination("home") {})
                destination(object : SingletonDestination("not a path") {})
                toNowhere = edge(from = Home, to = Elsewhere, transition = melt)
            }
        }

        assertTrue(GraphProblem.StartNotRegistered(Life()) in problems)
        assertTrue(GraphProblem.DuplicatePath("home") in problems)
        assertTrue(problems.any { it is GraphProblem.InvalidPath && it.route.path == "not a path" })
        assertTrue(GraphProblem.UnknownRoute(toNowhere, Elsewhere) in problems)
    }

    @Test
    fun everyDestinationMustBeReachableFromTheStart() {
        val problems = problemsOf {
            navGraph(start = Home) {
                destination(Home); destination(About); destination(Boids)
                edge(from = Home, to = About, transition = melt)
                edge(from = Boids, to = Home, transition = melt)
            }
        }

        assertEquals(listOf(GraphProblem.Unreachable(Boids)), problems)
    }

    @Test
    fun andBackAddsTheOppositeEdgePlayingTheTransitionReversed() {
        lateinit var there: Edge
        val graph = navGraph(start = Home) {
            destination(Home); destination(About)
            there = edge(from = Home, to = About, transition = melt, onInterrupt = Interrupt.Reverse).apply { andBack() }
        }

        val back = graph.edgeFor(About, Home)!!
        assertEquals(melt.reversed(), back.transition)
        assertEquals(there.onInterrupt, back.onInterrupt)
    }

    @Test
    fun everyEffectNeedsARenderer() {
        lateinit var toBoids: Edge
        val graph = navGraph(start = Home) {
            destination(Home); destination(Boids)
            edge(from = any, to = any, transition = melt)
            toBoids = edge(from = any, to = Boids, transition = melt + fog)
            reducedMotion = transition(after(1.seconds)) { play(ShaderEffect("cross-fade")) }
        }
        val onlyUi = EffectRegistry { it is AnimatedEffect }

        val problems = assertFailsWith<InvalidNavGraphException> { graph.requireRenderable(onlyUi) }.problems

        assertEquals(
            listOf(GraphProblem.NoRenderer(ShaderEffect("fog"), toBoids), GraphProblem.NoRenderer(ShaderEffect("cross-fade"), edge = null)),
            problems,
        )
        graph.requireRenderable { true }
    }

    @Test
    fun theMessageNamesEveryProblem() {
        val error = assertFailsWith<InvalidNavGraphException> {
            navGraph(start = Home) {
                destination(Home); destination(Boids)
            }
        }

        assertEquals("Invalid navigation graph:\n  - no edges lead from the start to \"boids\"", error.message)
    }
}
