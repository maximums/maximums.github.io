package com.cdodi.shell

import com.cdodi.adapters.browser.HashUrls
import com.cdodi.adapters.browser.followBrowserHistory
import com.cdodi.adapters.browser.followReducedMotion
import com.cdodi.adapters.browser.followVisibility
import com.cdodi.adapters.compose.ambient
import com.cdodi.adapters.compose.transitions
import com.cdodi.adapters.gpu.GpuCanvas
import com.cdodi.adapters.gpu.SceneHost
import com.cdodi.adapters.gpu.SceneRegistry
import com.cdodi.adapters.gpu.effects.GpuEffects
import com.cdodi.adapters.input.InputState
import com.cdodi.core.bus.DefaultLifecycleBus
import com.cdodi.core.bus.DefaultNavigationBus
import com.cdodi.core.bus.LifecycleBus
import com.cdodi.core.bus.NavigationBus
import com.cdodi.core.bus.pauseWhile
import com.cdodi.core.navigation.DestinationCodec
import com.cdodi.core.navigation.Navigator
import com.cdodi.core.navigation.graph.NavGraph
import com.cdodi.core.navigation.graph.requireRenderable
import com.cdodi.core.navigation.graph.sceneReady
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.navigation.signal.Signal
import com.cdodi.core.time.DefaultHeartbeat
import com.cdodi.core.time.Heartbeat
import com.cdodi.features.background.BackgroundScene
import com.cdodi.webgpu.context.requestGpuContext
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement

/** Whether the WebGPU layer runs. */
sealed interface GpuStatus {
    /** Still asking the browser for a device. */
    data object Starting : GpuStatus

    data object Running : GpuStatus

    /** No WebGPU here: the UI layer draws a fallback background and carries transitions alone. */
    data class Unavailable(val reason: String) : GpuStatus
}

/** Everything the site runs on, created once before the first frame. */
class AppRuntime(
    val heartbeat: Heartbeat,
    val lifecycle: LifecycleBus,
    val signals: MutableSignals,
    val navigation: NavigationBus,
    val input: InputState,
    val gpu: StateFlow<GpuStatus>,
)

fun bootstrap(): AppRuntime {
    val heartbeat = DefaultHeartbeat()

    val lifecycle = DefaultLifecycleBus()
    followVisibility(lifecycle)
    followReducedMotion(lifecycle)
    heartbeat.pauseWhile(lifecycle) { !it.isVisible }
    heartbeat.ambient.pauseWhile(lifecycle) { it.prefersReducedMotion }

    val graph = appGraph().apply { requireRenderable(siteEffects) }
    val signals = MutableSignals()
    val urls = HashUrls(DestinationCodec(graph), graph.start)
    val navigator = Navigator(graph, signals, start = urls.current(), reducedMotion = { lifecycle.state.value.prefersReducedMotion })
    val navigation = DefaultNavigationBus(navigator, heartbeat.transitions)
    followBrowserHistory(navigation, heartbeat, urls)
    val input = InputState()

    val gpu = MutableStateFlow<GpuStatus>(GpuStatus.Starting)
    val scope = MainScope()
    scope.launch { gpu.value = startGpuLayer(heartbeat, navigation, signals, graph, input, scope) }

    return AppRuntime(heartbeat, lifecycle, signals, navigation, input, gpu.asStateFlow())
}

private suspend fun startGpuLayer(
    heartbeat: Heartbeat,
    navigation: NavigationBus,
    signals: MutableSignals,
    graph: NavGraph,
    input: InputState,
    scope: CoroutineScope,
): GpuStatus {
    val gpu = try {
        requestGpuContext()
    } catch (e: Exception) {
        return unavailable(signals, graph, "the device request failed: ${e.message}")
    } ?: return unavailable(signals, graph, "this browser has no WebGPU")

    val canvas = GpuCanvas(document.getElementById("gpu")!!.unsafeCast<HTMLCanvasElement>(), gpu)
    val background = BackgroundScene(heartbeat.ambient)
    val scenes = SceneRegistry(graph.routes.associateWith { background })
    SceneHost(gpu, canvas, heartbeat, navigation.state, signals, scenes, GpuEffects.renderers(), input, scope)
    return GpuStatus.Running
}

/** Without scenes there is nothing to wait for: every scene counts as ready. */
private fun unavailable(signals: MutableSignals, graph: NavGraph, reason: String): GpuStatus {
    graph.routes.forEach { signals.raise(Signal.sceneReady(it)) }
    return GpuStatus.Unavailable(reason)
}
