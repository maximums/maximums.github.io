package com.cdodi.features.home

import com.cdodi.math.Bounds
import com.cdodi.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class BonsaiViewTest {

    /** The maple's bounds, as the shipped file reads. */
    private val maple = Bounds(Vec3(-0.010303685f, 0.0008210861f, -0.0071108025f), Vec3(0.016768588f, 0.03071015f, 0.009606925f))

    private fun corners(b: Bounds) = (0 until 8).map { i ->
        Vec3(if (i and 1 == 0) b.min.x else b.max.x, if (i and 2 == 0) b.min.y else b.max.y, if (i and 4 == 0) b.min.z else b.max.z)
    }

    @Test
    fun theTreeFitsAtEveryAngleAndShape() {
        for (aspect in listOf(0.6f, 0.87f, 1f, 1.6f)) {
            for (step in 0 until 24) {
                val yaw = (step * PI / 12).toFloat()
                val view = BonsaiView.of(maple, aspect, yaw)
                for (corner in corners(maple)) {
                    val p = view.viewProjection.transformPoint(corner)
                    assertTrue(abs(p.x) <= 1f && abs(p.y) <= 1f, "aspect $aspect, yaw $yaw: $corner lands at $p")
                    assertTrue(p.z in 0f..1f, "aspect $aspect, yaw $yaw: $corner is outside the depth range ($p)")
                }
            }
        }
    }

    @Test
    fun turningKeepsTheTreeTheSameSize() {
        // The camera's distance doesn't depend on the angle: the tree never zooms as it turns.
        val distances = (0 until 12).map { (BonsaiView.of(maple, 0.87f, (it * PI / 6).toFloat()).eye - maple.center).length }
        assertTrue(distances.all { abs(it - distances[0]) < 1e-6f }, "$distances")
    }

    @Test
    fun theFrontIsPlusZAndTheCameraIsALittleAbove() {
        val centre = maple.center
        val front = BonsaiView.of(maple, 1f, 0f)
        assertTrue(front.eye.z > centre.z && abs(front.eye.x - centre.x) < 1e-6f, "${front.eye}")
        assertTrue(front.eye.y > centre.y, "above")
        val side = BonsaiView.of(maple, 1f, (PI / 2).toFloat())
        assertTrue(side.eye.x > centre.x, "a quarter turn puts the camera on the tree's right")
        val middle = front.viewProjection.transformPoint(centre)
        assertTrue(abs(middle.x) < 1e-5f && abs(middle.y) < 1e-5f, "it looks at the middle: $middle")
        assertTrue(front.nearest > 0f && front.nearest < front.farthest)
    }
}
