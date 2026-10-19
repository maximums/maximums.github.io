package com.cdodi.ui.theme

import androidx.compose.ui.graphics.Color

/** DESIGN.md §3's swatches, with exactly its names, so a colour reads the same in the design and in the theme. */
private object Swatches {
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

    /** The glass's tint: a fog pane is this, at its fogginess. */
    val Glass = Color(16, 20, 24)

    /** Home's panes are a cooler glass. */
    val PaneGlass = Color(18, 22, 26)

    /** The top bar's dark. */
    val Bar = Color(10, 13, 16)
}

/** "Autumn Rain" (DESIGN.md): a wet street at night behind fogged glass, lit by its lamps. */
val AutumnTheme = with(Swatches) {
    Theme(
        name = "autumn",
        colors = ColorRoles(
            ground = Night,
            groundRaised = Slate,
            stroke = WetStone,
            muted = Fog,
            text = Mist,
            bright = Parchment,
            accent = Lamp,
            heat = Ember,
            cold = Rain,
            // DESIGN.md: Parchment at 6–10 %.
            hairline = Parchment.copy(alpha = 0.09f),
        ),
        glass = GlassTokens(
            fogged = Glass.copy(alpha = 0.58f),
            cleared = Glass.copy(alpha = 0.16f),
            homePane = PaneGlass.copy(alpha = 0.52f),
            thick = PaneGlass.copy(alpha = 0.8f),
            welcome = Glass.copy(alpha = 0.62f),
            reading = Night.copy(alpha = 0.72f),
            folded = Night.copy(alpha = 0.82f),
            barTop = Bar.copy(alpha = 0.85f),
            barBottom = Bar.copy(alpha = 0.35f),
        ),
        // street.wgsl's sky stops and rain.wgsl's vignette.
        scene = SceneTokens(
            sky = listOf(Color(0xFF0A0D10), Color(0xFF10151A), Color(0xFF171613), Color(0xFF1E1812)),
            light = Lamp,
            shade = Color(0xFF080A0C),
        ),
    )
}
