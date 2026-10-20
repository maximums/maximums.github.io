package com.cdodi.math

/** An axis-aligned box from [min] to [max]: what a camera frames, and what a model's positions declare. */
data class Bounds(val min: Vec3, val max: Vec3) {

    init {
        require(min.x <= max.x && min.y <= max.y && min.z <= max.z) { "A box's min must not exceed its max: $min..$max" }
    }

    val center: Vec3 get() = (min + max) / 2f

    val size: Vec3 get() = max - min

    /** Half the diagonal: the radius of the sphere around the box. */
    val radius: Float get() = size.length / 2f

    /** The box around both. */
    infix fun union(other: Bounds) = Bounds(min.min(other.min), max.max(other.max))

    /** The box around this one's eight corners moved by [m]: larger than the shape inside when [m] turns it, never smaller. */
    fun transformed(m: Mat4): Bounds {
        var lo = m.transformPoint(min)
        var hi = lo
        for (i in 1 until 8) {
            val corner = m.transformPoint(Vec3(if (i and 1 == 0) min.x else max.x, if (i and 2 == 0) min.y else max.y, if (i and 4 == 0) min.z else max.z))
            lo = lo.min(corner)
            hi = hi.max(corner)
        }
        return Bounds(lo, hi)
    }

    companion object {
        /** The box around [points], three floats (x, y, z) each; null when there are none. */
        fun around(points: FloatArray): Bounds? {
            if (points.size < 3) return null
            var lo = Vec3(points[0], points[1], points[2])
            var hi = lo
            for (i in 3 until points.size - 2 step 3) {
                val p = Vec3(points[i], points[i + 1], points[i + 2])
                lo = lo.min(p)
                hi = hi.max(p)
            }
            return Bounds(lo, hi)
        }
    }
}
