package com.cdodi.core.time

/**
 * Turns variable frame times into fixed simulation steps (the accumulator the old `Manager` had), so a simulation
 * behaves the same at 30, 60 or 144 fps and replays deterministically.
 *
 * @param maxStepsPerFrame caps catch-up work after a slow frame, so one long frame can't cause a spiral of them.
 */
class FixedStepper(val step: Float, private val maxStepsPerFrame: Int = 8) {
    init {
        require(step > 0f) { "step must be > 0, got $step" }
    }

    private var accumulator = 0f

    /** Fraction of the next step already accumulated, in `[0, 1)`: for interpolating what is drawn. */
    val alpha: Float get() = accumulator / step

    /** Adds [dt] seconds and calls [onStep] once per whole step now due. Returns how many steps ran. */
    fun advance(dt: Float, onStep: (step: Float) -> Unit): Int {
        accumulator += dt
        var steps = 0
        while (accumulator >= step && steps < maxStepsPerFrame) {
            onStep(step)
            accumulator -= step
            steps++
        }
        if (steps == maxStepsPerFrame) accumulator %= step // drop the backlog, keep the fraction
        return steps
    }
}
