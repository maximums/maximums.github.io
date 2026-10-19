package com.cdodi.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.ambient
import com.cdodi.adapters.compose.shaderTime
import com.cdodi.components.animatePlacement
import com.cdodi.components.morphScale
import com.cdodi.ui.components.GlassOutline
import com.cdodi.ui.components.GlassShape
import com.cdodi.ui.components.drawThread
import com.cdodi.ui.components.focusRing
import com.cdodi.ui.components.glass
import com.cdodi.ui.components.warmth
import com.cdodi.ui.theme.Autumn
import com.cdodi.ui.theme.AutumnColors
import com.cdodi.ui.theme.Motion
import com.cdodi.ui.theme.settling

/** Home's panes are a little thinner than a fog pane (the Home mockup). */
private val PaneFog = Color(18, 22, 26).copy(alpha = 0.52f)

/** How thick the other panes fog over while the chosen one is wiped. */
private val ThickFog = Color(18, 22, 26).copy(alpha = 0.8f)

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

/** A corner of Home's window, where a pane sits, and where its label grows from. */
enum class Corner(val alignment: Alignment, val origin: TransformOrigin, val top: Boolean, val start: Boolean) {
    TopStart(Alignment.TopStart, TransformOrigin(0f, 0f), top = true, start = true),
    TopEnd(Alignment.TopEnd, TransformOrigin(1f, 0f), top = true, start = false),
    BottomStart(Alignment.BottomStart, TransformOrigin(0f, 1f), top = false, start = true),
    BottomEnd(Alignment.BottomEnd, TransformOrigin(1f, 1f), top = false, start = false),
}

/** The four panes of Home (DESIGN.md §8), in the top bar's order. */
enum class MenuItem(val label: String, val number: String, val corner: Corner) {
    About("About", "01", Corner.TopStart),
    Life("Game of Life", "02", Corner.TopEnd),
    Boids("Boids", "03", Corner.BottomStart),
    Notes("Notes", "04", Corner.BottomEnd),
}

/** A menu word: on its Home pane, or in the top bar. One movable content per word, so it can fly between the two. */
typealias MenuLabel = @Composable LookaheadScope.(modifier: Modifier, atHome: Boolean, active: Boolean) -> Unit

/**
 * The word for [item]. It's laid out at its top-bar size everywhere and drawn larger on Home, growing from its pane's
 * corner, so the flight between the two only moves and scales it ([animatePlacement], [morphScale]). On Home it keeps
 * the page's density (it flies), so its scale carries the window's ([MenuLayout.windowScale]). Hover and focus warm it
 * (and its pane, which shares [interaction]); in the top bar, the current page's word carries a lamp thread.
 */
@Composable
fun rememberMenuLabel(item: MenuItem, interaction: MutableInteractionSource, onClick: () -> Unit): MenuLabel {
    val currentOnClick by rememberUpdatedState(onClick)
    return remember(item, interaction) {
        movableContentWithReceiverOf { modifier, atHome, active ->
            val layout = LocalMenuLayout.current
            val hover by warmth(interaction)
            val warm = maxOf(hover, LocalPaneEffects.current.glow(item))
            val current by settling(active && !atHome, Motion.SHORT_SECONDS)
            val homeScale = layout.homeLabel.value / layout.navLabel.value * layout.windowScale
            val colour = when {
                atHome -> lerp(AutumnColors.Mist, AutumnColors.Parchment, warm)
                active -> AutumnColors.Parchment
                else -> lerp(AutumnColors.Fog, AutumnColors.Parchment, warm)
            }
            Text(
                text = item.label,
                style = Autumn.type.nav.copy(
                    fontSize = layout.navLabel,
                    lineHeight = layout.navLabel * 1.1f,
                    shadow = Shadow(AutumnColors.Lamp.copy(alpha = (if (atHome) 0.65f else 0.45f) * warm), blurRadius = if (atHome) 28f else 22f),
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
                        drawThread(Offset(size.width * 0.15f, y), Offset(size.width * 0.85f, y), alpha = current)
                    },
            )
        }
    }
}

