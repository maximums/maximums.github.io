package com.cdodi.features.home

import com.cdodi.math.Bounds
import com.cdodi.math.Mat4
import com.cdodi.math.Vec3
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * How the tree is seen. The camera stands on a circle around the tree's vertical axis, [yaw] radians round from its
 * front (+Z), a little above it ([pitch]), looking at its middle. It stands far enough back that the tree fits the
 * target at every angle, so turning it never zooms it or cuts it off: whatever can turn into view lies in a cylinder
 * round that axis, as wide as the box's diagonal across and as tall as the box, and the camera fits that cylinder.
 * [nearest] and [farthest] are the view depths of the tree's front and back, for the mist.
 */
internal class BonsaiView private constructor(
    val viewProjection: Mat4,
    val view: Mat4,
    val eye: Vec3,
    val nearest: Float,
    val farthest: Float,
) {
    companion object {
        /** The vertical field of view: a long lens, so the tree isn't stretched at the sides. */
        const val FOV_Y = 0.5f

        /** A little above the tree, as a bonsai on a table is seen. */
        const val PITCH = 0.12f

        /** A little room around the tree. */
        private const val MARGIN = 1.03f

        fun of(bounds: Bounds, aspect: Float, yaw: Float, pitch: Float = PITCH): BonsaiView {
            val centre = bounds.center
            val size = bounds.size
            val reach = sqrt(size.x * size.x + size.z * size.z) / 2
            val halfHeight = size.y / 2
            val t = tan(FOV_Y / 2)
            val halfWidth = atan(t * aspect)
            val up = sin(pitch)
            val level = cos(pitch)
            // Seen from a little above, the cylinder's near rim reaches lowest at its bottom edge and highest at its top
            // edge: each must stay inside the field of view at its own depth. Across, its outline is a cone round it.
            val forBottom = (reach * up + halfHeight * level) / t + reach * level - halfHeight * up
            val forTop = (halfHeight * level - reach * up) / t + reach * level + halfHeight * up
            val across = reach / sin(halfWidth) + halfHeight * up
            val distance = maxOf(forBottom, forTop, across) * MARGIN
            val eye = centre + Vec3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch)) * distance
            val view = Mat4.lookAt(eye, centre)
            val nearest = distance - bounds.radius
            val farthest = distance + bounds.radius
            val projection = Mat4.perspective(FOV_Y, aspect, nearest * 0.9f, farthest * 1.1f)
            return BonsaiView(projection * view, view, eye, nearest, farthest)
        }
    }
}
