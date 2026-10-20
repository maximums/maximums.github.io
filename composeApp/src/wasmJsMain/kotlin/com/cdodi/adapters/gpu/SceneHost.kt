package com.cdodi.adapters.gpu

import com.cdodi.adapters.gpu.effects.GpuEffectRenderer
import com.cdodi.adapters.input.InputState
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.effect.Layer
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.navigation.graph.sceneReady
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.navigation.signal.Signal
import com.cdodi.core.time.Frame
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Heartbeat
import com.cdodi.core.time.Subscription
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUExtent3DDict
import com.cdodi.webgpu.bindings.GPUTexture
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.context.GpuContext
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Runs the WebGPU layer. Every frame it reads where navigation is and draws accordingly:
 *
 * - **Idle**: the destination's scene, straight into the canvas.
 * - **Transitioning**: if a GPU effect has started, both scenes go to offscreen textures and the most recent GPU
 *   effect blends them into the canvas (in a sequence that's the running segment; finished ones hold their end).
 *   Before any GPU effect has started, the scene being left stays on screen.
 *
 * Scenes are prepared the first time navigation heads for them; when one is ready the host raises `sceneReady` for its
 * routes, which condition-based edges wait on. A scene that isn't ready draws as Night. Visible scenes are stepped
 * on their own clocks in the Simulation phase; drawing happens in the Render phase.
 *
 * The drawing itself waits for a microtask queued in the Render phase. In a Compose frame the heartbeat ticks first and
 * Compose lays out after, so drawing during the tick would put what the scene draws behind UI elements (see
 * [GpuWidgets]) where they were a frame ago, visibly trailing a card that's moving. The microtask runs once Compose has
 * laid out and drawn the same frame, and both canvases still show in the same browser frame.
 *
 * Every frame starts by writing the [theme]'s colours, if the theme has changed, for the shaders that draw in them.
 *
 * Glass panes that Compose reports need the scene behind them blurred: while any is showing, the frame goes through the
 * [glass] compositor on its way to the canvas.
 *
 * A failure while drawing closes the host and is reported to [onFailure] instead of propagating.
 *
 * Closing it ends everything it started: the scenes, effects and glass still preparing (in its own child of [scope]),
 * and the `sceneReady` signals it raised, so a host started after it (a new device) is waited for again.
 */
class SceneHost(
    private val gpu: GpuContext,
    private val canvas: GpuCanvas,
    heartbeat: Heartbeat,
    private val navigation: StateFlow<NavState>,
    private val signals: MutableSignals,
    private val scenes: SceneRegistry,
    private val effects: List<GpuEffectRenderer>,
    private val input: InputState,
    scope: CoroutineScope,
    private val profiler: GpuProfiler?,
    private val glass: GlassCompositor,
    private val theme: GpuTheme,
    private val onFailure: (Throwable) -> Unit,
) : AutoCloseable {

    private val requested = mutableSetOf<Scene>()
    private val ready = mutableSetOf<Scene>()
    private val readyEffects = mutableSetOf<GpuEffectRenderer>()
    private val encodedThisFrame = mutableSetOf<Scene>()
    private val sceneSubscriptions = mutableListOf<Subscription>()
    private val raised = mutableSetOf<Signal>()
    private var offscreen: Offscreen? = null
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val work = CoroutineScope(scope.coroutineContext + job)

    private var isClosed = false
    private val renderSubscription = heartbeat.subscribe(FramePhase.Render) { frame ->
        queueMicrotask {
            if (isClosed) return@queueMicrotask
            try {
                render(frame)
            } catch (e: Throwable) {
                close()
                onFailure(e)
            }
        }
    }

    init {
        work.launch {
            glass.prepare(gpu, canvas.format)
            ensureActive()
            glass.resize(canvas.width, canvas.height, blurLevels())
        }
        for (effect in effects) {
            work.launch {
                effect.prepare(gpu, canvas.format)
                ensureActive()
                effect.resize(canvas.width, canvas.height)
                readyEffects += effect
            }
        }
    }

    private fun render(frame: Frame) {
        theme.update()
        val nav = navigation.value
        prepareScenesFor(nav)
        if (canvas.applyPendingSize()) onResize()
        if (canvas.width == 0 || canvas.height == 0) return

        val encoder = gpu.device.createCommandEncoder()
        profiler?.beginFrame()
        val canvasView = canvas.currentView()
        val panes = glass.collect()
        val target = if (panes > 0) glass.frameView else canvasView
        when (nav) {
            is NavState.Idle -> draw(sceneOf(nav.at), encoder, target)
            is NavState.Transitioning -> drawTransition(nav, encoder, target, frame)
        }
        if (panes > 0) glass.encode(encoder, canvasView, panes, profiler)
        profiler?.resolve(encoder)
        gpu.queue.submit(listOf(encoder.finish()).toJsArray())
        profiler?.afterSubmit()
        encodedThisFrame.forEach { it.afterSubmit() }
        encodedThisFrame.clear()
    }

    private fun drawTransition(nav: NavState.Transitioning, encoder: GPUCommandEncoder, target: GPUTextureView, frame: Frame) {
        val (state, renderer) = latestGpuEffect(nav.effects) ?: return draw(sceneOf(nav.from), encoder, target)
        val textures = offscreen ?: Offscreen(canvas.width, canvas.height).also { offscreen = it }
        val fromScene = sceneOf(nav.from)
        val toScene = sceneOf(nav.to)
        draw(fromScene, encoder, textures.fromView)
        val toView = if (toScene === fromScene) textures.fromView else textures.toView.also { draw(toScene, encoder, it) }
        renderer.encode(encoder, target, textures.fromView, toView, state, frame)
    }

    private fun latestGpuEffect(states: List<EffectState>): Pair<EffectState, GpuEffectRenderer>? {
        for (state in states.asReversed()) {
            if (state.effect.layer != Layer.Gpu) continue
            val renderer = readyEffects.firstOrNull { state.effect.id in it.ids } ?: continue
            return state to renderer
        }
        return null
    }

    private fun draw(scene: Scene?, encoder: GPUCommandEncoder, target: GPUTextureView) {
        if (scene != null && scene in ready) {
            scene.encode(encoder, target)
            encodedThisFrame += scene
        } else {
            FullscreenPass.clear(encoder, target, theme.ground)
        }
    }

    private fun sceneOf(destination: Destination): Scene? = scenes.sceneFor(destination.route)

    /** Starts preparing every scene navigation is showing or heading for. */
    private fun prepareScenesFor(nav: NavState) {
        val wanted = when (nav) {
            is NavState.Idle -> listOf(nav.at)
            is NavState.Transitioning -> listOf(nav.from, nav.to)
        } + nav.history.current
        for (destination in wanted) {
            val scene = sceneOf(destination) ?: continue
            if (requested.add(scene)) prepare(scene)
        }
    }

    private fun prepare(scene: Scene) = work.launch {
        try {
            scene.prepare(gpu, canvas.format)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The scene stays Night; edges waiting on it give up at their timeout.
            println("Scene ${scenes.routesOf(scene)} failed to prepare: ${e.message}")
            return@launch
        }
        ensureActive()
        scene.resize(canvas.width, canvas.height)
        ready += scene
        sceneSubscriptions += scene.clock.subscribe(FramePhase.Simulation) { frame ->
            if (isVisible(scene)) scene.update(frame, input)
        }
        for (route in scenes.routesOf(scene)) {
            val signal = Signal.sceneReady(route)
            raised += signal
            signals.raise(signal)
        }
    }

    private fun isVisible(scene: Scene): Boolean = when (val nav = navigation.value) {
        is NavState.Idle -> sceneOf(nav.at) === scene
        is NavState.Transitioning -> sceneOf(nav.from) === scene || sceneOf(nav.to) === scene
    }

    private fun onResize() {
        offscreen?.close()
        offscreen = null
        ready.forEach { it.resize(canvas.width, canvas.height) }
        readyEffects.forEach { it.resize(canvas.width, canvas.height) }
        glass.resize(canvas.width, canvas.height, blurLevels())
    }

    /** Half-size steps for the glass blur: one more on a dense screen, so it's about as wide in CSS pixels. */
    private fun blurLevels(): Int = if (window.devicePixelRatio >= 1.5) 5 else 4

    override fun close() {
        if (isClosed) return
        isClosed = true
        job.cancel()
        raised.forEach(signals::lower)
        renderSubscription.close()
        sceneSubscriptions.forEach { it.close() }
        offscreen?.close()
        glass.close()
        theme.close()
        scenes.all.forEach { it.close() }
        effects.forEach { it.close() }
        profiler?.close()
        canvas.close()
    }

    /** The two textures a transition renders its scenes into, in the canvas's size and format. */
    private inner class Offscreen(width: Int, height: Int) : AutoCloseable {
        private val from = texture(width, height, "scene being left")
        private val to = texture(width, height, "scene being entered")
        val fromView: GPUTextureView = from.createView()
        val toView: GPUTextureView = to.createView()

        private fun texture(width: Int, height: Int, label: String): GPUTexture = gpu.device.createTexture(
            GPUTextureDescriptor(
                size = GPUExtent3DDict(width = width, height = height),
                format = canvas.format,
                usage = GPUTextureUsage.RENDER_ATTACHMENT or GPUTextureUsage.TEXTURE_BINDING,
                label = label,
            )
        )

        override fun close() {
            from.destroy()
            to.destroy()
        }
    }
}

/** Runs [callback] once the current task (here, Compose's frame) has finished, before the browser shows the frame. */
private fun queueMicrotask(callback: () -> Unit): Unit = js("queueMicrotask(callback)")
