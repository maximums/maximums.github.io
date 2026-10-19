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
import com.cdodi.core.navigation.transition.delayed
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
     * The menu moves between the centre of Home and the top bar: its words fly and scale, and the rhombus becomes the
     * page's glass, each following this segment's progress ([LocalMorph][com.cdodi.adapters.compose.effects.LocalMorph]).
     */
    val menuMorph = transition(after(1500.milliseconds, Easing.FastOutSlowIn)) { play(AnimatedEffect(UiEffectIds.MENU_MORPH)) }

    // "Breath on the window" (DESIGN.md §9). The GPU draws the fog; Compose plays the parts on the menu and the bar.

    /** Fog rolls in from the corners over the scene being left. */
    val fogRoll = transition(after(900.milliseconds, Easing.Settle)) { play(ShaderEffect(GpuEffectIds.FOG_ROLL)) }

    /** The fog breathes until the next scene is ready: at least 600 ms, at most 3 s. */
    val holdFog = transition(atLeast(600.milliseconds) and whenever(Signal.targetSceneReady, timeout = 3.seconds)) {
        play(ShaderEffect(GpuEffectIds.FOG_HOLD))
    }

    /** The fog parts from the centre over the scene being entered. */
    val clearing = transition(after(1400.milliseconds, Easing.Settle)) { play(ShaderEffect(GpuEffectIds.FOG_CLEAR)) }

    /** The top bar condenses onto the glass, from blurred to sharp, half a second into the clearing. */
    val condense = transition(after(900.milliseconds, Easing.Settle)) { play(AnimatedEffect(UiEffectIds.CONDENSE)) }.delayed(500.milliseconds)

    /** 1: a band of clarity wipes across the chosen pane, the others fog over, its word catches the lamp. */
    val wipe = transition(after(900.milliseconds, Easing.Settle)) {
        play(AnimatedEffect(UiEffectIds.FOG_WIPE))
        play(AnimatedEffect(UiEffectIds.FOG_THICKEN))
        play(AnimatedEffect(UiEffectIds.LABEL_GLOW))
    }

    /** 2: the window loosens and sinks back into the weather as fog banks roll in from the corners. */
    val dissolve = transition(after(1100.milliseconds, Easing.Settle)) {
        play(AnimatedEffect(UiEffectIds.MENU_MORPH))
        play(ShaderEffect(GpuEffectIds.FOG_ROLL))
    }

    /** 3: one line of verse fades in while the fog breathes and the next scene gathers itself. */
    val caption = transition(after(600.milliseconds, Easing.Settle)) { play(AnimatedEffect(UiEffectIds.CAPTION)) }

    val pageToPage = (melt with fogRoll) + holdFog + (fade with clearing)

    val openPage = (menuMorph with fogRoll) + holdFog + (fade with clearing with condense)

    /**
     * Home to a scene (DESIGN.md §9): wipe, dissolve, hold, clear. About 3.4 s when the scene is quick, 5.4 s at most;
     * going back plays it reversed: the page evaporates off the glass, the fog returns, the window re-forms.
     */
    val breathOnTheWindow = wipe + dissolve + (holdFog with caption) + (fade with clearing with condense)

    /** DESIGN.md: with reduced motion, transitions become a plain 400 ms cross-fade. */
    val reducedMotion = transition(after(400.milliseconds)) { play(AnimatedEffect(UiEffectIds.FADE)) }
}
