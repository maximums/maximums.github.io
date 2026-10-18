package com.cdodi.features.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * The scene without WebGPU (DESIGN.md §8, "No WebGPU"): a static, dimmed copy of the rainy street, drawn once by
 * Compose. A dark sky and a band of soft lights where the bokeh would be: street lamps, tail lights, head lights.
 */
@Composable
fun StillScene(modifier: Modifier = Modifier) {
    val lights = remember {
        val random = Random(7)
        val colours = listOf(Color(0xFFFFB24D), Color(0xFFFF2A0A), Color(0xFFE6E6FF), Color(0xFFFFB24D))
        List(18) { i ->
            Light(
                x = random.nextFloat(),
                y = 0.42f + random.nextFloat() * 0.22f,
                radius = 0.03f + random.nextFloat() * 0.05f,
                colour = colours[i % colours.size].copy(alpha = 0.18f + random.nextFloat() * 0.2f),
            )
        }
    }

    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0D24), Color(0xFF07080F), Color(0xFF030406))))
        for (light in lights) {
            val centre = Offset(light.x * size.width, light.y * size.height)
            val radius = light.radius * size.height
            drawCircle(Brush.radialGradient(listOf(light.colour, light.colour.copy(alpha = 0f)), centre, radius), radius, centre)
        }
    }
}

private class Light(val x: Float, val y: Float, val radius: Float, val colour: Color)
