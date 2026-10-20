package com.cdodi.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.ambient
import com.cdodi.adapters.compose.shaderTime
import com.cdodi.design.components.GlassOutline
import com.cdodi.design.components.Thread
import com.cdodi.design.components.drawThread
import com.cdodi.design.components.glass
import com.cdodi.design.components.warmth
import com.cdodi.design.theme.SiteTheme
import com.cdodi.design.theme.lerp

/**
 * What a transition is doing to Home's panes right now (DESIGN.md §9, `wipe`): the chosen pane is wiped clear, the
 * others fog over, the chosen word catches the lamp. Each is 0..1, read while drawing.
 */
class PaneEffects(val wipe: (MenuItem) -> Float, val thicken: (MenuItem) -> Float, val glow: (MenuItem) -> Float) {
    companion object {
        val None = PaneEffects({ 0f }, { 0f }, { 0f })
    }
}

val LocalPaneEffects = staticCompositionLocalOf { PaneEffects.None }

/**
 * Home's window (DESIGN.md §8, the Home mockup): four fogged panes around the Welcome rhombus, corner marks around
 * them, drops sliding down the glass. Hovering a pane wipes its fog off, its label catches the lamp and its thread grows.
 *
 * All of it is laid out at its design size and scaled to [MenuLayout.window] ([AtDesignScale]), except what flies to
 * the pages, at the page's density: the words, and the rhombus, which becomes the page's glass.
 *
 * [labels] puts each word on its pane; null leaves the panes without words, while they dissolve and the words fly to
 * the top bar. [alpha] (read while drawing) is how solid the window is. [rhombus] is the Welcome pane.
 */
@Composable
fun HomeWindow(
    layout: MenuLayout,
    interactions: Map<MenuItem, MutableInteractionSource>,
    labels: (@Composable (MenuItem, Modifier) -> Unit)?,
    onOpen: (MenuItem) -> Unit,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    rhombus: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(layout.window)) {
        AtDesignScale(layout) {
            val home = layout.home
            Frame(home, Modifier.matchParentSize(), alpha)
            for (item in MenuItem.entries) {
                Pane(item, home, interactions.getValue(item), labels, alpha, Modifier.align(item.corner.alignment)) { onOpen(item) }
            }
            AtPageScale { rhombus() }
            Drops(Modifier.matchParentSize(), alpha)
        }
    }
}

/** Corner marks reaching [HomeDesign.marksReach] outside the window, and a faint frame 11.5 dp outside it. */
@Composable
private fun Frame(home: HomeDesign, modifier: Modifier, alpha: () -> Float) {
    val colors = SiteTheme.colors
    Canvas(modifier) {
        val a = alpha()
        if (a <= 0.001f) return@Canvas
        val out = home.marksReach.toPx()
        val mark = 24.dp.toPx()
        val stroke = Stroke(1.dp.toPx())
        val marks = Path().apply {
            val l = -out
            val t = -out
            val r = size.width + out
            val b = size.height + out
            moveTo(l, t + mark); lineTo(l, t); lineTo(l + mark, t)
            moveTo(r - mark, t); lineTo(r, t); lineTo(r, t + mark)
            moveTo(r, b - mark); lineTo(r, b); lineTo(r - mark, b)
            moveTo(l + mark, b); lineTo(l, b); lineTo(l, b - mark)
        }
        drawPath(marks, colors.bright.copy(alpha = 0.35f * a), style = stroke)
        val inset = 11.5.dp.toPx()
        drawRect(
            colors.bright.copy(alpha = 0.07f * a),
            topLeft = Offset(-inset, -inset),
            size = androidx.compose.ui.geometry.Size(size.width + inset * 2, size.height + inset * 2),
            style = stroke,
        )
    }
}

/** A pane: the right triangle in its quadrant's outer corner, [gap] off the window's diagonals. */
private fun triangle(corner: Corner, gap: Float) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    when (corner) {
        Corner.TopStart -> { moveTo(0f, 0f); lineTo(w - gap, 0f); lineTo(0f, h - gap) }
        Corner.TopEnd -> { moveTo(gap, 0f); lineTo(w, 0f); lineTo(w, h - gap) }
        Corner.BottomStart -> { moveTo(0f, gap); lineTo(0f, h); lineTo(w - gap, h) }
        Corner.BottomEnd -> { moveTo(w, gap); lineTo(w, h); lineTo(gap, h) }
    }
    close()
}

