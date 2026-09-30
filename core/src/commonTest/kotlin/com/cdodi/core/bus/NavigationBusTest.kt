package com.cdodi.core.bus

import com.cdodi.core.navigation.About
import com.cdodi.core.navigation.BackStack
import com.cdodi.core.navigation.Home
import com.cdodi.core.navigation.Life
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.Navigator
import com.cdodi.core.navigation.effect.AnimatedEffect
import com.cdodi.core.navigation.graph.navGraph
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.navigation.transition.after
import com.cdodi.core.navigation.transition.transition
import com.cdodi.core.time.DefaultHeartbeat
import com.cdodi.core.time.FramePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class NavigationBusTest {

    private val heartbeat = DefaultHeartbeat()
    private val transitions = heartbeat.child("transitions")
    private val graph = navGraph(start = Home) {
        destination(Home); destination(About); destination(Life)
        edge(from = any, to = any, transition = transition(after(1.seconds)) { play(AnimatedEffect("melt")) })
    }
    private val bus = DefaultNavigationBus(Navigator(graph, MutableSignals()), transitions)
    private var now = 0L

    private fun frame(seconds: Double = 0.1) {
        now += (seconds * 1e9).toLong()
        heartbeat.tick(now)
    }

    private val progress get() = assertIs<NavState.Transitioning>(bus.state.value).effects.single().progress!!

    init {
        frame() // the first tick only sets where time starts
    }

    @Test
    fun intentsWaitForTheNextNavigationPhaseAndApplyInOrder() {
        bus.send(NavIntent.NavigateTo(About))
        bus.send(NavIntent.NavigateTo(Life()))
        bus.send(NavIntent.Back)
        assertEquals(NavState.Idle(Home, BackStack(listOf(Home))), bus.state.value, "nothing happens between frames")

        frame()

        assertEquals(BackStack(listOf(Home, About, Life()), index = 1), bus.state.value.history)
    }

    @Test
    fun aNewTransitionShowsItsFirstFrameAtZero() {
        bus.send(NavIntent.NavigateTo(About))

        frame(0.1)
        assertEquals(0f, progress)
        frame(0.1)
        frame(0.05)
        assertEquals(0.15f, progress, 1e-4f)
    }

    @Test
    fun transitionsFollowTheirClock() {
        bus.send(NavIntent.NavigateTo(About))
        frame()

        transitions.timeScale = 0.5f
        frame(0.1)
        assertEquals(0.05f, progress, 1e-4f)

        transitions.isPaused = true
        frame(0.1)
        assertEquals(0.05f, progress, 1e-4f)
    }

    @Test
    fun anIntentSentEarlierInTheFrameAppliesInThatFrame() {
        heartbeat.subscribe(FramePhase.Input) { if (it.index == 2L) bus.send(NavIntent.NavigateTo(About)) }

        frame()

        assertIs<NavState.Transitioning>(bus.state.value)
    }

    @Test
    fun aClosedBusIgnoresIntents() {
        bus.close()

        bus.send(NavIntent.NavigateTo(About))
        frame()

        assertIs<NavState.Idle>(bus.state.value)
    }
}
