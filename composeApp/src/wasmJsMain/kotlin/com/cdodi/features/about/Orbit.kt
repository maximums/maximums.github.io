package com.cdodi.features.about

import kotlin.math.exp

/**
 * Which way round the maple is turned. A drag moves where it's going ([turn]); the tree follows on the heartbeat
 * ([step]), easing in with a time constant of [SETTLE] seconds, so it turns as a thing with weight rather than a picture
 * spun. It turns only within [ARC] of its front: the canopy is shallow from the side, as a bonsai's is, shaped to be
 * seen from one side. Where it's left, it stays.
 */
class Orbit {

    /** The angle the tree is drawn at: radians round from its front, positive taking the camera to its right. */
    var yaw = 0f
        private set

    /** Where the drags have sent it. */
    var target = 0f
        private set

    fun turn(radians: Float) {
        target = (target + radians).coerceIn(-ARC, ARC)
    }

    /** Eases [yaw] toward [target] by [dt] seconds; a paused clock (dt 0) holds it. */
    fun step(dt: Float) {
        if (dt <= 0f) return
        yaw += (target - yaw) * (1f - exp(-dt / SETTLE))
    }

    companion object {
        /** About 57° either way. */
        const val ARC = 1f

        /** Most of the way there in half a second, all of it in about one. */
        const val SETTLE = 0.3f
    }
}
