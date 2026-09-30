package com.cdodi.components

import com.cdodi.adapters.compose.effects.Morph

/**
 * A value that follows a target by a [Morph] instead of its own clock. When the target changes it starts from
 * wherever it is *now*, so a morph interrupted halfway goes on from where it visibly is; it holds while the morph is
 * waiting and snaps to the target once everything has settled.
 */
internal class ProgressTween<T : Any>(private val lerp: (from: T, to: T, fraction: Float) -> T) {
    private var from: T? = null
    private var to: T? = null
    private var current: T? = null

    /** The value for this frame. Safe to call several times per frame with the same arguments. */
    fun update(target: T, morph: Morph): T {
        if (target != to) {
            from = current ?: target
            to = target
        }
        val value = when (morph) {
            Morph.Settled -> target
            Morph.Waiting -> current ?: target
            is Morph.Moving -> lerp(from ?: target, target, morph.fraction.coerceIn(0f, 1f))
        }
        current = value
        return value
    }
}
