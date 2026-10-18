package com.cdodi.adapters.gpu

/**
 * UI elements that want the scene to draw something behind them ("GPU-backed widgets"). Compose can't blur or light
 * the WebGPU canvas underneath it, so an element reports where it is, and the scene draws its effect there, under
 * the element. The scene reads the registry every frame, after Compose has laid out (see [SceneHost]), so the effect
 * stays exactly under the element even while it moves.
 */
class GpuWidgets {

    enum class Kind {
        /** A lit sphere, its light circling (the Welcome card's, which also breathes in and out). */
        Sphere,
        BreathingSphere,
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
}
