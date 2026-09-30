package com.cdodi.adapters.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.cdodi.core.bus.NavigationBus
import com.cdodi.core.time.Clock
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Heartbeat
import kotlinx.coroutines.flow.StateFlow

val LocalHeartbeat = staticCompositionLocalOf<Heartbeat> { error("No Heartbeat provided") }

val LocalNavigationBus = staticCompositionLocalOf<NavigationBus> { error("No NavigationBus provided") }

/** Background shaders and ambient shimmer; frozen while the user prefers reduced motion. */
val Clock.ambient: Clock get() = child("ambient")

/** Navigation transitions. */
val Clock.transitions: Clock get() = child("transitions")

/** The period shader time is folded into; per-shader periods come with the WebGPU background (Phase 4). */
const val SHADER_TIME_PERIOD = 10_000.0

/** Compose's frame clock drives the heartbeat; the heartbeat is the source of truth everything else reads. */
@Composable
fun HeartbeatDriver(heartbeat: Heartbeat) {
    LaunchedEffect(heartbeat) {
        while (true) withFrameNanos(heartbeat::tick)
    }
}

/**
 * This flow's value, read in the Ui phase of every frame. Collecting it in a coroutine would show a frame's result
 * one frame late; this way Compose shows what the heartbeat computed in the same frame.
 */
@Composable
fun <T> StateFlow<T>.collectEachFrame(heartbeat: Heartbeat = LocalHeartbeat.current): State<T> {
    val state = remember(this) { mutableStateOf(value) }
    DisposableEffect(this, heartbeat) {
        val subscription = heartbeat.subscribe(FramePhase.Ui) { state.value = value }
        onDispose(subscription::close)
    }
    return state
}

/** This clock's time for a shader uniform, folded so an f32 keeps its precision, updated in every frame's Ui phase. */
@Composable
fun Clock.shaderTime(period: Double = SHADER_TIME_PERIOD): State<Float> {
    val state = remember(this, period) { mutableFloatStateOf(elapsedFolded(period)) }
    DisposableEffect(this, period) {
        val subscription = subscribe(FramePhase.Ui) { state.floatValue = elapsedFolded(period) }
        onDispose(subscription::close)
    }
    return state
}
