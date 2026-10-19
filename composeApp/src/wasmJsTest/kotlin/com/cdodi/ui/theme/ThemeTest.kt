package com.cdodi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What every theme owes the reader, whatever its season (DESIGN.md §3's contrast notes, WCAG 2's ratios). */
class ThemeTest {

    private val themes = listOf(AutumnTheme)

    /** WCAG 2's contrast ratio between two opaque colours: 1 (none) to 21 (black on white). */
    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun assertContrast(minimum: Float, foreground: Color, background: Color, what: String) {
        val ratio = contrast(foreground.compositeOver(background), background)
        assertTrue(ratio >= minimum, "$what: $ratio:1, needs $minimum:1")
    }

    @Test
    fun textReadsOnTheGroundAndOnEveryPane() {
        for (theme in themes) {
            val colors = theme.colors
            val grounds = mapOf(
                "ground" to colors.ground,
                "raised ground" to colors.groundRaised,
                // A pane over the darkest part of the scene: its fill over the ground.
                "fog pane" to theme.glass.fogged.compositeOver(colors.ground),
                "reading pane" to theme.glass.reading.compositeOver(colors.ground),
            )
            for ((name, ground) in grounds) {
                assertContrast(4.5f, colors.muted, ground, "${theme.name}: muted text on the $name")
                assertContrast(7f, colors.text, ground, "${theme.name}: body text on the $name")
                assertContrast(7f, colors.bright, ground, "${theme.name}: headings on the $name")
                assertContrast(4.5f, colors.accent, ground, "${theme.name}: the accent (links) on the $name")
                // Heat is for large text and marks only: WCAG's large-text minimum.
                assertContrast(3f, colors.heat, ground, "${theme.name}: heat on the $name")
            }
        }
    }

    @Test
    fun theAutumnRolesAreDesignMdsSwatches() {
        // DESIGN.md §3's notes, so a change to a swatch shows up here as well as on screen.
        val colors = AutumnTheme.colors
        assertEquals(6.2f, contrast(colors.muted, colors.ground), 0.1f)
        assertEquals(8.3f, contrast(colors.accent, colors.ground), 0.1f)
        assertEquals(3.8f, contrast(colors.heat, colors.ground), 0.1f)
    }

    @Test
    fun aPaneIsFoggierThanAClearedOne() {
        // The GPU blurs the street behind a pane by where its fill sits between cleared and fogged.
        for (theme in themes) {
            val glass = theme.glass
            assertTrue(glass.fogged.alpha > glass.cleared.alpha, theme.name)
            for (pane in listOf(glass.homePane, glass.thick, glass.welcome)) {
                assertTrue(pane.alpha > glass.cleared.alpha, "${theme.name}: $pane is clearer than a cleared pane")
            }
        }
    }
}
