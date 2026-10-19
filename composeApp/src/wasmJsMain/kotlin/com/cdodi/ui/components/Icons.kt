package com.cdodi.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * DESIGN.md §6: thin stroke icons (1.4 px on a 16 px grid), never emoji or filled glyphs. Drawn in white, to be tinted
 * with the theme's colours where they're shown.
 */
object StrokeIcons {
    val Play = icon("Play", "M5 3l8 5-8 5V3z")
    val Pause = icon("Pause", "M5.5 3v10M10.5 3v10")
    val Step = icon("Step", "M4 3l6 5-6 5V3zM12 3v10")
    val Clear = icon("Clear", "M3 8a5 5 0 1 0 1.5-3.6M3 3v2.5h2.5")
    val Scatter = icon("Scatter", "M8 8l-4-4M8 8l4-4M8 8l-4 4M8 8l4 4M3 2.5h2M2.5 3v2M13 2.5h-2M13.5 3v2M3 13.5h2M2.5 13v-2M13 13.5h-2M13.5 13v-2")
    val Fit = icon("Fit", "M2.5 6V2.5H6M10 2.5h3.5V6M13.5 10v3.5H10M6 13.5H2.5V10")

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 16.dp, defaultHeight = 16.dp, viewportWidth = 16f, viewportHeight = 16f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
