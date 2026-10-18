package com.cdodi.ui.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cdodi.ui.theme.AutumnColors
import com.cdodi.ui.theme.settling
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint

/**
 * DESIGN.md §2: light gathers, nothing jumps. Hover and focus *warm* an element; they never move or scale it. This is
 * how warm it is, 0 to 1, settling over 1.3 s on the heartbeat.
 */
@Composable
fun warmth(interaction: InteractionSource, alsoWhen: Boolean = false): State<Float> {
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    return settling(hovered || focused || alsoWhen)
}

/**
 * Soft light around [shape]: a lamp glow (DESIGN.md: `0 0 24px -8px Lamp @ 60 %` on buttons), or a shadow under a
 * pane. [strength] is read while drawing, so a warming glow redraws without recomposing.
 */
fun Modifier.glow(color: Color, radius: Dp, shape: Shape, spread: Dp = 0.dp, strength: () -> Float = { 1f }): Modifier = drawBehind {
    val alpha = strength()
    if (alpha <= 0.001f) return@drawBehind
    val grow = spread.toPx()
    drawSoft(shape, Offset(-grow, -grow), Size(size.width + grow * 2, size.height + grow * 2), color.copy(alpha = color.alpha * alpha), radius.toPx())
}

/** [shape] at [offset] × [size], blurred by [radius] (Skia's blur mask: the GPU does it, nothing is rasterised here). */
fun DrawScope.drawSoft(shape: Shape, offset: Offset, size: Size, color: Color, radius: Float) {
    val path = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawSoft))
        translate(offset)
    }
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = color.toArgb()
            isAntiAlias = true
            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, (radius / 2f).coerceAtLeast(0.5f))
        }
        canvas.nativeCanvas.drawPath(path.asSkiaPath(), paint)
        paint.close()
    }
}

/**
 * DESIGN.md §6: focus is always visible, as a 1 px Lamp ring 3 px outside the element. Drawn only while the keyboard
 * is in use: a click focuses an element too, and shouldn't leave a ring behind.
 */
@Composable
fun Modifier.focusRing(interaction: InteractionSource, shape: Shape): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val inputMode = LocalInputModeManager.current
    return drawWithContent {
        drawContent()
        if (focused && inputMode.inputMode == InputMode.Keyboard) {
            val offset = 3.dp.toPx()
            val outline = shape.createOutline(Size(size.width + offset * 2, size.height + offset * 2), layoutDirection, this)
            val path = Path().apply { addOutline(outline); translate(Offset(-offset, -offset)) }
            drawPath(path, AutumnColors.Lamp, style = Stroke(1.dp.toPx()))
        }
    }
}

/** A 1 px thread of lamp light from [start] to [end], glowing (the active navigation item, a chosen option, a rule). */
fun DrawScope.drawThread(start: Offset, end: Offset, alpha: Float = 1f, glow: Float = 1f) {
    if (alpha <= 0.001f) return
    val thickness = 1.dp.toPx()
    if (glow > 0f) {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = AutumnColors.Lamp.copy(alpha = 0.7f * alpha * glow).toArgb()
                isAntiAlias = true
                strokeWidth = 3.dp.toPx()
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 6.dp.toPx())
            }
            canvas.nativeCanvas.drawLine(start.x, start.y, end.x, end.y, paint)
            paint.close()
        }
    }
    drawLine(AutumnColors.Lamp.copy(alpha = alpha), start, end, strokeWidth = thickness)
}
