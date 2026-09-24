package com.cdodi.core.time

import kotlin.test.Test
import kotlin.test.assertEquals

class FixedStepperTest {

    @Test
    fun runsOneStepPerWholeStepOfTime() {
        val stepper = FixedStepper(step = 0.01f)
        var steps = 0

        repeat(10) { stepper.advance(0.025f) { steps++ } }

        assertEquals(25, steps)
    }

    @Test
    fun alphaIsTheFractionOfTheNextStep() {
        val stepper = FixedStepper(step = 0.1f)
        stepper.advance(0.25f) {}

        assertEquals(0.5f, stepper.alpha, 1e-4f)
    }

    @Test
    fun aHugeFrameIsCappedAndItsBacklogDropped() {
        val stepper = FixedStepper(step = 0.01f, maxStepsPerFrame = 4)

        val first = stepper.advance(1.005f) {}
        val second = stepper.advance(0f) {}

        assertEquals(4, first)
        assertEquals(0, second) // the rest of the second was dropped, not carried over
        assertEquals(0.5f, stepper.alpha, 1e-3f)
    }
}
