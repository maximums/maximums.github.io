package com.cdodi.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ApproachLayoutModifierNode
import androidx.compose.ui.layout.ApproachMeasureScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.lerp
import com.cdodi.adapters.compose.effects.LocalMorph

// 1. lookaheadSize - final size of component
// 2. lookaheadCoordinates - final position of component
// 3. lookaheadScopeCoordinates - boundaries of LookaheadScope
// 4. coordinates - current position of component
// 5. localLookaheadPositionOf - `Future` layout map
// 6. localPositionOf - `Present` layout map

/**
 * Moves and resizes a layout towards where lookahead says it will end up, by the running morph ([LocalMorph])
 * rather than a clock of its own, so the menu follows the navigation model: it pauses, slows
 * and reverses with it. Reading the progress during layout makes only layout run again each frame.
 */
class PlacementModifierNode(var lookaheadScope: LookaheadScope) :
    ApproachLayoutModifierNode, CompositionLocalConsumerModifierNode, Modifier.Node() {

    private val offset = ProgressTween<IntOffset>(::lerp)
    private val size = ProgressTween<IntSize> { from, to, fraction ->
        IntSize(lerp(from.width, to.width, fraction), lerp(from.height, to.height, fraction))
    }

    private fun morph() = currentValueOf(LocalMorph).current()

    override fun isMeasurementApproachInProgress(lookaheadSize: IntSize): Boolean =
        size.update(lookaheadSize, morph()) != lookaheadSize

    override fun Placeable.PlacementScope.isPlacementApproachInProgress(lookaheadCoordinates: LayoutCoordinates): Boolean {
        val target = with(lookaheadScope) { lookaheadScopeCoordinates.localLookaheadPositionOf(lookaheadCoordinates).round() }
        return offset.update(target, morph()) != target
    }

    override fun ApproachMeasureScope.approachMeasure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val (width, height) = size.update(lookaheadSize, morph())
        val placeable = measurable.measure(Constraints.fixed(width, height))

        return layout(placeable.width, placeable.height) {
            val placement = coordinates?.let { current ->
                val target = with(lookaheadScope) { lookaheadScopeCoordinates.localLookaheadPositionOf(current).round() }
                val position = with(lookaheadScope) { lookaheadScopeCoordinates.localPositionOf(current, Offset.Zero).round() }
                offset.update(target, morph()) - position
            } ?: IntOffset.Zero

            placeable.place(placement)
        }
    }
}

data class PlacementNodeElement(val lookaheadScope: LookaheadScope) : ModifierNodeElement<PlacementModifierNode>() {

    override fun update(node: PlacementModifierNode) {
        node.lookaheadScope = lookaheadScope
    }

    override fun create() = PlacementModifierNode(lookaheadScope)
}

fun Modifier.animatePlacement(lookaheadScope: LookaheadScope): Modifier = then(PlacementNodeElement(lookaheadScope))
