package com.cdodi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import blog.composeapp.generated.resources.Res
import blog.composeapp.generated.resources.cormorant_garamond_italic
import blog.composeapp.generated.resources.cormorant_garamond_light_italic
import blog.composeapp.generated.resources.jetbrains_mono_light
import blog.composeapp.generated.resources.jetbrains_mono_regular
import blog.composeapp.generated.resources.manrope_medium
import blog.composeapp.generated.resources.manrope_regular
import blog.composeapp.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.preloadFont

/**
 * DESIGN.md §4: a poet's serif for everything expressive, a quiet sans for reading, an instrument's mono for numbers.
 * Compose draws text on its own canvas, so CSS web fonts can't reach it: the three faces are bundled as resources.
 */
@Immutable
class AutumnFonts(val serif: FontFamily, val sans: FontFamily, val mono: FontFamily)

/** DESIGN.md §4's roles. Colours are left to the caller, except where a role always has one. */
@Immutable
class AutumnType(fonts: AutumnFonts) {
    /** Display: 88–132, the line as tall as the type. */
    val display = TextStyle(fontFamily = fonts.serif, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic, fontSize = 96.sp, lineHeight = 96.sp)

    /** Titles: 40 / 44. */
    val title = TextStyle(fontFamily = fonts.serif, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic, fontSize = 40.sp, lineHeight = 44.sp)

    /** Navigation and choices: 19–23. */
    val nav = TextStyle(fontFamily = fonts.serif, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic, fontSize = 23.sp)

    /** A line of verse, captions in the serif. */
    val verse = TextStyle(fontFamily = fonts.serif, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 26.sp)

    /** Body: 16 / 26. */
    val body = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp)

    /** Small sans: control names, buttons. */
    val small = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

    /** Label: SemiBold, upper case, 10–11, tracking 0.30 em. Callers upper-case the text. */
    val label = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.3.em)

    /** The brand line in the top bar: Medium, 11, tracking 0.32 em. */
    val brand = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.32.em)

    /** Readout: 11–16 / 1.5, tracking 0.04–0.08 em. */
    val readout = TextStyle(fontFamily = fonts.mono, fontWeight = FontWeight.Light, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.06.em)

    /** Hints in the corners: spaced-out mono capitals. */
    val hint = TextStyle(fontFamily = fonts.mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.24.em)
}

val LocalAutumnType = staticCompositionLocalOf<AutumnType> { error("No AutumnTheme around this composable") }

/**
 * The fonts, once every face has loaded; null until then. A page drawn before them would flash in a fallback face and
 * then jump, so the app waits (behind the loading line) instead.
 */
@Composable
fun rememberAutumnFonts(): AutumnFonts? {
    val faces = listOf(
        preload(Res.font.cormorant_garamond_light_italic, FontWeight.Light, FontStyle.Italic),
        preload(Res.font.cormorant_garamond_italic, FontWeight.Normal, FontStyle.Italic),
        preload(Res.font.manrope_regular, FontWeight.Normal),
        preload(Res.font.manrope_medium, FontWeight.Medium),
        preload(Res.font.manrope_semibold, FontWeight.SemiBold),
        preload(Res.font.jetbrains_mono_light, FontWeight.Light),
        preload(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    ).map { it ?: return null }
    return AutumnFonts(
        serif = FontFamily(faces[0], faces[1]),
        sans = FontFamily(faces[2], faces[3], faces[4]),
        mono = FontFamily(faces[5], faces[6]),
    )
}

@Composable
private fun preload(resource: FontResource, weight: FontWeight, style: FontStyle = FontStyle.Normal) =
    preloadFont(resource, weight, style).value
