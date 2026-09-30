package com.cdodi.core.navigation

import com.cdodi.core.navigation.NavResult.Reason
import com.cdodi.core.navigation.effect.AnimatedEffect
import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.effect.Layer
import com.cdodi.core.navigation.effect.ShaderEffect
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.navigation.graph.Interrupt
import com.cdodi.core.navigation.graph.NavGraphBuilder
import com.cdodi.core.navigation.graph.navGraph
import com.cdodi.core.navigation.graph.sceneReady
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.navigation.signal.Signal
import com.cdodi.core.navigation.transition.Transition
import com.cdodi.core.navigation.transition.after
import com.cdodi.core.navigation.transition.plus
import com.cdodi.core.navigation.transition.transition
import com.cdodi.core.navigation.transition.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NavigatorTest {

    private val signals = MutableSignals()
    private val melt = transition(after(1.seconds)) { play(ShaderEffect("melt", Layer.Ui)) }
    private val morph = transition(after(2.seconds)) { play(AnimatedEffect("morph")) }
    private val fog = transition(whenever(Signal.sceneReady(Boids), timeout = 3.seconds)) { play(ShaderEffect("fog")) }

    /** The site's shape: a melt anywhere, the menu morph between Home and its pages, fog into Boids. */
    private fun site(intoBoids: Transition = fog) = navGraph(start = Home) {
        destination(Home); destination(About); destination(Life); destination(Boids)
        edge(from = any, to = any, transition = melt)
        edge(from = Home, to = anyOf(About, Life), transition = morph).andBack()
        edge(from = any, to = Boids, transition = intoBoids, onInterrupt = Interrupt.Reverse)
    }

    /** Only the melt, interrupted with [policy]. */
    private fun meltEverywhere(policy: Interrupt, more: NavGraphBuilder.() -> Unit = {}) = navGraph(start = Home) {
        destination(Home); destination(About); destination(Life)
        edge(from = any, to = any, transition = melt, onInterrupt = policy)
        more()
    }

    private val Navigator.transitioning get() = assertIs<NavState.Transitioning>(state.value)

    private val Navigator.effect: EffectState get() = transitioning.effects.single()

    private fun Navigator.assertIdleAt(destination: Destination) = assertEquals(destination, assertIs<NavState.Idle>(state.value).at)

    @Test
    fun startsIdleAtTheStart() {
        val navigator = Navigator(site(), signals)

        assertEquals(NavState.Idle(Home, BackStack(listOf(Home))), navigator.state.value)
    }

    @Test
    fun playsTheMostSpecificEdge() {
        val navigator = Navigator(site(), signals)

        navigator.navigate(About)
        assertEquals("morph", navigator.effect.effect.id)
        navigator.advance(2.0)

        navigator.navigate(Life())
        assertEquals("melt", navigator.effect.effect.id)
        navigator.advance(1.0)

        navigator.navigate(Boids)
        assertEquals("fog", navigator.effect.effect.id)
    }

    @Test
    fun arrivesWhenTheTransitionCompletes() {
        val navigator = Navigator(site(), signals)

        navigator.navigate(About)
        assertEquals(0f, navigator.effect.progress, "effects show from the first frame")
        navigator.advance(1.0)
        assertEquals(0.5f, navigator.transitioning.progress)
        navigator.advance(1.0)

        navigator.assertIdleAt(About)
    }

    @Test
    fun aConditionBasedEdgeWaitsForTheScene() {
        val navigator = Navigator(site(), signals)

        navigator.navigate(Boids)
        navigator.advance(2.0)
        assertNull(navigator.effect.progress, "the fog breathes while it waits")
        assertNull(navigator.transitioning.progress)

        signals.raise(Signal.sceneReady(Boids))
        navigator.advance(0.016)
        navigator.assertIdleAt(Boids)
    }

    @Test
    fun aConditionBasedEdgeGivesUpAtItsTimeout() {
        val navigator = Navigator(site(), signals)

        navigator.navigate(Boids)
        navigator.advance(2.9)
        assertIs<NavState.Transitioning>(navigator.state.value)
        navigator.advance(0.1)

        navigator.assertIdleAt(Boids)
    }

    @Test
    fun aQueuedNavigationStartsWithTheTimeLeftOver() {
        val navigator = Navigator(meltEverywhere(Interrupt.Queue), signals)
        navigator.navigate(About)
        navigator.advance(0.6)

        assertEquals(NavResult.Accepted, navigator.navigate(Life()))
        assertEquals(About, navigator.transitioning.to, "the running transition finishes first")

        navigator.advance(0.6) // 0.4 to finish, 0.2 into the next
        assertEquals(About, navigator.transitioning.from)
        assertEquals(Life(), navigator.transitioning.to)
        assertEquals(0.2f, navigator.effect.progress!!, 1e-4f)
    }

    @Test
    fun replaceSkipsToTheDestinationAndGoesOn() {
        val navigator = Navigator(meltEverywhere(Interrupt.Replace), signals)
        navigator.navigate(About)
        navigator.advance(0.6)

        navigator.navigate(Life())

        assertEquals(About, navigator.transitioning.from)
        assertEquals(Life(), navigator.transitioning.to)
        assertEquals(0f, navigator.effect.progress)
    }

    @Test
    fun reverseTurnsTheTransitionAroundWhereItIs() {
        val navigator = Navigator(meltEverywhere(Interrupt.Reverse), signals)
        navigator.navigate(About)
        navigator.advance(0.75)

        navigator.back()

        assertEquals(About, navigator.transitioning.from)
        assertEquals(Home, navigator.transitioning.to)
        assertEquals(EffectState(ShaderEffect("melt", Layer.Ui), progress = 0.75f, elapsed = 0.25, reversed = true), navigator.effect)
        assertEquals(0.25f, navigator.effect.travelled!!, 1e-4f)

        navigator.advance(0.75)
        navigator.assertIdleAt(Home)
    }

    @Test
    fun reversingTowardsAThirdDestinationGoesBackFirst() {
        val navigator = Navigator(meltEverywhere(Interrupt.Reverse), signals)
        navigator.navigate(About)
        navigator.advance(0.75)

        navigator.navigate(Life())
        assertEquals(Home, navigator.transitioning.to)

        navigator.advance(1.0) // 0.75 back to Home, then 0.25 towards Life
        assertEquals(Home, navigator.transitioning.from)
        assertEquals(Life(), navigator.transitioning.to)
        assertEquals(0.25f, navigator.effect.progress!!, 1e-4f)
    }

    @Test
    fun reversingTwiceHeadsForTheOriginalTargetFromWhereItIs() {
        val navigator = Navigator(meltEverywhere(Interrupt.Reverse), signals)
        navigator.navigate(About)
        navigator.advance(0.75)
        navigator.back()
        navigator.advance(0.25)

        navigator.forward()

        assertEquals(Home, navigator.transitioning.from)
        assertEquals(About, navigator.transitioning.to)
        assertEquals(0.5f, navigator.effect.progress!!, 1e-4f)
        assertEquals(false, navigator.effect.reversed)
        navigator.advance(0.5)
        navigator.assertIdleAt(About)
    }

    @Test
    fun interruptingACompositePlaysBackTheSegmentsThatRan() {
        val navigator = Navigator(site(intoBoids = morph + fog), signals)
        navigator.navigate(Boids)
        navigator.advance(2.5) // the morph is done, the fog has waited half a second

        navigator.back()

        assertEquals("fog", navigator.effect.effect.id)
        navigator.advance(0.5) // the fog plays back over the time it waited
        assertEquals(EffectState(AnimatedEffect("morph"), progress = 1f, elapsed = 0.0, reversed = true), navigator.effect)
        navigator.advance(1.0)
        assertEquals(0.5f, navigator.effect.progress!!, 1e-4f)
        navigator.advance(1.0)
        navigator.assertIdleAt(Home)
    }

    @Test
    fun overallProgressIsExactForATimeOnlyComposite() {
        val navigator = Navigator(site(intoBoids = morph + melt), signals)
        navigator.navigate(Boids)

        navigator.advance(1.5)

        assertEquals(0.5f, navigator.transitioning.progress!!, 1e-4f)
        assertEquals("morph", navigator.effect.effect.id)
    }

    @Test
    fun backAndForwardWalkTheHistory() {
        val navigator = Navigator(site(), signals)
        navigator.navigate(About)
        navigator.advance(2.0)

        navigator.back()
        assertEquals(EffectState(AnimatedEffect("morph"), progress = 1f, elapsed = 0.0, reversed = true), navigator.effect, "the way back plays the morph reversed")
        navigator.advance(2.0)
        navigator.assertIdleAt(Home)
        assertEquals(BackStack(listOf(Home, About), index = 0), navigator.state.value.history)

        navigator.forward()
        navigator.advance(2.0)
        navigator.assertIdleAt(About)
    }

    @Test
    fun navigatingDropsTheEntriesAhead() {
        val navigator = Navigator(site(), signals)
        navigator.navigate(About)
        navigator.advance(2.0)
        navigator.back()
        navigator.advance(2.0)

        navigator.navigate(Life())

        assertEquals(BackStack(listOf(Home, Life())), navigator.state.value.history)
        assertTrue(!navigator.state.value.history.canGoForward)
    }

    @Test
    fun aRefusedNavigationChangesNothing() {
        var webGpu = false
        val graph = navGraph(start = Home) {
            destination(Home); destination(About); destination(Life); destination(Boids)
            edge(from = Home, to = anyOf(About, Life), transition = melt, onInterrupt = Interrupt.Queue)
            edge(from = any, to = Home, transition = melt)
            edge(from = Home, to = Boids, transition = melt, allowIf = { _, _ -> webGpu })
        }
        val navigator = Navigator(graph, signals)

        assertEquals(NavResult.AlreadyThere, navigator.navigate(Home))
        assertEquals(NavResult.Refused(Reason.EndOfHistory), navigator.back())
        assertEquals(NavResult.Refused(Reason.UnknownDestination), navigator.navigate(Elsewhere))
        assertEquals(NavResult.Refused(Reason.Guard), navigator.navigate(Boids))
        assertEquals(NavState.Idle(Home, BackStack(listOf(Home))), navigator.state.value)

        webGpu = true
        assertEquals(NavResult.Accepted, navigator.navigate(Boids))
    }

    @Test
    fun aQueuedNavigationIsCheckedFromWhereTheScreenWillBe() {
        val graph = navGraph(start = Home) {
            destination(Home); destination(About); destination(Life)
            edge(from = Home, to = anyOf(About, Life), transition = melt, onInterrupt = Interrupt.Queue)
            edge(from = any, to = Home, transition = melt)
        }
        val navigator = Navigator(graph, signals)
        navigator.navigate(About)

        // Home → Life exists, but the screen will be at About, and About → Life doesn't.
        assertEquals(NavResult.Refused(Reason.NoEdge), navigator.navigate(Life()))
        assertEquals(BackStack(listOf(Home, About)), navigator.state.value.history)
    }

    @Test
    fun changingOnlyTheArgumentsNeedsNoEdge() {
        val graph = navGraph(start = Life()) {
            destination(Life); destination(Home)
            edge(from = Life, to = Home, transition = melt).andBack()
        }
        val navigator = Navigator(graph, signals)

        navigator.navigate(Life("B36/S23"))

        navigator.assertIdleAt(Life("B36/S23"))
    }

    @Test
    fun reducedMotionPlaysTheGraphsPlainTransitionInstead() {
        var reduced = true
        val graph = navGraph(start = Home) {
            destination(Home); destination(About)
            edge(from = any, to = any, transition = melt)
            reducedMotion = transition(after(400.milliseconds)) { play(AnimatedEffect("cross-fade")) }
        }
        val navigator = Navigator(graph, signals, reducedMotion = { reduced })

        navigator.navigate(About)
        assertEquals("cross-fade", navigator.effect.effect.id)
        navigator.advance(0.4)
        navigator.assertIdleAt(About)

        reduced = false
        navigator.navigate(Home)
        assertEquals("melt", navigator.effect.effect.id)
    }

    @Test
    fun aDeepLinkStartsSomewhereElse() {
        val navigator = Navigator(site(), signals, start = Life("B36/S23"))

        navigator.assertIdleAt(Life("B36/S23"))
        navigator.navigate(Home)
        assertEquals("morph", navigator.effect.effect.id)
    }
}
