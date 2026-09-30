package com.cdodi.core.navigation.transition

import kotlin.math.abs

/** Maps linear progress `[0, 1]` to eased progress. Framework-free, so the navigation model doesn't need Compose. */
fun interface Easing {
    fun transform(fraction: Float): Float

    companion object {
        val Linear = Easing { it }

        /** DESIGN.md's `Settle`: movement settles the way water does. */
        val Settle = CubicBezier(0.22f, 0.61f, 0.36f, 1f)

        val FastOutSlowIn = CubicBezier(0.4f, 0f, 0.2f, 1f)
    }
}

/** A CSS-style cubic Bézier easing from (0, 0) to (1, 1) with control points ([x1], [y1]) and ([x2], [y2]). */
class CubicBezier(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) : Easing {
    init {
        require(x1 in 0f..1f && x2 in 0f..1f) { "x control points must be in [0, 1]" }
    }

    override fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f

        // Solve x(t) = fraction for t by bisection (x is monotonic for x1, x2 in [0, 1]), then return y(t).
        var low = 0f
        var high = 1f
        var t = fraction
        repeat(32) {
            val x = bezier(t, x1, x2)
            if (abs(x - fraction) < 1e-6f) return bezier(t, y1, y2)
            if (x < fraction) low = t else high = t
            t = (low + high) / 2f
        }
        return bezier(t, y1, y2)
    }

    private fun bezier(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
    }
}
