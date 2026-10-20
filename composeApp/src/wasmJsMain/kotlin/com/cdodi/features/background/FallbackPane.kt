package com.cdodi.features.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.cdodi.design.components.Label
import com.cdodi.design.components.LampButton
import com.cdodi.design.components.QuietButton
import com.cdodi.design.components.StrokeIcons
import com.cdodi.design.components.glass
import com.cdodi.design.theme.SiteTheme

/**
 * DESIGN.md §8, No WebGPU: one pane over the still scene. It says what's missing, which browsers have it, and offers a
 * way on: the Game of Life on the CPU.
 */
@Composable
fun FallbackPane(onPlayLife: () -> Unit, onAbout: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(max = 560.dp).glass().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Label("WebGPU is not available")
        Text("The rain can't reach this browser.", style = SiteTheme.type.title, color = SiteTheme.colors.bright)
        Text(
            "This site paints its scenes with WebGPU, which this browser doesn't provide. Chrome and Edge, Safari 26, " +
                "and Firefox on Windows and macOS do.",
            style = SiteTheme.type.body,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LampButton("Play Game of Life anyway", onPlayLife, icon = StrokeIcons.Play)
            QuietButton("About the project", onAbout)
        }
        Text(
            "Everything else still works — only the painted scenes are missing.",
            style = SiteTheme.type.small.copy(fontStyle = FontStyle.Italic),
            color = SiteTheme.colors.muted,
        )
    }
}
