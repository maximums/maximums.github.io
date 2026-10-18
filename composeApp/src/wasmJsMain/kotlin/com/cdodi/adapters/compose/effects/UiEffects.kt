package com.cdodi.adapters.compose.effects

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.GraphicsLayerScope
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.effect.Effect
import com.cdodi.core.navigation.effect.EffectRegistry
import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.effect.Layer

object UiEffectIds {
    const val PIXEL_MELT = "pixel-melt"
    const val FADE = "fade"
    const val MENU_MORPH = "menu-morph"
}

/**
 * The effects the Compose layer draws. Page effects ([PageEffect]) are applied to whole pages by the navigation
 * renderer; `menu-morph` drives the menu's placement and shape through [LocalMorph].
 */
object UiEffects : EffectRegistry {
    private val ids = setOf(UiEffectIds.PIXEL_MELT, UiEffectIds.FADE, UiEffectIds.MENU_MORPH)

    override fun canRender(effect: Effect): Boolean = effect.layer == Layer.Ui && effect.id in ids
}

/**
 * Where the running layout morph is. Layouts that morph (the menu's placement and shape) follow it instead of a clock
 * of their own, so they pause, slow down and reverse with navigation.
 */
sealed interface Morph {
    /** No transition: layouts sit where lookahead puts them. */
    data object Settled : Morph

    /** A transition is running, but its morph hasn't started (or it has none): layouts hold still. */
    data object Waiting : Morph

    /** The morph is [fraction] of the way from where layouts were when their target changed to the target. */
    data class Moving(val fraction: Float) : Morph
}

/** Read during layout, so only layout runs again each frame, with no recomposition and no frame of lag. */
fun interface MorphSource {
    fun current(): Morph
}

val LocalMorph = staticCompositionLocalOf { MorphSource { Morph.Settled } }

/** The [Morph] that effect [id] (e.g. `menu-morph`) puts layouts in. */
fun NavState.morph(id: String): Morph = when (this) {
    is NavState.Idle -> Morph.Settled
    is NavState.Transitioning -> effects.firstOrNull { it.effect.id == id }?.travelled?.let(Morph::Moving) ?: Morph.Waiting
}

enum class PageRole { Leaving, Entering }

/**
 * A UI effect drawn on a whole page. During a transition the page being left starts fully visible and the page being
 * entered starts hidden; effects change that from there. A transition with no page effect cuts at its end.
 */
interface PageEffect {
    val id: String

    /**
     * How visible the page is under this effect, given how visible it is so far. The page's layer takes it as its
     * alpha, and effects the scene draws behind the page's elements fade with it.
     */
    fun visibility(state: EffectState, role: PageRole, visibility: Float): Float = visibility

    /** Anything else the effect does to the page's layer: a shader over its pixels, for one. */
    fun GraphicsLayerScope.apply(state: EffectState, role: PageRole, transition: NavState.Transitioning) {}
}

/** The page being entered fades in over the one being left, which fades out. */
object FadeEffect : PageEffect {
    override val id = UiEffectIds.FADE

    override fun visibility(state: EffectState, role: PageRole, visibility: Float): Float {
        val travelled = state.travelled ?: return visibility
        return if (role == PageRole.Entering) travelled else 1f - travelled
    }
}

/** How visible a page in [role] is now, after every page effect has had its say; 1 outside a transition. */
fun NavState.visibilityOf(role: PageRole?, pageEffects: List<PageEffect>): Float {
    if (role == null || this !is NavState.Transitioning) return 1f
    var visibility = if (role == PageRole.Entering) 0f else 1f
    for (state in effects) {
        val effect = pageEffects.firstOrNull { it.id == state.effect.id } ?: continue
        visibility = effect.visibility(state, role, visibility)
    }
    return visibility
}