/** The pane's triangle as a quad for the GPU's blur: its third corner repeated. */
private fun triangleOutline(corner: Corner, gap: Float) = GlassOutline { w, h, out ->
    val points = when (corner) {
        Corner.TopStart -> floatArrayOf(0f, 0f, w - gap, 0f, 0f, h - gap)
        Corner.TopEnd -> floatArrayOf(gap, 0f, w, 0f, w, h - gap)
        Corner.BottomStart -> floatArrayOf(0f, gap, 0f, h, w - gap, h)
        Corner.BottomEnd -> floatArrayOf(w, gap, w, h, gap, h)
    }
    points.copyInto(out)
    out[6] = points[4]
    out[7] = points[5]
}

/** Whether [point] is inside the pane's triangle in a quadrant of [side]. */
private fun inTriangle(point: Offset, side: Float, corner: Corner): Boolean {
    val x = if (corner.start) point.x else side - point.x
    val y = if (corner.top) point.y else side - point.y
    return x >= 0 && y >= 0 && x + y <= side
}

@Composable
private fun Pane(
    item: MenuItem,
    home: HomeDesign,
    interaction: MutableInteractionSource,
    labels: (@Composable (MenuItem, Modifier) -> Unit)?,
    alpha: () -> Float,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val warm by warmth(interaction)
    val glass = SiteTheme.glass
    val effects = LocalPaneEffects.current
    val gap = with(LocalDensity.current) { home.paneGap.toPx() }
    val shape = remember(item, gap) { triangle(item.corner, gap) }
    val outline = remember(item, gap) { triangleOutline(item.corner, gap) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    Box(
        modifier
            .size(home.side / 2)
            .glass(
                shape = shape,
                style = { lerp(glass.homePane, glass.thick, effects.thicken(item)) },
                hairline = false,
                shadow = false,
                // Hover thins the fog most of the way (the storyboard); the wipe takes the rest.
                clear = { 0.7f * warm },
                alpha = alpha,
                outline = outline,
                wipe = { effects.wipe(item) },
            )
            // A pane only answers inside its triangle: the quadrants' other halves belong to the rhombus. While it
            // dissolves (no words on it), it doesn't answer at all.
            .then(if (labels == null) Modifier else Modifier.pointerInput(item) {
                var hover: HoverInteraction.Enter? = null
                try {
                    awaitPointerEventScope {
                        var pressedInside = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val inside = inTriangle(change.position, size.width.toFloat(), item.corner)
                            if (event.type == PointerEventType.Exit || !inside) {
                                hover?.let { interaction.tryEmit(HoverInteraction.Exit(it)) }
                                hover = null
                            } else if (hover == null) {
                                hover = HoverInteraction.Enter().also { interaction.tryEmit(it) }
                            }
                            when (event.type) {
                                PointerEventType.Press -> pressedInside = inside
                                PointerEventType.Release -> {
                                    if (pressedInside && inside) {
                                        change.consume()
                                        currentOnOpen()
                                    }
                                    pressedInside = false
                                }
                            }
                        }
                    }
                } finally {
                    // Cancelled with the pointer still over the pane (a transition takes the words away): the pane
                    // isn't hovered any more, or it would still be lit on the way back.
                    hover?.let { interaction.tryEmit(HoverInteraction.Exit(it)) }
                }
            }),
    ) {
        PaneWords(item, home, maxOf(warm, effects.glow(item)), labels, alpha, Modifier.align(item.corner.alignment))
    }
}

/**
 * The number, the word and the lamp rule in a pane's outer corner; on the lower panes, the other way up. At the
 * window's design size like the rest of it, so the rule grown on hover stays inside the triangle at any window size.
 */
@Composable
private fun PaneWords(item: MenuItem, home: HomeDesign, warm: Float, labels: (@Composable (MenuItem, Modifier) -> Unit)?, alpha: () -> Float, modifier: Modifier) {
    val colors = SiteTheme.colors
    val corner = item.corner
    val wordHeight = with(LocalDensity.current) { (home.label * WORD_LINE).toDp() }
    Column(
        modifier.padding(horizontal = home.insetX, vertical = home.insetY),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (corner.start) Alignment.Start else Alignment.End,
    ) {
        val number = @Composable {
            Text(item.number, style = SiteTheme.type.readout.copy(fontSize = 11.sp, letterSpacing = 0.2.em), color = colors.muted, modifier = Modifier.graphicsLayer { this.alpha = alpha() })
        }
        val rule = @Composable {
            Box(
                Modifier
                    .width(home.rule)
                    .height(1.dp)
                    .drawBehind {
                        val length = 28.dp.toPx() + (size.width - 28.dp.toPx()) * warm
                        val x0 = if (corner.start) 0f else size.width - length
                        drawThread(colors.accent, Offset(x0, 0.5f), Offset(x0 + length, 0.5f), alpha = (0.5f + 0.5f * warm) * alpha())
                    },
            )
        }
        val word = @Composable {
            Box(Modifier.height(wordHeight), contentAlignment = corner.alignment) {
                // The word flies to the top bar, so it's laid out at the page's density (see rememberMenuLabel).
                AtPageScale { labels?.invoke(item, Modifier) }
            }
        }
        if (corner.top) { number(); word(); rule() } else { rule(); word(); number() }
    }
}