/**
 * Home's window (DESIGN.md §8, the Home mockup): four fogged panes around the Welcome rhombus, corner marks around
 * them, drops sliding down the glass. Hovering a pane wipes its fog off, its label catches the lamp and its thread grows.
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
        Frame(Modifier.matchParentSize(), alpha)
        for (item in MenuItem.entries) {
            Pane(item, layout, interactions.getValue(item), labels, alpha, Modifier.align(item.corner.alignment)) { onOpen(item) }
        }
        rhombus()
        Drops(Modifier.matchParentSize(), alpha)
    }
}

/** Corner marks 20 dp outside the window, and a faint frame 8 dp outside it. */
@Composable
private fun Frame(modifier: Modifier, alpha: () -> Float) {
    Canvas(modifier) {
        val a = alpha()
        if (a <= 0.001f) return@Canvas
        val out = 20.dp.toPx()
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
        drawPath(marks, AutumnColors.Parchment.copy(alpha = 0.35f * a), style = stroke)
        val inset = 11.5.dp.toPx()
        drawRect(
            AutumnColors.Parchment.copy(alpha = 0.07f * a),
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
    layout: MenuLayout,
    interaction: MutableInteractionSource,
    labels: (@Composable (MenuItem, Modifier) -> Unit)?,
    alpha: () -> Float,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val warm by warmth(interaction)
    val effects = LocalPaneEffects.current
    val density = LocalDensity.current
    val gap = with(density) { 10.dp.toPx() } * layout.windowScale
    val shape = remember(item, gap) { triangle(item.corner, gap) }
    val outline = remember(item, gap) { triangleOutline(item.corner, gap) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    Box(
        modifier
            .size(layout.pane)
            .glass(
                shape = shape,
                fill = { lerp(PaneFog, ThickFog, effects.thicken(item)) },
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
                awaitPointerEventScope {
                    var hover: HoverInteraction.Enter? = null
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
            }),
    ) {
        PaneWords(item, layout, maxOf(warm, effects.glow(item)), labels, alpha, Modifier.align(item.corner.alignment))
    }
}

/**
 * The number, the word and the lamp rule in a pane's outer corner; on the lower panes, the other way up. Laid out at
 * the window's design size ([AtDesignScale]): the rule grown on hover stays inside the triangle at any window size.
 */
@Composable
private fun PaneWords(item: MenuItem, layout: MenuLayout, warm: Float, labels: (@Composable (MenuItem, Modifier) -> Unit)?, alpha: () -> Float, modifier: Modifier) {
    val pageDensity = LocalDensity.current
    AtDesignScale(layout) { PaneWordsAtDesign(item, layout, warm, labels, alpha, pageDensity, modifier) }
}

@Composable
private fun PaneWordsAtDesign(
    item: MenuItem,
    layout: MenuLayout,
    warm: Float,
    labels: (@Composable (MenuItem, Modifier) -> Unit)?,
    alpha: () -> Float,
    pageDensity: Density,
    modifier: Modifier,
) {
    val corner = item.corner
    val wordHeight = with(LocalDensity.current) { (layout.homeLabel * 1.1f).toDp() }
    val ruleLength = if (layout.compact) 64.dp else 110.dp
    Column(
        modifier
            .padding(horizontal = if (layout.compact) 18.dp else 36.dp, vertical = if (layout.compact) 18.dp else 38.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (corner.start) Alignment.Start else Alignment.End,
    ) {
        val number = @Composable {
            Text(item.number, style = Autumn.type.readout.copy(fontSize = 11.sp, letterSpacing = 0.2.em), color = AutumnColors.Fog, modifier = Modifier.graphicsLayer { this.alpha = alpha() })
        }
        val rule = @Composable {
            Box(
                Modifier
                    .width(ruleLength)
                    .height(1.dp)
                    .drawBehind {
                        val length = 28.dp.toPx() + (size.width - 28.dp.toPx()) * warm
                        val x0 = if (corner.start) 0f else size.width - length
                        drawThread(Offset(x0, 0.5f), Offset(x0 + length, 0.5f), alpha = (0.5f + 0.5f * warm) * alpha())
                    },
            )
        }
        val word = @Composable {
            Box(Modifier.height(wordHeight), contentAlignment = corner.alignment) {
                // The word flies to the top bar, so it's laid out at the page's density (see rememberMenuLabel).
                CompositionLocalProvider(LocalDensity provides pageDensity) { labels?.invoke(item, Modifier) }
            }
        }
        if (corner.top) { number(); word(); rule() } else { rule(); word(); number() }
    }
}

/** A few drops sliding down the glass, on the ambient clock. */
@Composable
private fun Drops(modifier: Modifier, alpha: () -> Float) {
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
                    listOf(Color.White.copy(alpha = 0.6f * fade * a), AutumnColors.Parchment.copy(alpha = 0.08f * fade * a)),
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

/**
 * Lays [content] out at Home's design size, scaled to the window: inside, a dp is [MenuLayout.windowScale] of the
 * page's, so the mockup's words, rules and gaps keep their proportions to the panes whatever the window's size.
 */
@Composable
private fun AtDesignScale(layout: MenuLayout, content: @Composable () -> Unit) {
    val page = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(page.density * layout.windowScale, page.fontScale), content = content)
}

/** What the Welcome rhombus says (the Home mockup), at the window's design size. */
@Composable
fun WelcomeWords(layout: MenuLayout, name: String) = AtDesignScale(layout) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (layout.compact) 12.dp else 18.dp)) {
        Text("Welcome", style = Autumn.type.display.copy(fontSize = layout.welcome, lineHeight = layout.welcome), color = AutumnColors.Parchment)
        Box(Modifier.width(44.dp).height(1.dp).drawBehind { drawThread(Offset(0f, 0.5f), Offset(size.width, 0.5f)) })
        Text(name.uppercase(), style = Autumn.type.brand, color = AutumnColors.Fog)
        Text("stay a while — it's raining", style = Autumn.type.verse.copy(fontSize = if (layout.compact) 16.sp else 19.sp), color = AutumnColors.Mist.copy(alpha = 0.8f))
    }
}

/**
 * The slim top bar (DESIGN.md §8, shared): the brand mark and name on the left, which go Home, and the menu's words in
 * the centre. [solid] (read while drawing) is how far it has condensed onto the glass: from blurred and faint to sharp.
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
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(layout.barHeight)
            .drawBehind {
                val a = solid()
                if (a <= 0.001f) return@drawBehind
                drawRect(Brush.verticalGradient(listOf(Color(10, 13, 16).copy(alpha = 0.85f * a), Color(10, 13, 16).copy(alpha = 0.35f * a))))
                drawLine(AutumnColors.Parchment.copy(alpha = 0.06f * a), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            },
    ) {
        val condensing = Modifier.graphicsLayer {
            val a = solid()
            alpha = a
            val blur = (1f - a) * 8.dp.toPx()
            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
        }
        // On a phone the words take the right, so [end] sits by the brand; elsewhere it has the right to itself.
        Row(Modifier.align(Alignment.CenterStart).padding(start = layout.margin).then(condensing), verticalAlignment = Alignment.CenterVertically) {
            Brand(layout, name, onHome, Modifier)
            if (layout.compact) end()
        }
        if (!layout.compact) Box(Modifier.align(Alignment.CenterEnd).padding(end = layout.margin).then(condensing)) { end() }
        Row(
            Modifier.align(if (layout.compact) Alignment.CenterEnd else Alignment.Center).padding(end = if (layout.compact) layout.margin else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(layout.navGap),
            verticalAlignment = Alignment.CenterVertically,
            content = words,
        )
    }
}

/** The lamp-lit diamond and the name (on a phone, the diamond alone). */
@Composable
private fun Brand(layout: MenuLayout, name: String, onHome: () -> Unit, modifier: Modifier) {
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
            drawPath(path, AutumnColors.Lamp, style = Stroke(1.dp.toPx()))
        }
        if (!layout.compact) Text(name.uppercase(), style = Autumn.type.brand, color = lerp(AutumnColors.Mist, AutumnColors.Parchment, warm))
    }
}

/** A hint in a corner: spaced-out mono capitals (DESIGN.md §8). */
@Composable
fun CornerHint(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Autumn.type.hint, color = AutumnColors.Fog, modifier = modifier)
}

/**
 * A line of verse over the fog while the next scene gathers itself (DESIGN.md §9, `holdFog`), with a thread of light
 * under it. [alpha] is read while drawing.
 */
@Composable
fun Caption(text: String, alpha: () -> Float, modifier: Modifier = Modifier) {
    Column(
        modifier.graphicsLayer { this.alpha = alpha() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = Autumn.type.verse.copy(fontSize = 24.sp), color = AutumnColors.Parchment)
        Box(Modifier.width(56.dp).height(1.dp).drawBehind { drawThread(Offset(0f, 0.5f), Offset(size.width, 0.5f)) })
    }
}

/** The colophon on Home. */
@Composable
fun Colophon(modifier: Modifier = Modifier) {
    Text("painted with WebGPU · Kotlin/Wasm", style = Autumn.type.readout.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = AutumnColors.Fog, modifier = modifier)
}
