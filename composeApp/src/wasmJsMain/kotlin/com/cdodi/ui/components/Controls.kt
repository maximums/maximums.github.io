package com.cdodi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.ui.theme.ColorRoles
import com.cdodi.ui.theme.Motion
import com.cdodi.ui.theme.SiteTheme
import com.cdodi.ui.theme.settling
import kotlin.math.roundToInt

/**
 * DESIGN.md §6, Slider: a 1 px thread (Lamp up to the value, WetStone after) and a falling-drop thumb that glows when
 * touched. [label] and [valueText] sit above it. Arrow keys move it by a [steps]th of the range. [onChangeFinished]
 * runs when a drag or tap ends (the flock size applies only then).
 */
@Composable
fun DripSlider(
    label: String,
    valueText: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 100,
    onChangeFinished: () -> Unit = {},
) {
    val colors = SiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val warm by warmth(interaction)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentFinished by rememberUpdatedState(onChangeFinished)
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    fun valueAt(x: Float, width: Float) = range.start + (x / width).coerceIn(0f, 1f) * (range.endInclusive - range.start)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = SiteTheme.type.small, color = colors.text, modifier = Modifier.weight(1f))
            Text(valueText, style = SiteTheme.type.readout, color = colors.muted)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    setProgress { target -> currentOnChange(target.coerceIn(range)); currentFinished(); true }
                }
                .focusRing(interaction, RoundedCornerShape(2.dp))
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = (range.endInclusive - range.start) / steps
                    val next = when (event.key) {
                        Key.DirectionRight, Key.DirectionUp -> value + step
                        Key.DirectionLeft, Key.DirectionDown -> value - step
                        Key.MoveHome -> range.start
                        Key.MoveEnd -> range.endInclusive
                        else -> return@onKeyEvent false
                    }
                    currentOnChange(next.coerceIn(range))
                    currentFinished()
                    true
                }
                .focusable(interactionSource = interaction)
                .pointerInput(range) {
                    detectTapGestures { offset -> currentOnChange(valueAt(offset.x, size.width.toFloat())); currentFinished() }
                }
                .pointerInput(range) {
                    detectHorizontalDragGestures(
                        onDragEnd = { currentFinished() },
                        onDragCancel = { currentFinished() },
                    ) { change, _ -> currentOnChange(valueAt(change.position.x, size.width.toFloat())) }
                }
                .drawBehind {
                    val y = size.height / 2
                    val x = size.width * fraction
                    val thread = 1.dp.toPx()
                    drawLine(colors.stroke, Offset(x, y), Offset(size.width, y), thread)
                    drawLine(colors.accent, Offset(0f, y), Offset(x, y), thread)
                    drawDrop(Offset(x, y), 0.55f + 0.45f * warm, colors)
                },
        )
    }
}

/** The slider's thumb: a bright teardrop, its point up, as if it were falling, with a glow of the accent. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDrop(centre: Offset, glow: Float, colors: ColorRoles) {
    val side = 12.dp.toPx()
    // A square with three round corners, turned 45° so the sharp one points up: a drop.
    val shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 0, bottomEndPercent = 50, bottomStartPercent = 50)
    translate(centre.x - side / 2, centre.y - side / 2) {
        rotate(-45f, pivot = Offset(side / 2, side / 2)) {
            drawSoft(shape, Offset(-3.dp.toPx(), -3.dp.toPx()), Size(side + 6.dp.toPx(), side + 6.dp.toPx()), colors.accent.copy(alpha = 0.55f * glow), 14.dp.toPx())
            val outline = shape.createOutline(Size(side, side), layoutDirection, this)
            drawPath(Path().apply { addOutline(outline) }, colors.bright)
        }
    }
}

/**
 * DESIGN.md §6, Choice: options in the serif over one hairline; the chosen one in Parchment over a lamp thread.
 */
