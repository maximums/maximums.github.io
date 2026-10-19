package com.cdodi.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cdodi.shell.LocalMenuLayout
import com.cdodi.ui.components.QuietButton
import com.cdodi.ui.components.glass
import com.cdodi.ui.theme.AutumnColors

/**
 * A scene page's controls on a fog pane (DESIGN.md §5), [width] wide, scrolling inside the pane when they're taller than
 * the room they're given (a short laptop window); the controls fade into the fog at an edge with more beyond it. On a
 * phone they'd cover most of the scene, so they fold behind a "Controls" button and open full width.
 */
@Composable
fun CompactPanel(width: Dp, modifier: Modifier = Modifier, spacing: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    if (!LocalMenuLayout.current.compact) {
        val scroll = rememberScrollState()
        Column(
            modifier.width(width).glass().fadingEdges(scroll).verticalScroll(scroll).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuietButton(if (open) "Close" else "Controls", { open = !open }, Modifier.align(Alignment.End))
        if (open) {
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(fill = { AutumnColors.Night.copy(alpha = 0.82f) })
                    .fadingEdges(scroll)
                    .verticalScroll(scroll)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        }
    }
}

/** Fades what's drawn into the pane's fog at the top or bottom edge while [scroll] has more beyond it. */
private fun Modifier.fadingEdges(scroll: ScrollState): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = minOf(40.dp.toPx(), size.height / 4)
        if (scroll.canScrollBackward) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = edge), size = Size(size.width, edge), blendMode = BlendMode.DstIn)
        }
        if (scroll.canScrollForward) {
            val top = size.height - edge
            drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = top, endY = size.height), Offset(0f, top), Size(size.width, edge), blendMode = BlendMode.DstIn)
        }
    }
