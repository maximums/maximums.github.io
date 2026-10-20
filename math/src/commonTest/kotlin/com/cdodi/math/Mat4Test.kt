package com.cdodi.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Mat4Test {

    private val random = Random(1964)

    private fun randomTrs(): Mat4 = Mat4.trs(
        Vec3(random.nextFloat() * 10 - 5, random.nextFloat() * 10 - 5, random.nextFloat() * 10 - 5),
        Quat(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalized(),
        Vec3(0.2f + random.nextFloat() * 3, 0.2f + random.nextFloat() * 3, 0.2f + random.nextFloat() * 3),
    )

    @Test
    fun theLayoutIsGltfsColumnMajorOne() {
        // glTF's own example: a translation's x, y and z are the matrix's 13th to 15th values.
        val m = Mat4.translation(Vec3(-2f, 3f, 5f))
        assertEquals(listOf(-2f, 3f, 5f), m.values.slice(12..14))
        assertEquals(-2f, m[0, 3], "row 0, column 3")
        assertEquals(Vec3(-1f, 4f, 6f), m.transformPoint(Vec3.ONE))
        assertEquals(Vec3.ONE, m.transformDirection(Vec3.ONE), "directions aren't moved")
    }

    @Test
    fun productsApplyTheRightHandFirst() {
        val move = Mat4.translation(Vec3.X)
        val double = Mat4.scale(Vec3(2f, 2f, 2f))
        assertEquals(Vec3(3f, 2f, 2f), (move * double).transformPoint(Vec3.ONE), "scaled, then moved")
        assertEquals(Vec3(4f, 2f, 2f), (double * move).transformPoint(Vec3.ONE), "moved, then scaled")
        val a = randomTrs()
        val b = randomTrs()
        val c = randomTrs()
        assertTrue(((a * b) * c).isCloseTo(a * (b * c), 1e-3f), "associative")
        assertEquals(a, a * Mat4.IDENTITY)
    }

    @Test
    fun trsIsTranslationTimesRotationTimesScale() {
        repeat(20) {
            val t = Vec3(random.nextFloat(), random.nextFloat(), random.nextFloat())
            val r = Quat(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()).normalized()
            val s = Vec3(random.nextFloat() + 0.5f, random.nextFloat() + 0.5f, random.nextFloat() + 0.5f)
            assertTrue(Mat4.trs(t, r, s).isCloseTo(Mat4.translation(t) * Mat4.rotation(r) * Mat4.scale(s)))
        }
    }

    @Test
    fun theInverseUndoesTheMatrix() {
        repeat(50) {
            val m = randomTrs() * randomTrs()
            assertTrue((m * m.inverse()).isCloseTo(Mat4.IDENTITY, 1e-4f), "$m")
            assertTrue((m.inverse() * m).isCloseTo(Mat4.IDENTITY, 1e-4f), "$m")
        }
        val projection = Mat4.perspective(1f, 1.5f, 0.1f, 100f)
        assertTrue((projection * projection.inverse()).isCloseTo(Mat4.IDENTITY, 1e-4f), "a projection too")
        assertFailsWith<IllegalArgumentException> { Mat4.scale(Vec3(1f, 0f, 1f)).inverse() }
    }

    @Test
    fun theDeterminantIsTheVolumeScale() {
        assertEquals(24f, Mat4.scale(Vec3(2f, 3f, 4f)).determinant, 1e-5f)
        assertEquals(-1f, Mat4.scale(Vec3(-1f, 1f, 1f)).determinant, 1e-6f, "a mirror turns space inside out")
        assertEquals(1f, Mat4.rotation(Quat.axisAngle(Vec3(1f, 2f, 3f), 0.7f)).determinant, 1e-5f)
    }

    @Test
    fun transposingTwiceIsNothing() {
        val m = randomTrs()
        assertEquals(m, m.transposed().transposed())
        assertEquals(m[1, 3], m.transposed()[3, 1])
    }

    @Test
    fun thePerspectiveMapsDepthToWebGpusZeroToOne() {
        val near = 0.5f
        val far = 20f
        val fovY = (PI / 3).toFloat()
        val p = Mat4.perspective(fovY, 2f, near, far)
        assertEquals(0f, p.transformPoint(Vec3(0f, 0f, -near)).z, 1e-6f, "the near plane")
        assertEquals(1f, p.transformPoint(Vec3(0f, 0f, -far)).z, 1e-5f, "the far plane")
        // The top of the field of view lands on the top edge; across, the aspect ratio stretches it.
        val distance = 4f
        val halfHeight = tan(fovY / 2) * distance
        assertEquals(1f, p.transformPoint(Vec3(0f, halfHeight, -distance)).y, 1e-5f)
        assertEquals(1f, p.transformPoint(Vec3(2 * halfHeight, 0f, -distance)).x, 1e-5f)
        assertFailsWith<IllegalArgumentException> { Mat4.perspective(1f, 1f, 0f, 10f) }
    }

    @Test
    fun lookAtPutsTheCameraAtTheOriginLookingDownMinusZ() {
        val eye = Vec3(3f, 2f, 5f)
        val target = Vec3(0f, 1f, 0f)
        val view = Mat4.lookAt(eye, target)
        assertClose(Vec3.ZERO, view.transformPoint(eye))
        assertClose(Vec3(0f, 0f, -(target - eye).length), view.transformPoint(target))
        val above = view.transformPoint(target + Vec3.Y)
        assertTrue(above.y > 0f && abs(above.x) < 1e-5f, "up stays up: $above")
        assertEquals(1f, view.determinant, 1e-5f, "a view only turns and moves")
    }

    private fun assertClose(expected: Vec3, actual: Vec3, tolerance: Float = 1e-5f) {
        assertTrue((expected - actual).length <= tolerance, "expected $expected, was $actual")
    }
}
