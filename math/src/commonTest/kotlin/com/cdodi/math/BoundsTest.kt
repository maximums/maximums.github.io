package com.cdodi.math

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BoundsTest {

    private val cube = Bounds(-Vec3.ONE, Vec3.ONE)

    @Test
    fun aBoxKnowsItsMiddleAndReach() {
        val box = Bounds(Vec3(0f, 0f, 0f), Vec3(2f, 4f, 4f))
        assertEquals(Vec3(1f, 2f, 2f), box.center)
        assertEquals(Vec3(2f, 4f, 4f), box.size)
        assertEquals(3f, box.radius, 1e-6f)
        assertFailsWith<IllegalArgumentException> { Bounds(Vec3.ONE, Vec3.ZERO) }
    }

    @Test
    fun aTurnedBoxGrowsToHoldItsCorners() {
        val turned = cube.transformed(Mat4.rotation(Quat.axisAngle(Vec3.Y, (PI / 4).toFloat())))
        assertEquals(sqrt(2f), turned.max.x, 1e-5f)
        assertEquals(1f, turned.max.y, 1e-5f, "turning about Y leaves the height")
        val moved = cube.transformed(Mat4.translation(Vec3(5f, 0f, 0f)))
        assertEquals(Bounds(Vec3(4f, -1f, -1f), Vec3(6f, 1f, 1f)), moved)
    }

    @Test
    fun theBoxAroundPointsAndBoxes() {
        assertEquals(Bounds(Vec3(-1f, 0f, 2f), Vec3(3f, 5f, 2f)), Bounds.around(floatArrayOf(3f, 0f, 2f, -1f, 5f, 2f)))
        assertNull(Bounds.around(FloatArray(0)))
        assertEquals(Bounds(-Vec3.ONE, Vec3(4f, 1f, 1f)), cube union Bounds(Vec3.ZERO, Vec3(4f, 0f, 0f)))
    }
}
