package com.cdodi.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cdodi.design.components.GlassShape
import com.cdodi.design.components.focusRing
import com.cdodi.design.components.glass
import com.cdodi.design.components.warmth
import com.cdodi.design.theme.SiteTheme

/**
 * The slim top bar (DESIGN.md §8, shared): the brand mark and name on the left, which go Home, the menu's words in the
 * centre and [end] on the right. Where the centre would crowd the brand or [end], the words keep [MenuLayout.navGap]
 * from them instead. On a phone the words take the right, and [end] sits by the brand. [solid] (read while drawing) is
 * how far the bar has condensed onto the glass: from blurred and faint to sharp.
 */
@Composable
fun BoxScope.TopBar(
    layout: MenuLayout,
    name: String,
    onHome: () -> Unit,
    solid: () -> Float,
    end: @Composable () -> Unit = {},
    words: @Composable RowScope.() -> Unit,
) {
    val colors = SiteTheme.colors
    val glass = SiteTheme.glass
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(layout.barHeight)
            .drawBehind {
                val a = solid()
                if (a <= 0.001f) return@drawBehind
                drawRect(Brush.verticalGradient(listOf(glass.barTop.copy(alpha = glass.barTop.alpha * a), glass.barBottom.copy(alpha = glass.barBottom.alpha * a))))
                drawLine(colors.hairline.copy(alpha = colors.hairline.alpha * a), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            },
    ) {
        val condensing = Modifier.graphicsLayer {
            val a = solid()
            alpha = a
            val blur = (1f - a) * 8.dp.toPx()
            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
        }
        Layout(
            content = {
                Row(condensing, verticalAlignment = Alignment.CenterVertically) {
                    Brand(layout, name, onHome, Modifier)
                    if (layout.compact) end()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(layout.navGap), verticalAlignment = Alignment.CenterVertically, content = words)
                Box(condensing) { if (!layout.compact) end() }
            },
            modifier = Modifier.matchParentSize(),
            measurePolicy = remember(layout) { barPolicy(layout) },
        )
    }
}

/** Places the bar's three parts: the brand, the words and the end ([TopBar]). */
private fun barPolicy(menu: MenuLayout) = MeasurePolicy { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val (brand, words, end) = measurables.map { it.measure(loose) }
    val width = constraints.maxWidth
    val margin = menu.margin.roundToPx()
    val gap = menu.navGap.roundToPx()
    layout(width, constraints.maxHeight) {
        fun centred(height: Int) = (constraints.maxHeight - height) / 2
        brand.place(margin, centred(brand.height))
        end.place(width - margin - end.width, centred(end.height))
        val nearest = margin + brand.width + gap
        val furthest = width - margin - end.width - (if (end.width > 0) gap else 0) - words.width
        val x = if (menu.compact) furthest else ((width - words.width) / 2).coerceIn(nearest, maxOf(nearest, furthest))
        words.place(x, centred(words.height))
    }
}

/** The diamond, in the accent, and the name (where the bar has no room for the name, the diamond alone). */
@Composable
private fun Brand(layout: MenuLayout, name: String, onHome: () -> Unit, modifier: Modifier) {
    val colors = SiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val warm by warmth(interaction)
    Row(
        modifier
            .focusRing(interaction, GlassShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onHome)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.size(14.dp)) {
            val path = Path().apply {
                moveTo(size.width / 2, 0.5f); lineTo(size.width - 0.5f, size.height / 2)
                lineTo(size.width / 2, size.height - 0.5f); lineTo(0.5f, size.height / 2); close()
            }
            drawPath(path, colors.accent, style = Stroke(1.dp.toPx()))
        }
        if (layout.brandName) Text(name.uppercase(), style = SiteTheme.type.brand, color = lerp(colors.text, colors.bright, warm))
    }
}
