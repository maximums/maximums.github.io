package com.cdodi.adapters.compose.effects

import com.cdodi.core.navigation.effect.Effect
import com.cdodi.core.navigation.effect.EffectRegistry
import com.cdodi.core.navigation.effect.Layer

object UiEffectIds {
    const val PIXEL_MELT = "pixel-melt"
    const val FADE = "fade"
    const val MENU_MORPH = "menu-morph"
}

/**
 * The effects the Compose layer draws. For now the page layers read them straight from the navigation state; a
 * renderer per id comes with the navigation renderer (Phase 3).
 */
object UiEffects : EffectRegistry {
    private val ids = setOf(UiEffectIds.PIXEL_MELT, UiEffectIds.FADE, UiEffectIds.MENU_MORPH)

    override fun canRender(effect: Effect): Boolean = effect.layer == Layer.Ui && effect.id in ids
}
