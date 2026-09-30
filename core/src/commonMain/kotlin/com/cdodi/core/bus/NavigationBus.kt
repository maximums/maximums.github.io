package com.cdodi.core.bus

import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.Navigator
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.time.Clock
import com.cdodi.core.time.FramePhase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow

sealed interface NavIntent {
    data class NavigateTo(val destination: Destination) : NavIntent

    data object Back : NavIntent

    data object Forward : NavIntent
}

/** Navigation as the UI sees it: intents go in from click handlers and the browser, state comes out. */
interface NavigationBus {
    val state: StateFlow<NavState>

    /** Never suspends and launches nothing; the intent is applied in the next Navigation phase. */
    fun send(intent: NavIntent)
}

/**
 * Runs a [Navigator] on the heartbeat. In every Navigation phase, the running transition advances by [clock]'s time,
 * then the intents sent since the last one are applied in order. The frame's time belongs to what played during it,
 * so a transition an intent starts shows its first frame at zero, and an interruption turns it around where it
 * visibly was. Every navigation change happens at the same point of a frame, whoever asked for it, and transitions
 * follow the clock's pause and time scale.
 */
class DefaultNavigationBus(private val navigator: Navigator, clock: Clock) : NavigationBus, AutoCloseable {

    private val intents = Channel<NavIntent>(Channel.UNLIMITED)
    private val subscription = clock.subscribe(FramePhase.Navigation) { frame ->
        navigator.advance(frame.dt.toDouble())
        applyIntents()
    }

    override val state: StateFlow<NavState> get() = navigator.state

    override fun send(intent: NavIntent) {
        intents.trySend(intent)
    }

    private fun applyIntents() {
        while (true) {
            when (val intent = intents.tryReceive().getOrNull() ?: return) {
                is NavIntent.NavigateTo -> navigator.navigate(intent.destination)
                NavIntent.Back -> navigator.back()
                NavIntent.Forward -> navigator.forward()
            }
        }
    }

    override fun close() {
        subscription.close()
        intents.close()
    }
}
