package com.cdodi.ui.theme

import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * What each colour is for (DESIGN.md §3). A theme fills the roles from its own swatches, so the site asks for an
 * accent, never for "Lamp": autumn's accent is its lamplight, another season's needn't be.
 */
@Immutable
data class ColorRoles(
    /** The ground everything stands on. Never pure black. */
    val ground: Color,
    /** Raised ground: the top bar. */
    val groundRaised: Color,
    /** Slider tracks, dividers, idle strokes. */
    val stroke: Color,
    /** Secondary text and labels, and passive marks. At least 4.5:1 on [ground]. */
    val muted: Color,
    /** Body text. */
    val text: Color,
    /** Headings, active items, slider thumbs, icons. */
    val bright: Color,
    /** Light, focus and the active thread: the one colour that calls. */
    val accent: Color,
    /** Heat and age: old Life cells, warnings. Large text or non-text only. */
    val heat: Color,
    /** Cold detail: grid lines, passive bars. */
    val cold: Color,
    /** Hairlines over the glass. */
    val hairline: Color,
)

/**
 * A kind of glass: the [fill] Compose draws over the scene, and how fogged it is, [fog], from 0 (clear) to 1 (a fog
 * pane). The GPU blurs the street behind by [fog], and condensation gathers with it; the two are apart so a theme's
 * glass can be pale and heavily frosted, or dark and clear.
 */
@Immutable
data class GlassStyle(val fill: Color, val fog: Float) {
    init {
        require(fog in 0f..1f) { "fog is 0 (clear) to 1 (a fog pane), got $fog" }
    }
}

/** The glass [fraction] of the way from [start] to [stop]: its fill and its fog alike. */
fun lerp(start: GlassStyle, stop: GlassStyle, fraction: Float) =
    GlassStyle(lerp(start.fill, stop.fill, fraction), start.fog + (stop.fog - start.fog) * fraction.coerceIn(0f, 1f))

/** Each kind of pane the site is built from (DESIGN.md §5), and the top bar. */
@Immutable
data class GlassTokens(
    /** A fog pane: controls over a scene. */
    val fogged: GlassStyle,
    /** A pane wiped clear, by a hover or by the transition's wipe. */
    val cleared: GlassStyle,
    /** Home's four panes, a little thinner than a fog pane. */
    val homePane: GlassStyle,
    /** The other panes, fogging over while the chosen one is wiped. */
    val thick: GlassStyle,
    /** The Welcome rhombus, a little thicker than a fog pane. */
    val welcome: GlassStyle,
    /** Under long reading (About, Notes): steadier than fog over moving lights. */
    val reading: GlassStyle,
    /** A phone's opened controls, over most of the scene. */
    val folded: GlassStyle,
    /** The top bar at its top edge, fading to [barBottom] at its lower one. */
    val barTop: Color,
    val barBottom: Color,
)

/** The scene's colours where Compose draws it: the still street, for a browser without WebGPU. */
@Immutable
data class SceneTokens(
    /** The sky's four stops, from the top down, as the street's shader has them. */
    val sky: List<Color>,
    /** The street's light, pooled low in the middle. */
    val light: Color,
    /** The dark the corners fall into (the vignette). */
    val shade: Color,
) {
    init {
        require(sky.size == 4) { "The sky has four stops, from the top down" }
    }
}

/** A look for the site: its colours by role, its glass and its scene. Plain data, so another one can be switched in. */
@Immutable
class Theme(val name: String, val colors: ColorRoles, val glass: GlassTokens, val scene: SceneTokens)

/** The theme in use, provided by [SiteTheme]. */
val LocalTheme = staticCompositionLocalOf<Theme> { error("No SiteTheme around this composable") }

/** Gives everything under it [theme], its type set in [fonts], and body text as the default for every `Text`. */
@Composable
fun SiteTheme(theme: Theme, fonts: FontSet, content: @Composable () -> Unit) {
    val type = remember(fonts) { TypeRoles(fonts) }
    CompositionLocalProvider(
        LocalTheme provides theme,
        LocalTypeRoles provides type,
        LocalTextStyle provides type.body,
        LocalContentColor provides theme.colors.text,
        content = content,
    )
}

/** The theme's parts, where a composable reads them: `Text("Boids", style = SiteTheme.type.title, color = SiteTheme.colors.bright)`. */
object SiteTheme {
    val colors: ColorRoles
        @Composable @ReadOnlyComposable get() = LocalTheme.current.colors

    val glass: GlassTokens
        @Composable @ReadOnlyComposable get() = LocalTheme.current.glass

    val scene: SceneTokens
        @Composable @ReadOnlyComposable get() = LocalTheme.current.scene

    val type: TypeRoles
        @Composable @ReadOnlyComposable get() = LocalTypeRoles.current
}
