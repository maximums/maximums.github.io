package com.cdodi.shell

import com.cdodi.ui.theme.AutumnTheme
import com.cdodi.ui.theme.LocalViewport
import com.cdodi.ui.theme.rememberAutumnFonts
import com.cdodi.ui.theme.windowViewport
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 * It also holds the UI back until the theme's fonts have loaded (a page in a fallback face would jump when they
 * arrive), then retires the page's loading line once Compose has drawn the first frame with them. And it measures the
 * window once for everything under it: the [LocalViewport], and the frame's [LocalMenuLayout] from it, both at the
 * page's density.
 */
@Composable
fun AppRoot(content: @Composable BoxScope.() -> Unit) {
    val fonts = rememberAutumnFonts()
    if (fonts != null) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            val loading = document.getElementById("loading") ?: return@LaunchedEffect
            loading.classList.add("gone")
            delay(1000) // its CSS fade
            loading.remove()
        }
    }

    val viewport = windowViewport()
    val layout = remember(viewport) { menuLayout(viewport) }
    Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }) {
        if (fonts != null) {
            CompositionLocalProvider(LocalViewport provides viewport, LocalMenuLayout provides layout) {
                AutumnTheme(fonts) { content() }
            }
        }
    }
}
