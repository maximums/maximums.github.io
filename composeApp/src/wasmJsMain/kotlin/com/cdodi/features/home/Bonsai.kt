package com.cdodi.features.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cdodi.adapters.compose.gpuBacked
import com.cdodi.adapters.gpu.GpuWidgets

/**
 * Where the maple stands on Home: an empty box that the scene draws the tree behind ([BonsaiRenderer]), filling it at
 * any size, fading with its page (`LocalPageVisibility`).
 */
@Composable
fun Bonsai(modifier: Modifier = Modifier) {
    Box(modifier.gpuBacked(GpuWidgets.Kind.Bonsai))
}
