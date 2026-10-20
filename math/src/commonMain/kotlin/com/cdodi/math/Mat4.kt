package com.cdodi.math

import kotlin.math.abs
import kotlin.math.tan

/**
 * A 4×4 matrix, column-major: the element at row `r` and column `c` is `values[c * 4 + r]`. That's glTF's `matrix`
 * layout and WGSL's `mat4x4f`, so a matrix is read from a file and written to a buffer as it is. Points are columns,
 * so `a * b` applies `b` first.
 */
class Mat4(val values: FloatArray) {

    init {
        require(values.size == 16) { "A 4×4 matrix has 16 values, not ${values.size}" }
    }

    operator fun get(row: Int, column: Int): Float = values[column * 4 + row]

    operator fun times(other: Mat4): Mat4 {
        val a = values
        val b = other.values
        val out = FloatArray(16)
        for (c in 0 until 4) {
            for (r in 0 until 4) {
                out[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3]
            }
        }
        return Mat4(out)
    }

    /** [p] as a point (w = 1), divided by the w it comes out with: so projections work too. */
    fun transformPoint(p: Vec3): Vec3 {
        val m = values
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        val w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        return if (w == 1f) Vec3(x, y, z) else Vec3(x / w, y / w, z / w)
    }

    /** [d] as a direction (w = 0): turned and scaled, not moved. */
    fun transformDirection(d: Vec3): Vec3 {
        val m = values
        return Vec3(m[0] * d.x + m[4] * d.y + m[8] * d.z, m[1] * d.x + m[5] * d.y + m[9] * d.z, m[2] * d.x + m[6] * d.y + m[10] * d.z)
    }

    fun transposed(): Mat4 = Mat4(FloatArray(16) { i -> values[(i % 4) * 4 + i / 4] })

    val determinant: Float
        get() {
            val c = cofactors()
            return values[0] * c[0] + values[1] * c[4] + values[2] * c[8] + values[3] * c[12]
        }

    /** The matrix that undoes this one. A singular matrix (one that flattens space) has none, so it's refused. */
    fun inverse(): Mat4 {
        val c = cofactors()
        val det = values[0] * c[0] + values[1] * c[4] + values[2] * c[8] + values[3] * c[12]
        require(abs(det) > 1e-12f) { "A singular matrix has no inverse" }
        return Mat4(FloatArray(16) { c[it] / det })
    }

    /** True if every element is within [tolerance] of [other]'s. */
    fun isCloseTo(other: Mat4, tolerance: Float = 1e-5f): Boolean = values.indices.all { abs(values[it] - other.values[it]) <= tolerance }

    override fun equals(other: Any?): Boolean = other is Mat4 && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String = (0 until 4).joinToString(prefix = "Mat4(", postfix = ")", separator = " | ") { r ->
        (0 until 4).joinToString(" ") { c -> this[r, c].toString() }
    }

    /**
     * The adjugate, column-major: the inverse times the determinant. Written out (the classic expansion in 2×2 minors)
     * rather than looped, as every renderer's matrix library does.
     */
    private fun cofactors(): FloatArray {
        val m = values
        val out = FloatArray(16)
        out[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        out[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        out[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        out[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        out[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        out[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        out[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        out[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        out[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        out[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        out[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        out[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        out[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        out[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        out[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        out[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        return out
    }

    companion object {
        val IDENTITY = Mat4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))

        fun translation(t: Vec3) = Mat4(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, t.x, t.y, t.z, 1f))

        fun scale(s: Vec3) = Mat4(floatArrayOf(s.x, 0f, 0f, 0f, 0f, s.y, 0f, 0f, 0f, 0f, s.z, 0f, 0f, 0f, 0f, 1f))

        fun rotation(q: Quat): Mat4 = trs(Vec3.ZERO, q, Vec3.ONE)

        /**
         * Translation × rotation × scale, the order glTF composes a node's `translation`, `rotation` and `scale` in:
         * scaled first, then turned, then moved. Built directly rather than by multiplying three matrices.
         */
        fun trs(t: Vec3, r: Quat, s: Vec3): Mat4 {
            val (x, y, z, w) = r
            return Mat4(
                floatArrayOf(
                    (1 - 2 * (y * y + z * z)) * s.x, 2 * (x * y + z * w) * s.x, 2 * (x * z - y * w) * s.x, 0f,
                    2 * (x * y - z * w) * s.y, (1 - 2 * (x * x + z * z)) * s.y, 2 * (y * z + x * w) * s.y, 0f,
                    2 * (x * z + y * w) * s.z, 2 * (y * z - x * w) * s.z, (1 - 2 * (x * x + y * y)) * s.z, 0f,
                    t.x, t.y, t.z, 1f,
                )
            )
        }

        /**
         * A perspective projection for WebGPU: right-handed, the camera looking down −Z with +Y up (as in glTF), and
         * depth from 0 at [near] to 1 at [far] (WebGPU's clip space, not OpenGL's −1 to 1). [fovY] is the vertical field
         * of view in radians; [aspect] is width over height.
         */
        fun perspective(fovY: Float, aspect: Float, near: Float, far: Float): Mat4 {
            require(near > 0f && far > near) { "The depth range must be 0 < near < far, not $near..$far" }
            val f = 1f / tan(fovY / 2)
            val depth = 1f / (near - far)
            return Mat4(floatArrayOf(f / aspect, 0f, 0f, 0f, 0f, f, 0f, 0f, 0f, 0f, far * depth, -1f, 0f, 0f, near * far * depth, 0f))
        }

        /**
         * The view matrix of a camera at [eye] looking at [target]: it moves the world so the camera sits at the origin
         * looking down −Z, with [up] as near to +Y as the direction allows.
         */
        fun lookAt(eye: Vec3, target: Vec3, up: Vec3 = Vec3.Y): Mat4 {
            val forward = (target - eye).normalized()
            val side = (forward cross up).normalized()
            val upward = side cross forward
            return Mat4(
                floatArrayOf(
                    side.x, upward.x, -forward.x, 0f,
                    side.y, upward.y, -forward.y, 0f,
                    side.z, upward.z, -forward.z, 0f,
                    -(side dot eye), -(upward dot eye), forward dot eye, 1f,
                )
            )
        }
    }
}
