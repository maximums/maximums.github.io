package com.cdodi.design.theme

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
        // Each pane's fog is where its fill stands between a cleared pane's and a fog pane's: autumn's glass is one tint.
        glass = GlassTokens(
            fogged = GlassStyle(Glass.copy(alpha = 0.58f), fog = 1f),
            cleared = GlassStyle(Glass.copy(alpha = 0.16f), fog = 0f),
            homePane = GlassStyle(PaneGlass.copy(alpha = 0.52f), fog = 0.86f),
            thick = GlassStyle(PaneGlass.copy(alpha = 0.8f), fog = 1f),
            welcome = GlassStyle(Glass.copy(alpha = 0.62f), fog = 1f),
            reading = GlassStyle(Night.copy(alpha = 0.72f), fog = 1f),
            folded = GlassStyle(Night.copy(alpha = 0.82f), fog = 1f),
            barTop = Bar.copy(alpha = 0.85f),
            barBottom = Bar.copy(alpha = 0.35f),
        ),
        // The design canvas's background study: its sky, its lights in DESIGN.md's colours, its fog and rain.
        scene = SceneTokens(
            sky = listOf(Color(0xFF0A0D10), Color(0xFF10151A), Color(0xFF171613), Color(0xFF1E1812)),
            lamps = Lamp,
            tailLights = Ember,
            headLights = Parchment,
            windows = Rain,
            streetFog = Color(170, 178, 180),
            // DESIGN.md's "scene fog".
            fog = Color(140, 148, 152),
            fallingNear = Color(222, 228, 230),
            fallingFar = Color(200, 210, 214),
            shade = Color(0xFF080A0C),
            ash = Color(64, 28, 20),
            figure = Color(16, 19, 23),
        ),
        copy = ThemeCopy(
            welcome = "stay a while — it's raining",
            sound = "rain",
            unavailable = "The rain can't reach this browser.",
            flockLine = "a murmuration under the rain",
            flockCaption = "the flock is gathering",
            lifeCaption = "the embers are catching",
        ),
        // Light rain recorded from an open window onto a city street at night (tools/sounds/master-rain.sh masters it).
        ambience = AmbienceTokens(
            sources = listOf("sounds/rain.webm", "sounds/rain.m4a"),
            level = 1f,
            muffled = 2_400f,
            clear = 9_000f,
            credit = Credit("Light rain on street", "BonnyOrbit", "https://freesound.org/s/380651/", source = "Freesound", licence = "CC0"),
        ),
    )
}