/** A few drops sliding down the glass, on the ambient clock. */
@Composable
private fun Drops(modifier: Modifier, alpha: () -> Float) {
    val colors = SiteTheme.colors
    val time by LocalHeartbeat.current.ambient.shaderTime()
    val drops = remember {
        val random = kotlin.random.Random(77)
        List(9) { Triple(0.07f + random.nextFloat() * 0.86f, random.nextFloat() * 0.4f, 7f + random.nextFloat() * 6f) to random.nextFloat() }
    }
    Canvas(modifier) {
        val a = alpha()
        if (a <= 0.001f) return@Canvas
        val w = 6.dp.toPx()
        val h = 9.dp.toPx()
        for ((drop, phase) in drops) {
            val (x, top, period) = drop
            val t = ((time / period + phase) % 1f)
            // Slow at first, then faster, as a drop gathers weight; it fades in, and out at the end of its run.
            val eased = t * t * (1.6f - 0.6f * t)
            val y = top * size.height + eased * size.height * 0.57f
            val fade = (if (t < 0.08f) t / 0.08f else 1f) * (if (t > 0.7f) (1f - (t - 0.7f) / 0.3f) else 1f)
            val centre = Offset(x * size.width, y)
            drawOval(Color.Black.copy(alpha = 0.45f * fade * a), topLeft = centre + Offset(-w / 2, -h / 2 + 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(w, h))
            drawOval(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.6f * fade * a), colors.bright.copy(alpha = 0.08f * fade * a)),
                    center = centre + Offset(-w * 0.15f, -h * 0.2f),
                    radius = w * 0.6f,
                    tileMode = TileMode.Clamp,
                ),
                topLeft = centre + Offset(-w / 2, -h / 2),
                size = androidx.compose.ui.geometry.Size(w, h),
            )
        }
    }
}

/** The page's density, inside Home's window ([AtDesignScale]), where [LocalDensity] is the window's. */
private val LocalPageDensity = staticCompositionLocalOf<Density> { error("Not inside Home's window (AtDesignScale)") }

/**
 * Lays [content] out at Home's design size ([MenuLayout.home]), scaled to the window: inside, a dp is
 * [MenuLayout.windowScale] of the page's, and type is [MenuLayout.textScale] of its size, so the design's words, rules
 * and gaps keep their places on the panes whatever the window's size. [AtPageScale] steps back out.
 */
@Composable
private fun AtDesignScale(layout: MenuLayout, content: @Composable () -> Unit) {
    val page = LocalDensity.current
    CompositionLocalProvider(
        LocalPageDensity provides page,
        LocalDensity provides Density(page.density * layout.windowScale, page.fontScale * layout.textScale / layout.windowScale),
        content = content,
    )
}

/** Lays [content] out at the page's density inside Home's window: for what flies between Home and the pages. */
@Composable
private fun AtPageScale(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides LocalPageDensity.current, content = content)
}

/** What the Welcome rhombus says (the Home mockup), at the window's design size. */
@Composable
fun WelcomeWords(layout: MenuLayout, name: String) = AtDesignScale(layout) {
    val home = layout.home
    val colors = SiteTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(home.welcomeGap)) {
        Text("Welcome", style = SiteTheme.type.display.copy(fontSize = home.welcome, lineHeight = home.welcome), color = colors.bright)
        Thread(44.dp)
        Text(name.uppercase(), style = SiteTheme.type.brand, color = colors.muted)
        Text("stay a while — it's raining", style = SiteTheme.type.verse.copy(fontSize = home.verse), color = colors.text.copy(alpha = 0.8f))
    }
}

/** The colophon on Home. */
@Composable
fun Colophon(modifier: Modifier = Modifier) {
    Text("painted with WebGPU · Kotlin/Wasm", style = SiteTheme.type.readout.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = SiteTheme.colors.muted, modifier = modifier)
}
