package com.cdodi.math

import kotlin.math.PI
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuatTest {

    private val quarterTurn = (PI / 2).toFloat()

    @Test
    fun rotationsAreRightHanded() {
        // A quarter turn about +Y takes +X to −Z, and about +Z takes +X to +Y.
        assertClose(-Vec3.Z, Quat.axisAngle(Vec3.Y, quarterTurn).rotate(Vec3.X))
        assertClose(Vec3.Y, Quat.axisAngle(Vec3.Z, quarterTurn).rotate(Vec3.X))
        assertClose(Vec3(1f, 2f, 3f), Quat.IDENTITY.rotate(Vec3(1f, 2f, 3f)))
    }

    @Test
    fun aProductAppliesTheRightHandFirst() {
        val aboutY = Quat.axisAngle(Vec3.Y, quarterTurn)
        val aboutZ = Quat.axisAngle(Vec3.Z, quarterTurn)
        val v = Vec3(1f, 2f, 3f)
        assertClose(aboutY.rotate(aboutZ.rotate(v)), (aboutY * aboutZ).rotate(v))
        assertClose(aboutZ.rotate(aboutY.rotate(v)), (aboutZ * aboutY).rotate(v))
    }

    @Test
    fun theMatrixTurnsLikeTheQuaternion() {
        val random = Random(958)
        repeat(30) {
            val q = Quat(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalized()
            val v = Vec3(random.nextFloat(), random.nextFloat(), random.nextFloat())
            assertClose(q.rotate(v), Mat4.rotation(q).transformPoint(v))
        }
    }

    @Test
    fun normalizingKeepsTheRotation() {
        val q = Quat(0f, 2f, 0f, 2f).normalized()
        assertEquals(1f, q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w, 1e-6f)
        assertClose(Quat.axisAngle(Vec3.Y, quarterTurn).rotate(Vec3.X), q.rotate(Vec3.X))
        assertFailsWith<IllegalArgumentException> { Quat(0f, 0f, 0f, 0f).normalized() }
    }

    private fun assertClose(expected: Vec3, actual: Vec3, tolerance: Float = 1e-5f) {
        assertTrue((expected - actual).length <= tolerance, "expected $expected, was $actual")
    }
}
