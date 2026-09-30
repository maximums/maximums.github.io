package com.cdodi.shell

import com.cdodi.adapters.browser.followReducedMotion
import com.cdodi.adapters.browser.followVisibility
import com.cdodi.adapters.compose.ambient
import com.cdodi.adapters.compose.effects.UiEffects
import com.cdodi.adapters.compose.transitions
import com.cdodi.core.bus.DefaultLifecycleBus
import com.cdodi.core.bus.DefaultNavigationBus
import com.cdodi.core.bus.LifecycleBus
import com.cdodi.core.bus.NavigationBus
import com.cdodi.core.bus.pauseWhile
import com.cdodi.core.navigation.Navigator
import com.cdodi.core.navigation.graph.requireRenderable
import com.cdodi.core.navigation.signal.MutableSignals
import com.cdodi.core.time.DefaultHeartbeat
import com.cdodi.core.time.Heartbeat

/** Everything the site runs on, created once before the first frame. */
class AppRuntime(
    val heartbeat: Heartbeat,
    val lifecycle: LifecycleBus,
    val signals: MutableSignals,
    val navigation: NavigationBus,
)

fun bootstrap(): AppRuntime {
    val heartbeat = DefaultHeartbeat()

    val lifecycle = DefaultLifecycleBus()
    followVisibility(lifecycle)
    followReducedMotion(lifecycle)
    heartbeat.pauseWhile(lifecycle) { !it.isVisible }
    heartbeat.ambient.pauseWhile(lifecycle) { it.prefersReducedMotion }

    val graph = appGraph().apply { requireRenderable(UiEffects) }
    val signals = MutableSignals()
    val navigator = Navigator(graph, signals, reducedMotion = { lifecycle.state.value.prefersReducedMotion })
    val navigation = DefaultNavigationBus(navigator, heartbeat.transitions)

    return AppRuntime(heartbeat, lifecycle, signals, navigation)
}
