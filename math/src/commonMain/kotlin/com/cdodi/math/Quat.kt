package com.cdodi.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A rotation, as a unit quaternion. The components are in glTF's order, the vector part first: (x, y, z, w). Rotations
 * are right-handed: a positive angle about an axis turns anticlockwise when the axis points at the viewer.
 */
data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {

    /** The rotation that applies [other] first, then this one. */
    operator fun times(other: Quat) = Quat(
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
        w * other.w - x * other.x - y * other.y - z * other.z,
    )

    /** [v] turned by this rotation. */
    fun rotate(v: Vec3): Vec3 {
        // v + 2w (u × v) + 2 u × (u × v), with u the vector part: the sandwich product q v q* without building q*.
        val u = Vec3(x, y, z)
        val t = (u cross v) * 2f
        return v + t * w + (u cross t)
    }

    /** The same rotation at length one: quaternions drift from it as they're multiplied, and files round them. */
    fun normalized(): Quat {
        val length = sqrt(x * x + y * y + z * z + w * w)
        require(length > 0f) { "A zero quaternion is no rotation" }
        return Quat(x / length, y / length, z / length, w / length)
    }

    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)

        /** A turn of [radians] about [axis] (which needn't be unit length). */
        fun axisAngle(axis: Vec3, radians: Float): Quat {
            val a = axis.normalized() * sin(radians / 2)
            return Quat(a.x, a.y, a.z, cos(radians / 2))
        }
    }
}
