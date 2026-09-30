package com.cdodi.data

import com.cdodi.core.time.Clock
import com.cdodi.core.time.FixedStepper
import com.cdodi.core.time.FramePhase

private const val FIXED_STEP = 0.0166f

/**
 * A simulation stepped at a fixed rate on [clock], in the Simulation phase of every frame. Pausing or slowing the
 * clock pauses or slows the simulation, and nothing else.
 */
abstract class Manager(clock: Clock) : AutoCloseable {
    private val stepper = FixedStepper(FIXED_STEP)
    private val subscription = clock.subscribe(FramePhase.Simulation) { frame -> stepper.advance(frame.dt, ::loop) }

    protected abstract fun loop(timeStep: Float)

    override fun close() = subscription.close()
}
