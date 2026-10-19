package com.cdodi.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.compose.ProfilingHudWidth
import com.cdodi.ui.components.CornerHint
import com.cdodi.ui.components.QuietButton
import com.cdodi.ui.components.glass
import com.cdodi.ui.theme.AutumnColors
import com.cdodi.ui.theme.LocalViewport

/** Where a scene page's panel of controls sits; its GPU readout takes the opposite corner. */
enum class PanelCorner { TopEnd, BottomStart }

/** Space between the panel, the readout and the hint. */
private val Gap = 24.dp

/**
 * A scene page's overlay on its GPU scene (DESIGN.md §8, the Boids and Life mockups): a panel of controls [panelWidth]
 * wide in its [corner], the GPU [readout] in the opposite corner, and a [hint] in the bottom-right one, all inside the
 * viewport's gutter.
 *
 * Where the window is too narrow for the panel and the readout side by side, the readout goes into the panel, at its
 * end. The hint wraps rather than run into the readout, and the panel stops short of the hint, scrolling inside when
 * its controls are taller than the room left. On a phone, the panel folds behind a button.
 */
@Composable
fun SceneOverlay(
    panelWidth: Dp,
    corner: PanelCorner,
    hint: String,
    readout: @Composable (Modifier) -> Unit,
    panelSpacing: Dp = 20.dp,
    panel: @Composable ColumnScope.() -> Unit,
) {
    val viewport = LocalViewport.current
    val hintText = @Composable { modifier: Modifier -> CornerHint(hint, modifier.padding(top = Gap), textAlign = TextAlign.End) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(viewport.gutter)) {
        val apart = !viewport.compact && maxWidth >= panelWidth + Gap + ProfilingHudWidth
        when {
            !apart -> {
                // On a phone the folded panel keeps to the top, wherever its corner.
                val bottom = corner == PanelCorner.BottomStart && !viewport.compact
                Column(Modifier.fillMaxSize(), verticalArrangement = if (bottom) Arrangement.Bottom else Arrangement.SpaceBetween) {
                    ScenePanel(panelWidth, panelSpacing, viewport.compact, Modifier.align(if (bottom) Alignment.Start else Alignment.End).weight(1f, fill = false)) {
                        panel()
                        readout(Modifier.fillMaxWidth())
                    }
                    hintText(Modifier.align(Alignment.End))
                }
            }
            corner == PanelCorner.TopEnd -> Row(Modifier.fillMaxSize()) {
                readout(Modifier.align(Alignment.Bottom))
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = Gap),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    ScenePanel(panelWidth, panelSpacing, folded = false, Modifier.weight(1f, fill = false), panel)
                    hintText(Modifier)
                }
            }
            else -> Row(Modifier.fillMaxSize()) {
                ScenePanel(panelWidth, panelSpacing, folded = false, Modifier.align(Alignment.Bottom), panel)
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = Gap),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    readout(Modifier)
                    hintText(Modifier)
                }
            }
        }
    }
}

/**
 * The panel: the controls on a fog pane (DESIGN.md §5), [width] wide, scrolling inside the pane when they're taller
 * than the room they're given; they fade into the fog at an edge with more beyond it. On a phone ([folded]) they'd
 * cover most of the scene, so they fold behind a "Controls" button and open full width.
 */
@Composable
private fun ScenePanel(width: Dp, spacing: Dp, folded: Boolean, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (!folded) {
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
