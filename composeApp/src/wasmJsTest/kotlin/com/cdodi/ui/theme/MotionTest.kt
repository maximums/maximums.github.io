package com.cdodi.ui.theme

import com.cdodi.core.navigation.transition.Easing
import kotlin.test.Test
import kotlin.test.assertEquals

class MotionTest {

    @Test
    fun theUiSettlesAsTheTransitionsDo() {
        // DESIGN.md's Settle twice: Compose's, for the UI, and the core's, for transitions. ui can't import the core,
        // so this keeps the two in step.
        for (step in 0..20) {
            val t = step / 20f
            assertEquals(Easing.Settle.transform(t), Motion.Settle.transform(t), 0.002f, "at $t")
        }
    }
}
