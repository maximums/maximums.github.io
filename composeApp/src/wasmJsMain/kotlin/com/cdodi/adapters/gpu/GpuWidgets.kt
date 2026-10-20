package com.cdodi.adapters.gpu

import com.cdodi.design.components.GlassPane
import com.cdodi.design.components.GlassSink

/**
 * UI elements that want the scene to draw something behind them ("GPU-backed widgets"). Compose can't blur or light
 * the WebGPU canvas underneath it, so an element reports where it is, and the scene draws its effect there, under
 * the element, with the [GpuWidgetRenderer] for its [Kind]. The scene reads the registry every frame, after Compose
 * has laid out (see [SceneHost]), so the effect stays exactly under the element even while it moves. It's also where
 * the glass reports its panes ([GlassSink]), for the scene host to blur the street behind them.
 */
class GpuWidgets : GlassSink {

    enum class Kind {
        /** A lit sphere, its light circling (the About page's). */
        Sphere,

        /** The maple on Home, a textured 3D model. */
        Bonsai,
    }

    /**
     * Where an element is, in device pixels of the window, and how visible it is right now: its page's visibility, so
     * the effect fades with the page.
     */
    class Widget(val kind: Kind, val left: Float, val top: Float, val width: Float, val height: Float, val visibility: () -> Float)

    private val widgets = LinkedHashMap<Any, Widget>()

    val current: Collection<Widget> get() = widgets.values

    fun report(owner: Any, widget: Widget) {
        widgets[owner] = widget
    }

    fun remove(owner: Any) {
        widgets.remove(owner)
    }

    private val panes = LinkedHashMap<Any, GlassPane>()

    /** The panes of glass, back to front: the scene host blurs what's behind them. */
    val glass: Collection<GlassPane> get() = panes.values

    override fun reportPane(owner: Any, pane: GlassPane) {
        panes[owner] = pane
    }

    override fun removePane(owner: Any) {
        panes.remove(owner)
    }
}
