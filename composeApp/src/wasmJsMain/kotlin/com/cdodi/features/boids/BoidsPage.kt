package com.cdodi.features.boids

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cdodi.ui.theme.SiteTheme

/** The Boids page without WebGPU: the murmuration needs compute shaders, so this only says so. */
@Composable
fun BoidsPage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "The murmuration needs WebGPU, which this browser doesn't have.",
            style = SiteTheme.type.verse,
            color = SiteTheme.colors.muted,
        )
    }
}
