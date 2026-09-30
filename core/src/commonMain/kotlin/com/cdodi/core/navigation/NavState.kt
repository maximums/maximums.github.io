package com.cdodi.core.navigation

import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.graph.Destination

/**
 * The navigation history, like the browser's: the destinations asked for, and where in them we are. It records
 * *intents*; it changes as soon as a navigation is accepted, while the screen catches up through transitions.
 */
data class BackStack(val entries: List<Destination>, val index: Int = entries.lastIndex) {
    init {
        require(index in entries.indices) { "index $index is outside the ${entries.size} entries" }
    }

    val current: Destination get() = entries[index]
    val canGoBack: Boolean get() = index > 0
    val canGoForward: Boolean get() = index < entries.lastIndex

    /** Adds [destination] after the current entry, dropping the entries that were ahead of it. */
    fun push(destination: Destination): BackStack = BackStack(entries.take(index + 1) + destination)

    fun back(): BackStack = copy(index = index - 1)

    fun forward(): BackStack = copy(index = index + 1)
}

/** What the screen should show, published every frame something changes. */
sealed interface NavState {
    val history: BackStack

    data class Idle(val at: Destination, override val history: BackStack) : NavState

    /**
     * Going from [from] to [to], in the direction of travel: after a reversal they swap, and the effects play
     * backwards (see [EffectState.travelled]).
     *
     * @param effects what each effect of the running segments should show.
     * @param progress overall progress, while only time decides when the transition ends; else null.
     */
    data class Transitioning(
        val from: Destination,
        val to: Destination,
        val effects: List<EffectState>,
        val progress: Float?,
        override val history: BackStack,
    ) : NavState
}

sealed interface NavResult {
    /** The history changed; the screen follows through transitions. */
    data object Accepted : NavResult

    /** The destination is already the current one; nothing changed. */
    data object AlreadyThere : NavResult

    /** Nothing changed, for this [reason]. */
    data class Refused(val reason: Reason) : NavResult

    enum class Reason {
        /** The destination's route is not in the graph. */
        UnknownDestination,

        /** No edge leads there from where the screen will be. */
        NoEdge,

        /** The edge's guard said no. */
        Guard,

        /** There is no entry to go back or forward to. */
        EndOfHistory,
    }
}
