package com.cdodi.adapters.compose

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import com.cdodi.adapters.gpu.GpuWidgets

/** Where elements report themselves for the scene to draw behind them; null where there is no GPU layer. */
val LocalGpuWidgets = staticCompositionLocalOf<GpuWidgets?> { null }

/** How visible the page around an element is, 0..1: set per page by the navigation renderer, 1 elsewhere. */
val LocalPageVisibility = staticCompositionLocalOf { { 1f } }

/**
 * Asks the scene to draw [kind] behind this element: its bounds are reported every time they change (moving with a
 * morph included), together with its page's visibility, and withdrawn when the element leaves the composition.
 */
fun Modifier.gpuBacked(kind: GpuWidgets.Kind): Modifier = this then GpuBackedElement(kind)

private data class GpuBackedElement(val kind: GpuWidgets.Kind) : ModifierNodeElement<GpuBackedNode>() {
    override fun create() = GpuBackedNode(kind)

    override fun update(node: GpuBackedNode) {
        node.kind = kind
    }
}

private class GpuBackedNode(var kind: GpuWidgets.Kind) : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var registry: GpuWidgets? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val widgets = registry ?: currentValueOf(LocalGpuWidgets)?.also { registry = it } ?: return
        val bounds = coordinates.boundsInWindow()
        widgets.report(this, GpuWidgets.Widget(kind, bounds.left, bounds.top, bounds.width, bounds.height, currentValueOf(LocalPageVisibility)))
    }

    override fun onDetach() {
        registry?.remove(this)
        registry = null
    }
}
