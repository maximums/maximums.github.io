package com.cdodi.data.boids

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

typealias Boid = Vec2

/**
 * Basically it is just a `Long`, because [Offset] is also a `value class`
 */
value class Vec2 private constructor (val value: Offset) {
    val magnitude: Float
        get() = hypot(value.x, value.y)

    infix fun distanceTo(other: Vec2) = (this - other).magnitude

    operator fun times(other: Vec2) = (value.x * other.value.x) + (value.y * other.value.y)

    operator fun plus(other: Vec2) = Vec2(x = value.x + other.value.x, y = value.y + other.value.y)

    operator fun minus(other: Vec2) = Vec2(x = value.x - other.value.x, y = value.y - other.value.y)

    companion object {
        operator fun invoke(x: Float, y: Float) = Vec2(value = Offset(x = x, y = y))
    }
}
