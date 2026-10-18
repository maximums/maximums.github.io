package com.cdodi.adapters.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.gpu.PassTiming
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Heartbeat
import com.cdodi.ui.components.GlassShape
import com.cdodi.ui.components.ReadoutRow
import com.cdodi.ui.components.formatDecimal
import com.cdodi.ui.theme.Autumn
import com.cdodi.ui.theme.AutumnColors
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Where the GPU's time goes (DESIGN.md §6, Readout; the Boids mockup): an outline pane, so it hides as little of the
 * scene as possible. A line with [title] (what's being simulated), the frame rate and the GPU total, then a row per
 * pass from `timestamp-query`, the slowest in Lamp. [timings] is null when the browser can't time GPU passes.
 */
@Composable
fun ProfilingHud(timings: StateFlow<List<PassTiming>?>, title: String, modifier: Modifier = Modifier) {
    val passes by timings.collectEachFrame()
    val fps by rememberFramesPerSecond()
    val measured = passes

    Column(
        modifier = modifier
            .width(360.dp)
            .border(1.dp, AutumnColors.Parchment.copy(alpha = 0.1f), GlassShape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = Autumn.type.readout, color = AutumnColors.Mist, maxLines = 1, modifier = Modifier.weight(1f))
            val total = measured?.sumOf { it.milliseconds }
            Text(
                "${fps.roundToInt()} fps" + (total?.let { " · ${formatDecimal(it, 2)} ms gpu" } ?: ""),
                style = Autumn.type.readout,
                color = AutumnColors.Mist,
                maxLines = 1,
            )
        }
        if (measured == null) {
            Text("this browser doesn't time GPU passes", style = Autumn.type.readout, color = AutumnColors.Fog)
        } else {
            val slowest = measured.maxOfOrNull { it.milliseconds } ?: 0.0
            for (pass in measured) {
                ReadoutRow(
                    name = pass.name,
                    value = formatDecimal(pass.milliseconds, 2),
                    share = if (slowest > 0.0) (pass.milliseconds / slowest).toFloat() else 0f,
                    dominant = slowest > 0.0 && pass.milliseconds >= slowest,
                    barWidth = 200.dp,
                )
            }
        }
    }
}

/** Frames per second from the heartbeat's frame timestamps (real time, whatever the time scale), smoothed. */
@Composable
fun rememberFramesPerSecond(heartbeat: Heartbeat = LocalHeartbeat.current): State<Float> {
    val fps = remember { mutableFloatStateOf(0f) }
    DisposableEffect(heartbeat) {
        var last = -1L
        var smoothed = 0.0
        val subscription = heartbeat.subscribe(FramePhase.Ui) { frame ->
            if (last > 0 && frame.timeNanos > last) {
                val instant = 1e9 / (frame.timeNanos - last)
                smoothed = if (smoothed == 0.0) instant else smoothed + (instant - smoothed) * 0.05
                fps.floatValue = smoothed.toFloat()
            }
            last = frame.timeNanos
        }
        onDispose(subscription::close)
    }
    return fps
}
