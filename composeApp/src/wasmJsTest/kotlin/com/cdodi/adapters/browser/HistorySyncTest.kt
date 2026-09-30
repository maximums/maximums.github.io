package com.cdodi.adapters.browser

import com.cdodi.core.bus.DefaultNavigationBus
import com.cdodi.core.bus.NavIntent
import com.cdodi.core.navigation.Navigator
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.navigation.graph.SingletonDestination
import com.cdodi.core.navigation.graph.navGraph
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.navigation.transition.Transition
import com.cdodi.core.time.DefaultHeartbeat
import com.cdodi.core.time.FramePhase
import kotlin.test.Test
import kotlin.test.assertEquals

private data object Home : SingletonDestination("home")
private data object About : SingletonDestination("about")
private data object Boids : SingletonDestination("boids")

class HistorySyncTest {

    /** What the browser was told, as the calls it received. */
    private class FakeBrowser : HistoryPort {
        val calls = mutableListOf<String>()
        override fun push(index: Int, url: String) { calls += "push $index $url" }
        override fun replace(index: Int, url: String) { calls += "replace $index $url" }
        override fun go(delta: Int) { calls += "go $delta" }
    }

    private var boidsAllowed = true
    private val graph = navGraph(start = Home) {
        destination(Home); destination(About); destination(Boids)
        edge(from = any, to = any, transition = Transition.None)
        edge(from = any, to = Boids, transition = Transition.None, allowIf = { _, _ -> boidsAllowed })
    }
    private val heartbeat = DefaultHeartbeat()
    private val navigation = DefaultNavigationBus(Navigator(graph, MutableSignals()), heartbeat.child("transitions"))
    private val browser = FakeBrowser()
    private val sync = HistorySync(browser, navigation, urlOf = { "#/$it" }, destinationAt = { url -> graph.route(url.removePrefix("#/")) as? Destination ?: Home })
    private var now = 0L

    init {
        heartbeat.subscribe(FramePhase.Ui) { sync.sync() }
    }

    private fun frame() {
        now += 16_000_000
        heartbeat.tick(now)
    }

    private val current get() = navigation.state.value.history

    @Test
    fun theFirstEntryIsTaggedWithTheStart() {
        assertEquals(listOf("replace 0 #/Home"), browser.calls)
    }

    @Test
    fun navigatingPushesAnEntry() {
        navigation.send(NavIntent.NavigateTo(About))
        frame()

        assertEquals(listOf("replace 0 #/Home", "push 1 #/About"), browser.calls)
    }

    @Test
    fun theBrowsersBackButtonGoesBackWithoutFurtherCalls() {
        navigation.send(NavIntent.NavigateTo(About))
        frame()
        browser.calls.clear()

        sync.onPop(state = 0, url = "#/Home")
        frame()

        assertEquals(Home, current.current)
        assertEquals(emptyList(), browser.calls)
    }

    @Test
    fun jumpingSeveralEntriesSendsThatManySteps() {
        navigation.send(NavIntent.NavigateTo(About))
        frame()
        navigation.send(NavIntent.NavigateTo(Boids))
        frame()

        sync.onPop(state = 0, url = "#/Home")
        frame()

        assertEquals(0, current.index)
    }

    @Test
    fun aBackFromTheUiMovesTheBrowserAndIgnoresItsEcho() {
        navigation.send(NavIntent.NavigateTo(About))
        frame()
        browser.calls.clear()

        navigation.send(NavIntent.Back)
        frame()
        sync.onPop(state = 0, url = "#/Home") // the browser reporting the go(-1) it was told to do
        frame()

        assertEquals(listOf("go -1"), browser.calls)
        assertEquals(0, current.index)
    }

    @Test
    fun aBrowserMoveThatNavigationRefusesIsUndone() {
        navigation.send(NavIntent.NavigateTo(Boids))
        frame()
        navigation.send(NavIntent.Back)
        frame()
        sync.onPop(state = 0, url = "#/Home") // echo of the UI back
        browser.calls.clear()
        boidsAllowed = false

        sync.onPop(state = 1, url = "#/Boids") // the browser's forward button
        frame()

        assertEquals(0, current.index)
        assertEquals(listOf("go -1"), browser.calls)
    }

    @Test
    fun aUrlTypedByHandNavigatesAndTagsTheEntryTheBrowserMade() {
        sync.onPop(state = null, url = "#/about")
        frame()

        assertEquals(About, current.current)
        assertEquals(listOf("replace 0 #/Home", "replace 1 #/About"), browser.calls)
    }
}
