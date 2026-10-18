package com.cdodi.features.boids

import com.cdodi.core.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the Boids page shows and sets. */
data class BoidsStatus(
    val count: Int = BoidsController.DEFAULT_COUNT,
    val isPaused: Boolean = false,
    val params: BoidsParams = BoidsParams(),
    /** The grid of the last step, across and down. */
    val grid: Pair<Int, Int> = 0 to 0,
)

/**
 * Between the Boids page and its GPU scene: the page sets the rule weights, the perception radius, the speed and the
 * flock size; the scene applies them in its next frame. Play and pause are the boids clock's pause.
 */
class BoidsController(private val clock: Clock, initialCount: Int = DEFAULT_COUNT) {

    private val mutableStatus = MutableStateFlow(BoidsStatus(isPaused = clock.isPaused, count = initialCount))
    val status: StateFlow<BoidsStatus> = mutableStatus.asStateFlow()

    internal var scatterRequested = false

    fun setParams(change: (BoidsParams) -> BoidsParams) = mutableStatus.update { it.copy(params = change(it.params)) }

    fun setCount(count: Int) {
        mutableStatus.update { it.copy(count = count.coerceIn(MIN_COUNT, MAX_COUNT)) }
        scatterRequested = true
    }

    fun playPause() {
        clock.isPaused = !clock.isPaused
        mutableStatus.update { it.copy(isPaused = clock.isPaused) }
    }

    /** A new flock, scattered at random. */
    fun scatter() {
        scatterRequested = true
    }

    internal fun reportGrid(grid: Pair<Int, Int>) {
        if (status.value.grid != grid) mutableStatus.update { it.copy(grid = grid) }
    }

    companion object {
        /** The boids clock's name: the flock runs on it and nothing else does. */
        const val CLOCK = "boids"

        const val DEFAULT_COUNT = 32_768

        /** A phone's first flock: its GPU and its screen are both smaller. */
        const val SMALL_SCREEN_COUNT = 8_192
        const val MIN_COUNT = 1_024
        const val MAX_COUNT = 1_048_576
    }
}
