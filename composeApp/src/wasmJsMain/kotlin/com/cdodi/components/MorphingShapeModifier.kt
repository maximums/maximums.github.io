package com.cdodi.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch

class MorphingShapeModifierNode(
    initialShape: QuadVertexProgress,
) : LayoutModifierNode, Modifier.Node() {

    private val cachedPath = Path()
    private val morphingAnimation: Animatable<QuadVertexProgress, AnimationVector4D> = Animatable(
        initialValue = initialShape,
        typeConverter = VertexesProgressToVector,
        label = "MorphingShapeAnimation",
    )

    private val nodeShape = object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val progress = morphingAnimation.value
            val width = size.width
            val height = size.height

            with(cachedPath) {
                rewind()
                moveTo(progress.topStart * width, 0f)
                lineTo(width, height - (progress.topEnd * height))
                lineTo(progress.bottomEnd * width, height)
                lineTo(0f, height - (progress.bottomStart * height))
                close()
            }
            return Outline.Generic(cachedPath)
        }
    }

    fun updateTargetShape(newShape: QuadVertexProgress, durationMillis: Int, easing: Easing) {
        if (morphingAnimation.targetValue != newShape) {
            coroutineScope.launch {
                morphingAnimation.animateTo(
                    targetValue = newShape,
                    animationSpec = tween(durationMillis = durationMillis, easing = easing)
                )
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)

        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                shape = nodeShape
                clip = true
            }
        }
    }
}

data class MorphingShapeModifierNodeElement(
    val targetShape: MorphingShape,
    val durationMillis: Int,
    val easing: Easing,
): ModifierNodeElement<MorphingShapeModifierNode>() {
    override fun create() = MorphingShapeModifierNode(targetShape.toVertexesProgress())

    override fun update(node: MorphingShapeModifierNode) = node.updateTargetShape(
        newShape = targetShape.toVertexesProgress(),
        durationMillis = durationMillis,
        easing = easing
    )
}

fun Modifier.morphingShape(
    targetShape: MorphingShape,
    durationMillis: Int = 1500,
    easing: Easing = FastOutSlowInEasing,
): Modifier = this then MorphingShapeModifierNodeElement(targetShape, durationMillis, easing)
