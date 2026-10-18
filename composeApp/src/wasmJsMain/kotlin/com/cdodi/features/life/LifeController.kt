package com.cdodi.features.life

import com.cdodi.core.time.Clock
import com.cdodi.data.gameoflife.LifeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the Life page shows. */
data class LifeStatus(
    val isPaused: Boolean = false,
    val generationsPerSecond: Float = LifeController.DEFAULT_SPEED,
    val wrap: Boolean = false,
    val rule: LifeRule = LifeRule.Conway,
    val generation: Long = 0,
    val population: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * Between the Life page and its GPU scene: the page asks, the scene carries it out in its next frame and reports back
 * through [status]. Play and pause are just the life clock's pause, so pausing stops the simulation and nothing else.
 */
class LifeController(private val clock: Clock) {

    private val mutableStatus = MutableStateFlow(LifeStatus(isPaused = clock.isPaused))
    val status: StateFlow<LifeStatus> = mutableStatus.asStateFlow()

    internal var stepsRequested = 0
    internal var clearRequested = false
    internal var scatterRequested = false
    internal var fitRequested = false

    val rule: LifeRule get() = status.value.rule

    fun setRule(rule: LifeRule) = mutableStatus.update { it.copy(rule = rule) }

    fun setWrap(wrap: Boolean) = mutableStatus.update { it.copy(wrap = wrap) }

    fun setSpeed(generationsPerSecond: Float) = mutableStatus.update { it.copy(generationsPerSecond = generationsPerSecond.coerceIn(MIN_SPEED, MAX_SPEED)) }

    fun playPause() {
        clock.isPaused = !clock.isPaused
        mutableStatus.update { it.copy(isPaused = clock.isPaused) }
    }

    /** One generation, while paused. */
    fun step() {
        stepsRequested++
    }

    fun clear() {
        clearRequested = true
    }

    /** A random soup in the middle of the grid. */
    fun scatter() {
        scatterRequested = true
    }

    /** Back to the starting zoom, centred. */
    fun fitView() {
        fitRequested = true
    }

    internal fun report(generation: Long, population: Int, width: Int, height: Int) {
        val current = status.value
        if (current.generation != generation || current.population != population || current.width != width || current.height != height) {
            mutableStatus.update { it.copy(generation = generation, population = population, width = width, height = height) }
        }
    }

    companion object {
        /** The life clock's name: the simulation runs on it, on the GPU or the CPU, and nothing else does. */
        const val CLOCK = "life"

        const val DEFAULT_SPEED = 12f
        const val MIN_SPEED = 1f
        const val MAX_SPEED = 60f
    }
}
