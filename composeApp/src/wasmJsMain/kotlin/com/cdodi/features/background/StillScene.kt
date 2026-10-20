package com.cdodi.features.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.cdodi.design.theme.SiteTheme
import kotlin.math.max

/**
 * The street without WebGPU (DESIGN.md §8, "No WebGPU"): the same street `street.wgsl` draws, the same lights in the
 * same places ([StreetLights]), but still and dimmed, drawn once by Compose. No rain, no drifting fog: the scene
 * waits for a browser that can paint it.
 */
@Composable
fun StillScene(modifier: Modifier = Modifier) {
    val scene = SiteTheme.scene
    Canvas(modifier.fillMaxSize()) {
        val (sky0, sky1, sky2, sky3) = scene.sky
        drawRect(Brush.verticalGradient(0f to sky0, 0.48f to sky1, 0.78f to sky2, 1f to sky3))

        // The study's 1440 × 900 space at the window's height, centred; narrower on a tall window, as street.wgsl.
        val toWindow = size.height / 900f
        val narrow = max(1f, 0.65f * (1440f / 900f) / (size.width / size.height))
        fun place(x: Float, y: Float) = Offset(size.width / 2 + (x - 720f) / narrow * toWindow, size.height / 2 + (y - 450f) * toWindow)

        val glowCentre = place(720f, 900f)
        scale(scaleX = 864f / narrow / 266f, scaleY = 1f, pivot = glowCentre) {
            val radius = 266f * toWindow
            drawCircle(Brush.radialGradient(0f to scene.lamps.copy(alpha = 0.14f), 0.7f to scene.lamps.copy(alpha = 0f), center = glowCentre, radius = radius), radius, glowCentre)
        }

        for (light in StreetLights.all) {
            val centre = place(light.x, light.y)
            val radius = light.size / 2 * toWindow
            val colour = light.kind.colourIn(scene)
            // Dimmed: the lights at the low point of their breath.
            val rim = light.alpha * 0.6f
            drawDisc(centre, radius, colour, rim)
            if (light.reflected) {
                val reflection = centre + Offset(0f, radius * 2.4f)
                scale(scaleX = 0.25f, scaleY = 1f, pivot = reflection) {
                    val length = radius * 2f
                    drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = rim * 0.2f), colour.copy(alpha = 0f)), reflection, length), length, reflection)
                }
            }
        }

        // The vignette, as rain.wgsl draws it.
        val corner = Offset(size.width / 2, size.height * 0.45f)
        scale(scaleX = 0.75f / 0.7f * size.width / size.height, scaleY = 1f, pivot = corner) {
            val radius = 0.7f * size.height
            drawRect(
                Brush.radialGradient(0.55f to scene.shade.copy(alpha = 0f), 1f to scene.shade.copy(alpha = 0.75f), center = corner, radius = radius),
                topLeft = Offset(corner.x - radius * 2, corner.y - radius * 2),
                size = Size(radius * 4, radius * 4),
            )
        }
    }
}

/** A light out of focus, as the study draws it: an even glow, a brighter rim near the edge, nothing past it. */
private fun DrawScope.drawDisc(centre: Offset, radius: Float, colour: Color, rim: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to colour.copy(alpha = rim * 0.55f),
            0.735f to colour.copy(alpha = rim * 0.55f),
            0.89f to colour.copy(alpha = rim),
            1f to colour.copy(alpha = 0f),
            center = centre,
            radius = radius,
        ),
        radius,
        centre,
    )
}

