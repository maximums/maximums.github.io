package com.cdodi.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import kotlinx.browser.document
import kotlinx.coroutines.delay

/**
 * The root of the UI layer. Skiko clears its canvas to opaque white every frame (SKIKO-949, still open), which would
 * hide the WebGPU canvas underneath; clearing to transparent first, here and only here, lets the scene show through
 * wherever Compose draws nothing. It must not sit inside an offscreen graphics layer.
 *
 * It also retires the page's loading indicator once Compose has drawn its first frame.
 */
@Composable
fun AppRoot(content: @Composable BoxScope.() -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        val loading = document.getElementById("loading") ?: return@LaunchedEffect
        loading.classList.add("gone")
        delay(1000) // its CSS fade
        loading.remove()
    }

    Box(
        modifier = Modifier.fillMaxSize().drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) },
        content = content,
    )
}
