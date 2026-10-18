package com.cdodi.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.cdodi.ui.theme.AutumnColors
import kotlin.random.Random

/** DESIGN.md §5: panes have a 2 px radius. */
val GlassShape = RoundedCornerShape(2.dp)

/**
 * DESIGN.md §5, the fog pane: wet glass over the scene. The fog fill, fine condensation, a 1 px hairline, an inner top
 * highlight and a long soft shadow. [clear] (0 fogged, 1 cleared) wipes the fog off, as hover and focus do. [fill]
 * replaces the default fog for panes a little thicker or thinner (the Welcome rhombus is 62 %). The lambdas are read
 * while drawing.
 *
 * The blur of the scene behind the pane is the GPU's part (PLAN Phase 8b): Compose can't see the WebGPU canvas.
 */
fun Modifier.glass(
    shape: Shape = GlassShape,
    fill: () -> Color = { AutumnColors.FogPane },
    hairline: Boolean = true,
    shadow: Boolean = true,
    clear: () -> Float = { 0f },
    alpha: () -> Float = { 1f },
): Modifier = drawBehind {
    val opacity = alpha()
    if (opacity <= 0.001f) return@drawBehind
    val cleared = clear().coerceIn(0f, 1f)
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }

    // `0 40px 80px -40px rgba(0,0,0,.85)`: a shadow well below the pane, as if it hung in front of the street.
    if (shadow) {
        val spread = 40.dp.toPx()
        drawSoft(shape, Offset(spread, spread + 40.dp.toPx()), Size((size.width - spread * 2).coerceAtLeast(1f), (size.height - spread * 2).coerceAtLeast(1f)), Color.Black.copy(alpha = 0.85f * opacity), 80.dp.toPx())
    }
    val fog = lerp(fill(), AutumnColors.ClearedPane, cleared)
    drawPath(path, fog.copy(alpha = fog.alpha * opacity))
    // Condensation gathers where the glass is fogged: none on a cleared pane.
    val fogginess = ((fog.alpha - AutumnColors.ClearedPane.alpha) / (AutumnColors.FogPane.alpha - AutumnColors.ClearedPane.alpha)).coerceIn(0f, 1f)
    if (fogginess > 0.001f) clipPath(path) { drawRect(Condensation.brush, alpha = 0.7f * fogginess * opacity) }
    if (hairline) {
        drawPath(path, AutumnColors.Parchment.copy(alpha = 0.09f * opacity), style = Stroke(1.dp.toPx()))
        // The inner top highlight: light catching the pane's upper edge.
        drawLine(AutumnColors.Parchment.copy(alpha = 0.06f * opacity), Offset(1.dp.toPx(), 1.5.dp.toPx()), Offset(size.width - 1.dp.toPx(), 1.5.dp.toPx()), 1.dp.toPx())
    }
}

/** Condensation: fine drops of water on the glass, one tile drawn once and repeated. */
private object Condensation {
    private const val TILE = 256

    val brush: ShaderBrush by lazy {
        val bitmap = ImageBitmap(TILE, TILE)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val random = Random(17)
        repeat(150) {
            val radius = 0.5f + random.nextFloat() * 0.8f
            paint.color = AutumnColors.Parchment.copy(alpha = 0.12f + random.nextFloat() * 0.12f)
            val x = random.nextFloat() * TILE
            val y = random.nextFloat() * TILE
            // An oval a little taller than wide: water gathers at a drop's lower edge.
            canvas.drawOval(x - radius, y - radius * 1.4f, x + radius, y + radius * 1.4f, paint)
        }
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
}
