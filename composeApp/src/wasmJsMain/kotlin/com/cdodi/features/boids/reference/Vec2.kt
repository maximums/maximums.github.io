package com.cdodi.features.boids.reference

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

/** A 2D vector. An [Offset] underneath, which is a value class over one `Long`, so a `Vec2` allocates nothing. */
value class Vec2 private constructor(val value: Offset) {
    val x: Float get() = value.x
    val y: Float get() = value.y

    val magnitude: Float get() = sqrt(x * x + y * y)

    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)

    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)

    operator fun div(scalar: Float) = Vec2(x / scalar, y / scalar)

    operator fun unaryMinus() = Vec2(-x, -y)

    infix fun dot(other: Vec2) = x * other.x + y * other.y

    /** The same direction with length 1; zero stays zero. */
    val normalized: Vec2 get() = magnitude.let { if (it > 0f) this / it else Zero }

    /** No longer than [max]. */
    fun limited(max: Float): Vec2 = magnitude.let { if (it > max) this * (max / it) else this }

    /** This difference across a world that wraps around: the shortest way, possibly over an edge. */
    fun wrappedDelta(world: Vec2) = Vec2(x - world.x * round(x / world.x), y - world.y * round(y / world.y))

    /** This position folded back into the world. */
    fun wrappedInto(world: Vec2) = Vec2(x - world.x * floor(x / world.x), y - world.y * floor(y / world.y))

    override fun toString() = "($x, $y)"

    companion object {
        operator fun invoke(x: Float, y: Float) = Vec2(Offset(x, y))

        val Zero = Vec2(0f, 0f)
    }
}
