package com.cdodi.adapters.compose.morph

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Constraints
import com.cdodi.adapters.compose.effects.LocalMorph
import com.cdodi.adapters.compose.effects.Morph

/**
 * Scales a layout to [scale] about [origin], following the running morph ([LocalMorph]) the way [animatePlacement]
 * moves it: a menu label is laid out once, at its top-bar size, and drawn larger on Home, so it can fly between the
 * two without its text being measured again each frame. Read during placement, so nothing recomposes.
 */
private class MorphScaleNode(var scale: Float, var origin: TransformOrigin) :
    LayoutModifierNode, CompositionLocalConsumerModifierNode, Modifier.Node() {

    private val tween = ProgressTween<Float> { from, to, fraction -> from + (to - from) * fraction }

    private val layer: GraphicsLayerScope.() -> Unit = {
        val current = tween.update(scale, currentValueOf(LocalMorph).current())
        scaleX = current
        scaleY = current
        transformOrigin = origin
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layer) }
    }
}

private data class MorphScaleElement(val scale: Float, val origin: TransformOrigin) : ModifierNodeElement<MorphScaleNode>() {
    override fun create() = MorphScaleNode(scale, origin)

    override fun update(node: MorphScaleNode) {
        node.scale = scale
        node.origin = origin
    }
}

fun Modifier.morphScale(scale: Float, origin: TransformOrigin): Modifier = this then MorphScaleElement(scale, origin)
