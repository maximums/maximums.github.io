package com.cdodi.math

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A point or a direction in 3D, in single precision like the GPU's `vec3f`. */
data class Vec3(val x: Float, val y: Float, val z: Float) {

    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)

    operator fun div(scale: Float) = Vec3(x / scale, y / scale, z / scale)

    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    /** The right-handed cross product: X × Y is Z. */
    infix fun cross(other: Vec3) = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

    val length: Float get() = sqrt(this dot this)

    /** The same direction, one long. A zero vector has no direction, so it's refused. */
    fun normalized(): Vec3 {
        val length = length
        require(length > 0f) { "A zero vector has no direction" }
        return this / length
    }

    /** The smaller of each component. */
    fun min(other: Vec3) = Vec3(min(x, other.x), min(y, other.y), min(z, other.z))

    /** The larger of each component. */
    fun max(other: Vec3) = Vec3(max(x, other.x), max(y, other.y), max(z, other.z))

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
        val X = Vec3(1f, 0f, 0f)
        val Y = Vec3(0f, 1f, 0f)
        val Z = Vec3(0f, 0f, 1f)
    }
}
