package com.cdodi

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeViewport
import com.cdodi.adapters.compose.HeartbeatDriver
import com.cdodi.adapters.compose.LocalGpuWidgets
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.LocalNavigationBus
import com.cdodi.adapters.compose.asMotionClock
import com.cdodi.adapters.input.InputLayer
import com.cdodi.design.components.LocalGlassSink
import com.cdodi.design.theme.LocalMotionClock
import com.cdodi.shell.App
import com.cdodi.shell.AppRoot
import com.cdodi.shell.bootstrap

fun main() {
    val runtime = bootstrap()

    ComposeViewport("compose") {
        HeartbeatDriver(runtime.heartbeat)

        // The design system's ports: UI motion on the heartbeat, and the glass's panes to the GPU layer.
        val motion = remember { runtime.heartbeat.child("ui").asMotionClock() }
        CompositionLocalProvider(
            LocalHeartbeat provides runtime.heartbeat,
            LocalMotionClock provides motion,
            LocalNavigationBus provides runtime.navigation,
        ) {
            AppRoot {
                InputLayer(runtime.input, runtime.heartbeat)
                CompositionLocalProvider(LocalGpuWidgets provides runtime.widgets, LocalGlassSink provides runtime.widgets) {
                    App(runtime)
                }
            }
        }
    }
}
