package com.cdodi.core.bus

import com.cdodi.core.time.Clock
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The page as the platform reports it. [isFocused]: the page has the keyboard focus (its window is the active one); a
 * visible page can be unfocused, behind another window or with the focus in the address bar.
 */
data class Lifecycle(val isVisible: Boolean = true, val prefersReducedMotion: Boolean = false, val isFocused: Boolean = true) {
    /** In front of the person, and theirs: visible and focused. The rain outside only falls then. */
    val isActive: Boolean get() = isVisible && isFocused
}

sealed interface LifecycleEvent {
    data object Foreground : LifecycleEvent

    data object Background : LifecycleEvent

    data class ReducedMotionChanged(val reduced: Boolean) : LifecycleEvent

    data class FocusChanged(val focused: Boolean) : LifecycleEvent
}

/**
 * Platform adapters (visibility, focus, `prefers-reduced-motion`) send events; the heartbeat, scenes, navigation and
 * sound read the state.
 */
interface LifecycleBus {
    val state: StateFlow<Lifecycle>

    fun send(event: LifecycleEvent)
}

class DefaultLifecycleBus(initial: Lifecycle = Lifecycle()) : LifecycleBus {
    private val mutableState = MutableStateFlow(initial)

    override val state: StateFlow<Lifecycle> = mutableState.asStateFlow()

    override fun send(event: LifecycleEvent) {
        mutableState.update {
            when (event) {
                LifecycleEvent.Foreground -> it.copy(isVisible = true)
                LifecycleEvent.Background -> it.copy(isVisible = false)
                is LifecycleEvent.ReducedMotionChanged -> it.copy(prefersReducedMotion = event.reduced)
                is LifecycleEvent.FocusChanged -> it.copy(isFocused = event.focused)
            }
        }
    }
}

/**
 * Pauses this clock while [condition] holds for the lifecycle, and resumes it when it stops holding:
 *
 * ```
 * heartbeat.pauseWhile(lifecycle) { !it.isVisible }            // hidden tab: everything stops
 * ambient.pauseWhile(lifecycle) { it.prefersReducedMotion }    // rain and fog hold a still frame
 * ```
 *
 * Checked in the Input phase. A frame's time is added before its phases run, so the first frame after the page comes
 * back still sees the pause and time doesn't jump. A pause set by someone else is left alone.
 */
fun Clock.pauseWhile(lifecycle: LifecycleBus, condition: (Lifecycle) -> Boolean): Subscription {
    var pausedHere = false
    return subscribe(FramePhase.Input) {
        val shouldPause = condition(lifecycle.state.value)
        if (shouldPause && !pausedHere && !isPaused) {
            isPaused = true
            pausedHere = true
        } else if (!shouldPause && pausedHere) {
            isPaused = false
            pausedHere = false
        }
    }
}
