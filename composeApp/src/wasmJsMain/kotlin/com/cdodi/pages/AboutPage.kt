package com.cdodi.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cdodi.adapters.compose.gpuBacked
import com.cdodi.adapters.gpu.GpuWidgets

/** For now the About page is its sphere, which the scene draws behind the page (a GPU-backed widget). */
@Composable
fun AboutPage() {
    Box(Modifier.fillMaxSize().gpuBacked(GpuWidgets.Kind.Sphere))
}
