package com.cdodi.adapters.browser

import com.cdodi.core.bus.NavIntent
import com.cdodi.core.bus.NavigationBus
import com.cdodi.core.navigation.BackStack
import com.cdodi.core.navigation.DestinationCodec
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Heartbeat
import com.cdodi.core.time.Subscription
import kotlinx.browser.window
import org.w3c.dom.PopStateEvent
import kotlin.math.abs

/** The part of `window.history` the sync needs, so its logic can be tested with a fake. */
interface HistoryPort {
    fun push(index: Int, url: String)

    fun replace(index: Int, url: String)

    fun go(delta: Int)
}

/**
 * Keeps the browser's history and the navigator's back stack in step, so the browser's back and forward buttons play
 * the configured edges, and every page (with its arguments) is a link.
 *
 * Each browser entry stores the back-stack index it stands for. A `popstate` ([onPop]) becomes that many Back or
 * Forward intents. Once per frame, after navigation has applied its intents, [sync] brings the browser along: a
 * navigation pushes an entry, a back or forward from the UI moves the browser with `history.go`, and a browser move
 * the navigator refused is undone the same way.
 */
class HistorySync(
    private val port: HistoryPort,
    private val navigation: NavigationBus,
    private val urlOf: (Destination) -> String,
    private val destinationAt: (url: String) -> Destination,
) {
    private var synced: BackStack = navigation.state.value.history
    private var browserIndex = synced.index
    private var ignoredPops = 0
    private var adoptTypedUrl = false

    init {
        port.replace(browserIndex, urlOf(synced.current))
    }

    /** The browser moved: [state] is the entry's stored index, or null for an entry it made for a URL typed by hand. */
    fun onPop(state: Int?, url: String) {
        if (ignoredPops > 0) {
            ignoredPops--
            return
        }
        if (state == null) {
            adoptTypedUrl = true
            navigation.send(NavIntent.NavigateTo(destinationAt(url)))
            return
        }
        val delta = state - synced.index
        browserIndex = state
        repeat(abs(delta)) { navigation.send(if (delta < 0) NavIntent.Back else NavIntent.Forward) }
    }

    /** Called once per frame, after the Navigation phase. */
    fun sync() {
        val now = navigation.state.value.history
        when {
            adoptTypedUrl -> port.replace(now.index, urlOf(now.current)) // the browser already made the entry
            now == synced -> go(now.index - browserIndex) // nothing changed, or navigation refused a browser move
            isPush(synced, now) && browserIndex == synced.index -> port.push(now.index, urlOf(now.current))
            now.entries == synced.entries -> go(now.index - browserIndex) // back/forward from the UI
            else -> port.replace(now.index, urlOf(now.current))
        }
        adoptTypedUrl = false
        synced = now
        browserIndex = now.index
    }

    private fun go(delta: Int) {
        if (delta == 0) return
        ignoredPops++
        port.go(delta)
    }

    private fun isPush(before: BackStack, after: BackStack): Boolean =
        after.index == before.index + 1 && after.entries.size == after.index + 1 &&
            after.entries.take(after.index) == before.entries.take(after.index)
}

/** Hash URLs, since GitHub Pages can't serve path-based deep links: the start page is the bare address, others `#/about`. */
class HashUrls(private val codec: DestinationCodec, private val start: Destination) {

    fun urlOf(destination: Destination): String =
        if (destination == start) window.location.pathname + window.location.search else "#/" + codec.encode(destination)

    fun destinationAt(url: String): Destination = codec.decode(url.substringAfter('#', missingDelimiterValue = "").removePrefix("/")) ?: start

    /** Where the page was opened: a deep link, or the start. */
    fun current(): Destination = destinationAt(window.location.hash)
}

private class WindowHistory : HistoryPort {
    override fun push(index: Int, url: String) = window.history.pushState(index.toJsNumber(), "", url)

    override fun replace(index: Int, url: String) = window.history.replaceState(index.toJsNumber(), "", url)

    override fun go(delta: Int) = window.history.go(delta)
}

/** Connects the browser's history to [navigation], syncing in every frame's Ui phase. */
fun followBrowserHistory(navigation: NavigationBus, heartbeat: Heartbeat, urls: HashUrls): Subscription {
    val sync = HistorySync(WindowHistory(), navigation, urls::urlOf, urls::destinationAt)
    window.addEventListener("popstate", { event ->
        val state = event.unsafeCast<PopStateEvent>().state
        sync.onPop(state?.unsafeCast<JsNumber>()?.toInt(), window.location.hash)
    })
    return heartbeat.subscribe(FramePhase.Ui) { sync.sync() }
}
