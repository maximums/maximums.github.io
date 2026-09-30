package com.cdodi.shell

import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.core.navigation.effect.AnimatedEffect
import com.cdodi.core.navigation.transition.Easing
import com.cdodi.core.navigation.transition.after
import com.cdodi.core.navigation.transition.transition
import com.cdodi.core.navigation.transition.with
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The site's reusable transitions. The effect ids are the ones [com.cdodi.adapters.compose.effects.UiEffects] renders. */
object Transitions {

    /** The page being left melts away in pixels. */
    val melt = transition(after(1.seconds)) { play(AnimatedEffect(UiEffectIds.PIXEL_MELT)) }

    /** The page being entered fades in over the one being left. */
    val fade = transition(after(2.seconds)) { play(AnimatedEffect(UiEffectIds.FADE)) }

    /**
     * The menu moves between the centre of Home and the top bar. The menu's modifiers still animate on their own when
     * the layout changes (1500 ms, FastOutSlowIn); this segment gives the model the same timing. They'll be driven by
     * its progress once the navigation renderer exists (Phase 3).
     */
    val menuMorph = transition(after(1500.milliseconds, Easing.FastOutSlowIn)) { play(AnimatedEffect(UiEffectIds.MENU_MORPH)) }

    val pageToPage = melt with fade

    val openPage = menuMorph with fade

    /** DESIGN.md: with reduced motion, transitions become a plain 400 ms cross-fade. */
    val reducedMotion = transition(after(400.milliseconds)) { play(AnimatedEffect(UiEffectIds.FADE)) }
}
