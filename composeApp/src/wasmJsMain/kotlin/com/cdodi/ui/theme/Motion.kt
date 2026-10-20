package com.cdodi.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** DESIGN.md §7: heavy, not fast. Motion settles the way water does. */
object Motion {
    /** `Settle`: cubic-bezier(.22, .61, .36, 1). */
    val Settle = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    /** Hover, focus and state changes: lamp light rising, fog clearing, a thread growing. */
    const val WARM_SECONDS = 1.3f

    /** A knob or a thread that moves a short way. */
    const val SHORT_SECONDS = 0.9f
}

/**
 * The clock UI motion runs on: hovers, focus, toggles. A port, which the app gives its own clock to (the heartbeat), so
 * every animation keeps step with everything else it animates (DESIGN.md §7).
 */
fun interface MotionClock {
    /** Calls [listener] every frame with the seconds since the last one, until the returned handle is closed. */
    fun everyFrame(listener: (seconds: Float) -> Unit): AutoCloseable
}

/** The app's [MotionClock]. */
val LocalMotionClock = staticCompositionLocalOf<MotionClock> { error("No MotionClock: the app provides one (LocalMotionClock)") }

/**
 * A value that settles towards [target] over [seconds] with [Motion.Settle], on the app's [MotionClock]. A new target
 * starts from wherever the value is, so a hover that ends halfway cools from halfway. Read it while drawing to change
 * only the drawing.
 */
@Composable
fun settling(target: Float, seconds: Float = Motion.WARM_SECONDS): State<Float> {
    val clock = LocalMotionClock.current
    val animation = remember(clock) { Settling(target) }
    animation.retarget(target)
    DisposableEffect(animation, seconds) {
        val frames = clock.everyFrame { dt -> animation.advance(dt, seconds) }
        onDispose(frames::close)
    }
    return animation.value
}

/** `if (on) 1 else 0`, settling. */
@Composable
fun settling(on: Boolean, seconds: Float = Motion.WARM_SECONDS): State<Float> = settling(if (on) 1f else 0f, seconds)

private class Settling(initial: Float) {
    val value = mutableFloatStateOf(initial)
    private var from = initial
    private var to = initial
    private var elapsed = 0f

    fun retarget(target: Float) {
        if (target == to) return
        from = value.floatValue
        to = target
        elapsed = 0f
    }

    fun advance(dt: Float, seconds: Float) {
        if (value.floatValue == to) return
        elapsed += dt
        val t = (elapsed / seconds).coerceIn(0f, 1f)
        value.floatValue = if (t >= 1f) to else from + (to - from) * Motion.Settle.transform(t)
    }
}
