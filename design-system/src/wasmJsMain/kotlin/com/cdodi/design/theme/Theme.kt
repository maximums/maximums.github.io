package com.cdodi.design.theme

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

/**
 * The scenes' colours, as the GPU draws them (the street, the rain, the fog, Life, the flock; through the theme's
 * uniform buffer) and as Compose draws the still street for a browser without WebGPU. What differs in kind between
 * themes (rain or snow, how it falls) is a scene of its own, not a token.
 */
@Immutable
data class SceneTokens(
    /** The sky's four stops, from the top down. */
    val sky: List<Color>,
    /** The street's lamps; their light also pools on the ground in the middle. */
    val lamps: Color,
    /** The cars' tail lights, low on one side. */
    val tailLights: Color,
    /** The cars' head lights, low on the other. */
    val headLights: Color,
    /** A few distant windows. */
    val windows: Color,
    /** The fog banks drifting over the street. */
    val streetFog: Color,
    /** The fog that rolls over a scene between pages, and the flock's fog bank. */
    val fog: Color,
    /** What falls past the glass, near and far (rain streaks, in autumn). */
    val fallingNear: Color,
    val fallingFar: Color,
    /** The dark the corners fall into (the vignette). */
    val shade: Color,
    /** Life's oldest cells, cooling from heat to nothing. */
    val ash: Color,
    /** The flock's birds against the fog. */
    val figure: Color,
) {
    /** The street's lights by kind, in this order: lamps, tail lights, head lights, windows. */
    val lights: List<Color> get() = listOf(lamps, tailLights, headLights, windows)

    init {
        require(sky.size == 4) { "The sky has four stops, from the top down" }
    }
}

/** Where the site speaks of its weather: the theme's words, so another season can say its own. */
@Immutable
data class ThemeCopy(
    /** A line of verse under the name, on Home. */
    val welcome: String,
    /** What the sound outside is, as a noun ("rain"): the sound toggle's name, and the About page's credit. */
    val sound: String,
    /** The heading for a browser that can't paint the scenes. */
    val unavailable: String,
    /** Boids' line under its title. */
    val flockLine: String,
    /** The line of verse while the fog holds on the way to Boids, and to Life. */
    val flockCaption: String,
    val lifeCaption: String,
)

/**
 * A work's credit (a recording, a model): its [title] by its [author], from [source] under [licence], at [url]. A
 * licence that asks for a link to its text (CC BY) gives it as [licenceUrl].
 */
@Immutable
data class Credit(val title: String, val author: String, val url: String, val source: String, val licence: String, val licenceUrl: String? = null)

/** The sound outside the window: the recording, how loud, how muffled by the glass, and whose it is. */
@Immutable
data class AmbienceTokens(
    /** The recording, in the formats to try in order: paths the page serves (an Opus file first, then AAC). */
    val sources: List<String>,
    /** Its level, 0 to 1. The recordings are mastered once (to −30 LUFS); turn one down here. */
    val level: Float,
    /** The glass's low-pass cut-off in Hz: behind fogged glass, and behind cleared glass. */
    val muffled: Float,
    val clear: Float,
    val credit: Credit,
) {
    init {
        require(sources.isNotEmpty()) { "A sound needs a recording" }
        require(level in 0f..1f) { "level is 0 to 1, got $level" }
        require(muffled in 20f..clear) { "the glass muffles from $muffled Hz to $clear Hz: muffled must be the lower" }
    }
}

/**
 * A look for the site: its colours by role, its glass, its scene, its words and its sound. Plain data, so another one
 * can be switched in.
 */
@Immutable
class Theme(
    val name: String,
    val colors: ColorRoles,
    val glass: GlassTokens,
    val scene: SceneTokens,
    val copy: ThemeCopy,
    val ambience: AmbienceTokens,
)

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

    val copy: ThemeCopy
        @Composable @ReadOnlyComposable get() = LocalTheme.current.copy

    val ambience: AmbienceTokens
        @Composable @ReadOnlyComposable get() = LocalTheme.current.ambience

    val type: TypeRoles
        @Composable @ReadOnlyComposable get() = LocalTypeRoles.current
}
