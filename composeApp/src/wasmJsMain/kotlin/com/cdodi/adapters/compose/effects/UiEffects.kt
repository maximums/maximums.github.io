package com.cdodi.adapters.compose.effects

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.effect.Effect
import com.cdodi.core.navigation.effect.EffectRegistry
import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.effect.Layer

object UiEffectIds {
    const val PIXEL_MELT = "pixel-melt"
    const val FADE = "fade"

    /** The menu leaves Home: its words fly to the top bar, its panes dissolve (DESIGN.md §9's `menu-dissolve`). */
    const val MENU_MORPH = "menu-morph"

    // "Breath on the window" (DESIGN.md §9). The GPU's part of the first two (the street behind the pane sharpening or
    // dimming) follows from the glass: its blur tracks how fogged each pane is.

    /** A band of clarity wipes across the chosen pane. */
    const val FOG_WIPE = "fog-wipe"

    /** The other panes fog over. */
    const val FOG_THICKEN = "fog-thicken"

    /** The chosen pane's word catches the lamp. */
    const val LABEL_GLOW = "label-glow"

    /** A line of verse while the fog holds. */
    const val CAPTION = "caption"

    /** The top bar condenses onto the glass, blur to sharp. */
    const val CONDENSE = "condense"
}

/**
 * The effects the Compose layer draws. Page effects ([PageEffect]) are applied to whole pages by the navigation
 * renderer; `menu-morph` drives the menu's placement and shape through [LocalMorph].
 */
object UiEffects : EffectRegistry {
    private val ids = setOf(
        UiEffectIds.PIXEL_MELT, UiEffectIds.FADE, UiEffectIds.MENU_MORPH,
        UiEffectIds.FOG_WIPE, UiEffectIds.FOG_THICKEN, UiEffectIds.LABEL_GLOW, UiEffectIds.CAPTION, UiEffectIds.CONDENSE,
    )

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

/**
 * The page being entered fades in over the one being left, which fades out. The entering page also condenses: it
 * starts blurred and sharpens as it arrives, like breath fading from a cold window (DESIGN.md §9, `condense`).
 */
object FadeEffect : PageEffect {
    override val id = UiEffectIds.FADE

    override fun visibility(state: EffectState, role: PageRole, visibility: Float): Float {
        val travelled = state.travelled ?: return visibility
        return if (role == PageRole.Entering) travelled else 1f - travelled
    }

    override fun GraphicsLayerScope.apply(state: EffectState, role: PageRole, transition: NavState.Transitioning) {
        if (role != PageRole.Entering) return
        val blur = (1f - (state.travelled ?: 1f)) * 12.dp.toPx()
        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
    }
}

/** How far effect [id] has played, by its own progress (1 at the destination's end, whichever way it plays); null if it hasn't started. */
fun NavState.progressOf(id: String): Float? =
    (this as? NavState.Transitioning)?.effects?.firstOrNull { it.effect.id == id }?.progress

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
