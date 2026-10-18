package com.cdodi.ui.theme

import androidx.compose.ui.graphics.Color

/** DESIGN.md §3, with exactly its names, so a token reads the same in the design and in the code. */
object AutumnColors {
    /** Ground. Never pure black. */
    val Night = Color(0xFF0D1013)

    /** Raised ground, the top bar. */
    val Slate = Color(0xFF161B20)

    /** Slider tracks, dividers, idle strokes. */
    val WetStone = Color(0xFF2E363D)

    /** Secondary text and labels (6.2:1 on Night). */
    val Fog = Color(0xFF8C9499)

    /** Body text. */
    val Mist = Color(0xFFD6D2C8)

    /** Headings, active items, slider thumbs. */
    val Parchment = Color(0xFFEDE6D6)

    /** The accent: light, focus, the active thread (8.3:1 on Night). */
    val Lamp = Color(0xFFD8A15A)

    /** Heat and age: tail lights, old Life cells, warnings. Large text or non-text only (3.8:1). */
    val Ember = Color(0xFFB4532E)

    /** Cold detail: rain streaks, grid lines, passive bars. */
    val Rain = Color(0xFF7E97A3)

    // Derived values (DESIGN.md §3, "derived values used across the mockups").

    /** Hairlines: Parchment at 6–10 %. */
    val Hairline = Parchment.copy(alpha = 0.09f)

    /** The fog pane's fill. */
    val FogPane = Color(16, 20, 24).copy(alpha = 0.58f)

    /** A cleared pane's fill. */
    val ClearedPane = Color(16, 20, 24).copy(alpha = 0.16f)
}
