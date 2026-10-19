package com.cdodi.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.compose.LocalGpuWidgets
import com.cdodi.adapters.compose.LocalPageVisibility
import com.cdodi.adapters.gpu.GpuWidgets
import com.cdodi.ui.theme.LocalTheme
import kotlin.random.Random

/** DESIGN.md §5: panes have a 2 px radius. */
val GlassShape = RoundedCornerShape(2.dp)

/**
 * A pane's outline for the GPU, which blurs the street behind it: a convex quad in the pane's own pixels, written into
 * 8 floats (a triangle repeats a corner). Asked every frame, so it can follow a morph.
 */
fun interface GlassOutline {
    fun corners(width: Float, height: Float, out: FloatArray)

    companion object {
        val Rectangle = GlassOutline { w, h, out ->
            out[0] = 0f; out[1] = 0f; out[2] = w; out[3] = 0f
            out[4] = w; out[5] = h; out[6] = 0f; out[7] = h
        }
    }
}

/**
 * DESIGN.md §5, the fog pane: wet glass over the scene.
 *
 * Compose draws the fog fill, fine condensation, a 1 px hairline, an inner top highlight and a long soft shadow. The
 * GPU draws what Compose can't see: the street behind the pane, blurred and desaturated. The pane reports itself (its
 * [outline]) to the scene host, and the blur follows how fogged the glass is, so a cleared pane shows the street sharp.
 *
 * [clear] (0 fogged, 1 cleared) wipes the fog off, as hover and focus do. [fill] replaces the theme's fog pane for
 * panes a little thicker or thinner (its [GlassTokens][com.cdodi.ui.theme.GlassTokens]). [wipe] (0 to 1) sweeps a band of clearness across the pane
 * from left to right, leaving it clear ("Breath on the window" starts so). The lambdas are read every frame.
 */
fun Modifier.glass(
    shape: Shape = GlassShape,
    fill: (() -> Color)? = null,
    hairline: Boolean = true,
    shadow: Boolean = true,
    clear: () -> Float = { 0f },
    alpha: () -> Float = { 1f },
    outline: GlassOutline = GlassOutline.Rectangle,
    wipe: () -> Float = { 0f },
): Modifier = this then GlassElement(shape, fill, hairline, shadow, clear, alpha, outline, wipe)

private data class GlassElement(
    val shape: Shape,
    val fill: (() -> Color)?,
    val hairline: Boolean,
    val shadow: Boolean,
    val clear: () -> Float,
    val alpha: () -> Float,
    val outline: GlassOutline,
    val wipe: () -> Float,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(this)

    override fun update(node: GlassNode) {
        node.style = this
    }
}

private class GlassNode(var style: GlassElement) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var registry: GpuWidgets? = null

    // What the last frame drew, for the GPU's part, which runs once Compose has drawn the frame.
    private var fogginess = 0f
    private var opacity = 0f

    override fun ContentDrawScope.draw() {
        drawGlass()
        drawContent()
    }

    private fun DrawScope.drawGlass() {
        val s = style
        opacity = s.alpha()
        if (opacity <= 0.001f) {
            fogginess = 0f
            return
        }
        val cleared = s.clear().coerceIn(0f, 1f)
        val wipe = s.wipe().coerceIn(0f, 1f)
        val path = Path().apply { addOutline(s.shape.createOutline(size, layoutDirection, this@drawGlass)) }

        // `0 40px 80px -40px rgba(0,0,0,.85)`: a shadow well below the pane, as if it hung in front of the street.
        if (s.shadow) {
            val spread = 40.dp.toPx()
            drawSoft(s.shape, Offset(spread, spread + 40.dp.toPx()), Size((size.width - spread * 2).coerceAtLeast(1f), (size.height - spread * 2).coerceAtLeast(1f)), Color.Black.copy(alpha = 0.85f * opacity), 80.dp.toPx())
        }
        val theme = currentValueOf(LocalTheme)
        val glass = theme.glass
        val fog = lerp(s.fill?.invoke() ?: glass.fogged, glass.cleared, cleared)
        // Condensation and blur gather where the glass is fogged: none on a cleared pane.
        fogginess = ((fog.alpha - glass.cleared.alpha) / (glass.fogged.alpha - glass.cleared.alpha)).coerceIn(0f, 1f)
        if (wipe <= 0f) {
            drawPath(path, fog.copy(alpha = fog.alpha * opacity))
        } else {
            // The wiped band, as the GPU computes it (panes.wgsl, wiped()), sampled into a gradient across the pane.
            val stops = Array(WIPE_STOPS + 1) { i ->
                val d = i / WIPE_STOPS.toFloat()
                val c = lerp(fog, glass.cleared, wiped(wipe, d))
                d to c.copy(alpha = c.alpha * opacity)
            }
            drawPath(path, Brush.horizontalGradient(*stops))
        }
        if (fogginess > 0.001f) clipPath(path) { drawRect(Condensation.brush(theme.colors.bright), alpha = 0.7f * fogginess * (1f - wipe) * opacity) }
        if (s.hairline) {
            val hairline = theme.colors.hairline
            drawPath(path, hairline.copy(alpha = hairline.alpha * opacity), style = Stroke(1.dp.toPx()))
            // The inner top highlight: light catching the pane's upper edge.
            drawLine(theme.colors.bright.copy(alpha = 0.06f * opacity), Offset(1.dp.toPx(), 1.5.dp.toPx()), Offset(size.width - 1.dp.toPx(), 1.5.dp.toPx()), 1.dp.toPx())
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val widgets = registry ?: currentValueOf(LocalGpuWidgets)?.also { registry = it } ?: return
        val bounds = coordinates.boundsInWindow()
        val page = currentValueOf(LocalPageVisibility)
        widgets.reportPane(
            this,
            GpuWidgets.Pane(
                left = bounds.left,
                top = bounds.top,
                width = bounds.width,
                height = bounds.height,
                corners = { w, h, out -> style.outline.corners(w, h, out) },
                blur = { fogginess },
                wipe = { style.wipe() },
                visibility = { opacity * page() },
            ),
        )
    }

    override fun onDetach() {
        registry?.removePane(this)
        registry = null
    }

    private companion object {
        const val WIPE_STOPS = 12
    }
}

/** How cleared a wipe at [progress] has left a point [d] of the way across: panes.wgsl's `wiped`, kept in step. */
private fun wiped(progress: Float, d: Float): Float {
    val softness = 0.3f
    val front = progress * (1f + softness)
    val t = ((d - (front - softness)) / softness).coerceIn(0f, 1f)
    return 1f - t * t * (3f - 2f * t)
}

/** Condensation: fine drops of water on the glass, one tile drawn once per colour and repeated. */
private object Condensation {
    private const val TILE = 256

    private var drawn: Pair<Color, ShaderBrush>? = null

    /** The tile in [color]; drawn again only when the theme changes it. */
    fun brush(color: Color): ShaderBrush = drawn?.takeIf { it.first == color }?.second ?: draw(color).also { drawn = color to it }

    private fun draw(color: Color): ShaderBrush {
        val bitmap = ImageBitmap(TILE, TILE)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val random = Random(17)
        repeat(150) {
            val radius = 0.5f + random.nextFloat() * 0.8f
            paint.color = color.copy(alpha = 0.12f + random.nextFloat() * 0.12f)
            val x = random.nextFloat() * TILE
            val y = random.nextFloat() * TILE
            // An oval a little taller than wide: water gathers at a drop's lower edge.
            canvas.drawOval(x - radius, y - radius * 1.4f, x + radius, y + radius * 1.4f, paint)
        }
        return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
}