@Composable
fun <T> Choice(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, textSize: TextUnit = 19.sp) {
    val colors = SiteTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind { drawLine(colors.hairline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) },
    ) {
        for ((name, option) in options) {
            val interaction = remember { MutableInteractionSource() }
            val warm by warmth(interaction)
            val on = option == selected
            val chosen by settling(on, Motion.SHORT_SECONDS)
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .focusRing(interaction, RoundedCornerShape(2.dp))
                    .clickable(interaction, indication = null, role = Role.Tab) { onSelect(option) }
                    .drawBehind {
                        val y = size.height - 0.5f
                        drawThread(colors.accent, Offset(size.width * 0.25f, y), Offset(size.width * 0.75f, y), alpha = chosen)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    style = SiteTheme.type.nav.copy(fontSize = textSize),
                    color = if (on) colors.bright else lerp(colors.muted, colors.text, warm),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** DESIGN.md §6, Toggle: a row with a 40×20 outline pill whose knob glows when on. */
@Composable
fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = SiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val knob by settling(on, Motion.SHORT_SECONDS)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .focusRing(interaction, RoundedCornerShape(2.dp))
            .clickable(interaction, indication = null, role = Role.Switch) { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = SiteTheme.type.small, color = colors.text, modifier = Modifier.weight(1f))
        val pill = RoundedCornerShape(10.dp)
        Box(
            Modifier
                .size(40.dp, 20.dp)
                .border(1.dp, lerp(colors.bright.copy(alpha = 0.2f), colors.accent.copy(alpha = 0.6f), knob), pill)
                .drawBehind {
                    val r = 6.dp.toPx()
                    val x = 3.dp.toPx() + r + knob * 20.dp.toPx()
                    val centre = Offset(x, size.height / 2)
                    if (knob > 0f) drawSoft(androidx.compose.foundation.shape.CircleShape, centre - Offset(r * 1.6f, r * 1.6f), Size(r * 3.2f, r * 3.2f), colors.accent.copy(alpha = 0.7f * knob), 12.dp.toPx())
                    drawCircle(lerp(colors.muted, colors.bright, knob), r, centre)
                },
        )
    }
}

/**
 * DESIGN.md §6, Readout: mono rows of a name, a 1 px bar with a glow and a value. The dominant row is Lamp, the rest
 * Rain. [share] is the bar's length, 0 to 1.
 */
@Composable
fun ReadoutRow(name: String, value: String, share: Float, dominant: Boolean, modifier: Modifier = Modifier, barWidth: Dp = 160.dp) {
    val colors = SiteTheme.colors
    val colour = if (dominant) colors.accent else colors.cold
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, style = SiteTheme.type.readout, color = colors.muted, modifier = Modifier.size(width = 88.dp, height = 18.dp))
        Box(
            Modifier
                .weight(1f)
                .height(9.dp)
                .drawBehind {
                    val length = (barWidth.toPx().coerceAtMost(size.width) * share.coerceIn(0f, 1f)).coerceAtLeast(4.dp.toPx())
                    val y = size.height / 2
                    drawSoft(RoundedCornerShape(1.dp), Offset(0f, y - 1.dp.toPx()), Size(length, 2.dp.toPx()), colour.copy(alpha = 0.6f), 8.dp.toPx())
                    drawLine(colour.copy(alpha = 0.9f), Offset(0f, y), Offset(length, y), 1.dp.toPx())
                },
        )
        Text(value, style = SiteTheme.type.readout, color = colors.muted, textAlign = TextAlign.End, modifier = Modifier.size(width = 64.dp, height = 18.dp))
    }
}

/** Formats [value] to [decimals] places without a locale (Kotlin/Wasm has no String.format). */
fun formatDecimal(value: Double, decimals: Int): String {
    var scale = 1
    repeat(decimals) { scale *= 10 }
    val scaled = (value * scale).roundToInt()
    if (decimals == 0) return scaled.toString()
    val sign = if (scaled < 0) "-" else ""
    val whole = kotlin.math.abs(scaled) / scale
    val fraction = (kotlin.math.abs(scaled) % scale).toString().padStart(decimals, '0')
    return "$sign$whole.$fraction"
}

/** 120 000: thousands apart by a thin space, as in the mockups' readouts. */
fun formatThousands(value: Int): String = value.toString().reversed().chunked(3).joinToString(" ").reversed()

