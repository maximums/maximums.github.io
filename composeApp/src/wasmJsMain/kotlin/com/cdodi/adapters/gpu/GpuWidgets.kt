package com.cdodi.adapters.gpu

/**
 * UI elements that want the scene to draw something behind them ("GPU-backed widgets"). Compose can't blur or light
 * the WebGPU canvas underneath it, so an element reports where it is, and the scene draws its effect there, under
 * the element. The scene reads the registry every frame, after Compose has laid out (see [SceneHost]), so the effect
 * stays exactly under the element even while it moves.
 */
class GpuWidgets {

    enum class Kind {
        /** A lit sphere, its light circling (the About page's). */
        Sphere,
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

    /**
     * A pane of glass (DESIGN.md §5) at [left], [top] × [width], [height] in device pixels of the window. [corners]
     * writes its outline into 8 floats as a convex quad in its own pixels (a triangle repeats a corner); it's asked
     * every frame, so a pane can change shape while it morphs. [blur] (0 cleared, 1 fogged), [wipe] (a band of
     * clearness sweeping across, 0 to 1) and [visibility] are read every frame too.
     */
    class Pane(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val corners: (width: Float, height: Float, out: FloatArray) -> Unit,
        val blur: () -> Float,
        val wipe: () -> Float,
        val visibility: () -> Float,
    )

    private val panes = LinkedHashMap<Any, Pane>()

    /** The panes of glass, back to front: the scene host blurs what's behind them. */
    val glass: Collection<Pane> get() = panes.values

    fun reportPane(owner: Any, pane: Pane) {
        panes[owner] = pane
    }

    fun removePane(owner: Any) {
        panes.remove(owner)
    }
}
