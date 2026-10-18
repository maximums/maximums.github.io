package com.cdodi.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp

/** The Boids page without WebGPU: the murmuration needs compute shaders, so this only says so. */
@Composable
fun BoidsPage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "The murmuration needs WebGPU, which this browser doesn't have.",
            fontSize = 18.sp,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF8C9499), // DESIGN.md: Fog
        )
    }
}
