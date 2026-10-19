package com.cdodi.adapters.compose.morph

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.cdodi.adapters.compose.effects.LocalMorph
import com.cdodi.ui.components.GlassOutline

/**
 * Where a morphing quad is this frame, for whoever else needs its outline: the GPU, which blurs the street behind the
 * pane ([outline]).
 */
class MorphingQuad {
    var vertices = MorphingShape.Rectangle.toVertexesProgress()
        internal set

    /** The quad's corners in a [width] × [height] layout, as [QuadShape] draws them. */
    val outline = GlassOutline { width, height, out ->
        val v = vertices
        out[0] = v.topStart * width; out[1] = 0f
        out[2] = width; out[3] = height - v.topEnd * height
        out[4] = v.bottomEnd * width; out[5] = height
        out[6] = 0f; out[7] = height - v.bottomStart * height
    }
}

/**
 * Clips a layout to a quad that morphs towards [target] by the running morph ([LocalMorph]), from whatever shape it
 * has when the target changes. [quad] hears where it is each frame.
 */
class MorphingShapeModifierNode(var target: QuadVertexProgress, var quad: MorphingQuad?) :
    LayoutModifierNode, CompositionLocalConsumerModifierNode, Modifier.Node() {

    private val vertices = ProgressTween<QuadVertexProgress>(::lerp)

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val current = vertices.update(target, currentValueOf(LocalMorph).current())
        quad?.vertices = current

        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                // A new value each time it changes, so the layer knows to rebuild its outline.
                shape = QuadShape(current)
                clip = true
            }
        }
    }
}

/** A quad whose corners sit at fractions of the layout's edges. */
private data class QuadShape(private val vertices: QuadVertexProgress) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val (width, height) = size
        val path = Path().apply {
            moveTo(vertices.topStart * width, 0f)
            lineTo(width, height - (vertices.topEnd * height))
            lineTo(vertices.bottomEnd * width, height)
            lineTo(0f, height - (vertices.bottomStart * height))
            close()
        }
        return Outline.Generic(path)
    }
}

data class MorphingShapeModifierNodeElement(val targetShape: MorphingShape, val quad: MorphingQuad?) : ModifierNodeElement<MorphingShapeModifierNode>() {
    override fun create() = MorphingShapeModifierNode(targetShape.toVertexesProgress(), quad)

    override fun update(node: MorphingShapeModifierNode) {
        node.target = targetShape.toVertexesProgress()
        node.quad = quad
    }
}

fun Modifier.morphingShape(targetShape: MorphingShape, quad: MorphingQuad? = null): Modifier =
    this then MorphingShapeModifierNodeElement(targetShape, quad)
