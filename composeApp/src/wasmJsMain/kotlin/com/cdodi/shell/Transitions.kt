package com.cdodi.shell

import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.adapters.gpu.effects.GpuEffectIds
import com.cdodi.core.navigation.effect.AnimatedEffect
import com.cdodi.core.navigation.effect.ShaderEffect
import com.cdodi.core.navigation.graph.targetSceneReady
import com.cdodi.core.navigation.signal.Signal
import com.cdodi.core.navigation.transition.Easing
import com.cdodi.core.navigation.transition.after
import com.cdodi.core.navigation.transition.and
import com.cdodi.core.navigation.transition.atLeast
import com.cdodi.core.navigation.transition.plus
import com.cdodi.core.navigation.transition.transition
import com.cdodi.core.navigation.transition.whenever
import com.cdodi.core.navigation.transition.with
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The site's reusable transitions. The effect ids are the ones [com.cdodi.adapters.compose.effects.UiEffects] and
 * [com.cdodi.adapters.gpu.effects.GpuEffects] render.
 */
object Transitions {

    /** The page being left melts away in pixels. */
    val melt = transition(after(1.seconds)) { play(AnimatedEffect(UiEffectIds.PIXEL_MELT)) }

    /** The page being entered fades in. */
    val fade = transition(after(1400.milliseconds, Easing.Settle)) { play(AnimatedEffect(UiEffectIds.FADE)) }

    /**
     * The menu moves between the centre of Home and the top bar. The menu's modifiers still animate on their own when
     * the layout changes (1500 ms, FastOutSlowIn); this segment gives the model the same timing.
     */
    val menuMorph = transition(after(1500.milliseconds, Easing.FastOutSlowIn)) { play(AnimatedEffect(UiEffectIds.MENU_MORPH)) }

    // "Breath on the window" (DESIGN.md §9), without the per-pane parts that need the redesigned menu.

    /** Fog rolls in from the corners over the scene being left. */
    val fogRoll = transition(after(900.milliseconds, Easing.Settle)) { play(ShaderEffect(GpuEffectIds.FOG_ROLL)) }

    /** The fog breathes until the next scene is ready: at least 600 ms, at most 3 s. */
    val holdFog = transition(atLeast(600.milliseconds) and whenever(Signal.targetSceneReady, timeout = 3.seconds)) {
        play(ShaderEffect(GpuEffectIds.FOG_HOLD))
    }

    /** The fog parts from the centre over the scene being entered. */
    val clearing = transition(after(1400.milliseconds, Easing.Settle)) { play(ShaderEffect(GpuEffectIds.FOG_CLEAR)) }

    val pageToPage = (melt with fogRoll) + holdFog + (fade with clearing)

    val openPage = (menuMorph with fogRoll) + holdFog + (fade with clearing)

    /** DESIGN.md: with reduced motion, transitions become a plain 400 ms cross-fade. */
    val reducedMotion = transition(after(400.milliseconds)) { play(AnimatedEffect(UiEffectIds.FADE)) }
}
