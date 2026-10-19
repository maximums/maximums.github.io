package com.cdodi.shell

import androidx.compose.runtime.State
import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.adapters.compose.effects.morph
import com.cdodi.adapters.compose.effects.progressOf
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.home.HomeDestination
import com.cdodi.features.life.LifeDestination

/**
 * What "Breath on the window" does to Home's panes now: the pane for the scene being opened (or returned from) is
 * wiped, the others thicken, its word glows. Each follows its effect's progress, so going back fogs them over again.
 */
internal fun State<NavState>.paneEffects(): PaneEffects {
    fun chosen(item: MenuItem): Boolean {
        val nav = value as? NavState.Transitioning ?: return false
        return (nav.from == HomeDestination && item.isAt(nav.to)) || (nav.to == HomeDestination && item.isAt(nav.from))
    }
    return PaneEffects(
        wipe = { item -> if (chosen(item)) value.progressOf(UiEffectIds.FOG_WIPE) ?: 0f else 0f },
        thicken = { item -> if (chosen(item)) 0f else value.progressOf(UiEffectIds.FOG_THICKEN) ?: 0f },
        glow = { item -> if (chosen(item)) value.progressOf(UiEffectIds.LABEL_GLOW) ?: 0f else 0f },
    )
}

/**
 * How condensed the top bar is: following `condense` when the transition plays it; before it has started, still
 * dissolved if the transition left Home; solid otherwise (one page to another).
 */
internal fun NavState.barSolidity(): Float = when (this) {
    is NavState.Idle -> 1f
    is NavState.Transitioning -> progressOf(UiEffectIds.CONDENSE) ?: if (from == HomeDestination) 0f else 1f
}

/** How visible the caption is: as `caption` has shown it, and fading out as the page arrives (`fade`). */
internal fun NavState.captionAlpha(): Float {
    val shown = progressOf(UiEffectIds.CAPTION) ?: return 0f
    val arrived = (this as? NavState.Transitioning)?.effects?.firstOrNull { it.effect.id == UiEffectIds.FADE }?.travelled ?: 0f
    return shown * (1f - arrived)
}

/** The line of verse while the fog holds on the way to a scene (DESIGN.md §10: placeholder copy). */
internal fun captionFor(nav: NavState): String? {
    if (nav !is NavState.Transitioning) return null
    val scene = if (nav.from == HomeDestination) nav.to else if (nav.to == HomeDestination) nav.from else return null
    return when (scene) {
        BoidsDestination -> "the flock is gathering"
        is LifeDestination -> "the embers are catching"
        else -> null
    }
}

/** Which side of the menu morph the layout is on. */
internal fun NavState.layoutAt(): Destination = when (this) {
    is NavState.Idle -> at
    is NavState.Transitioning -> if (effects.any { it.effect.id == UiEffectIds.MENU_MORPH }) to else from
}

/** Where navigation is going: the top bar's thread moves at the click. */
internal fun NavState.target(): Destination = when (this) {
    is NavState.Idle -> at
    is NavState.Transitioning -> to
}

/** Whether a transition is leaving or entering [destination]. */
internal fun NavState.involves(destination: Destination): Boolean =
    this is NavState.Transitioning && (from == destination || to == destination)

/** 1 on Home, 0 elsewhere, and in between while the menu morphs (DESIGN.md §9: the window dissolves, the bar condenses). */
internal fun NavState.homeness(): Float = when (this) {
    is NavState.Idle -> if (at == HomeDestination) 1f else 0f
    is NavState.Transitioning -> {
        val travelled = effects.firstOrNull { it.effect.id == UiEffectIds.MENU_MORPH }?.travelled
        when {
            from == HomeDestination && to != HomeDestination -> 1f - (travelled ?: 0f)
            to == HomeDestination && from != HomeDestination -> travelled ?: 0f
            else -> if (from == HomeDestination) 1f else 0f
        }
    }
}
