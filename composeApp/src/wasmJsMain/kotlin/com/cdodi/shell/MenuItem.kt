package com.cdodi.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.compose.morph.animatePlacement
import com.cdodi.adapters.compose.morph.morphScale
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.features.about.AboutDestination
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.life.LifeDestination
import com.cdodi.features.notes.NotesDestination
import com.cdodi.ui.components.GlassShape
import com.cdodi.ui.components.drawThread
import com.cdodi.ui.components.focusRing
import com.cdodi.ui.components.warmth
import com.cdodi.ui.theme.Motion
import com.cdodi.ui.theme.SiteTheme
import com.cdodi.ui.theme.settling

/** A corner of Home's window, where a pane sits, and where its label grows from. */
enum class Corner(val alignment: Alignment, val origin: TransformOrigin, val top: Boolean, val start: Boolean) {
    TopStart(Alignment.TopStart, TransformOrigin(0f, 0f), top = true, start = true),
    TopEnd(Alignment.TopEnd, TransformOrigin(1f, 0f), top = true, start = false),
    BottomStart(Alignment.BottomStart, TransformOrigin(0f, 1f), top = false, start = true),
    BottomEnd(Alignment.BottomEnd, TransformOrigin(1f, 1f), top = false, start = false),
}

/**
 * The four panes of Home (DESIGN.md §8), in the top bar's order, which is also the order pages melt in ([menuOrder]).
 * Each opens its [destination].
 */
enum class MenuItem(val label: String, val number: String, val corner: Corner, val destination: Destination) {
    About("About", "01", Corner.TopStart, AboutDestination),
    Life("Game of Life", "02", Corner.TopEnd, LifeDestination()),
    Boids("Boids", "03", Corner.BottomStart, BoidsDestination),
    Notes("Notes", "04", Corner.BottomEnd, NotesDestination());

    /** Whether [destination] is this item's page, whatever it carries (a Life rule, a note). */
    fun isAt(destination: Destination): Boolean = destination.route == this.destination.route
}

/** A menu word: on its Home pane, or in the top bar. One movable content per word, so it can fly between the two. */
typealias MenuLabel = @Composable LookaheadScope.(modifier: Modifier, atHome: Boolean, active: Boolean) -> Unit

/** A menu word's line, as a multiple of its size. */
internal const val WORD_LINE = 1.1f

/**
 * The word for [item]. It's laid out at its top-bar size everywhere and drawn larger on Home, growing from its pane's
 * corner, so the flight between the two only moves and scales it ([animatePlacement], [morphScale]). On Home it keeps
 * the page's density (it flies), so its scale carries the window's type scale ([MenuLayout.textScale]). Hover and
 * focus warm it (and its pane, which shares [interaction]); in the top bar, the current page's word carries a lamp
 * thread.
 */
@Composable
fun rememberMenuLabel(item: MenuItem, interaction: MutableInteractionSource, onClick: () -> Unit): MenuLabel {
    val currentOnClick by rememberUpdatedState(onClick)
    return remember(item, interaction) {
        movableContentWithReceiverOf { modifier, atHome, active ->
            // Read in here, not around the remember: the word is made once, and must follow a change of theme.
            val colors = SiteTheme.colors
            val layout = LocalMenuLayout.current
            val hover by warmth(interaction)
            val warm = maxOf(hover, LocalPaneEffects.current.glow(item))
            val current by settling(active && !atHome, Motion.SHORT_SECONDS)
            val homeScale = layout.home.label.value / layout.navLabel.value * layout.textScale
            val colour = when {
                atHome -> lerp(colors.text, colors.bright, warm)
                active -> colors.bright
                else -> lerp(colors.muted, colors.bright, warm)
            }
            Text(
                text = item.label,
                style = SiteTheme.type.nav.copy(
                    fontSize = layout.navLabel,
                    lineHeight = layout.navLabel * WORD_LINE,
                    shadow = Shadow(colors.accent.copy(alpha = (if (atHome) 0.65f else 0.45f) * warm), blurRadius = if (atHome) 28f else 22f),
                ),
                color = colour,
                maxLines = 1,
                modifier = modifier
                    .animatePlacement(this)
                    .morphScale(if (atHome) homeScale else 1f, item.corner.origin)
                    .focusRing(interaction, GlassShape)
                    .clickable(interaction, indication = null, role = Role.Button, onClick = currentOnClick)
                    .drawBehind {
                        val y = size.height + 8.dp.toPx()
                        drawThread(colors.accent, Offset(size.width * 0.15f, y), Offset(size.width * 0.85f, y), alpha = current)
                    },
            )
        }
    }
}
