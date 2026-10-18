package com.cdodi.adapters.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.gpu.PassTiming
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Heartbeat
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

// DESIGN.md tokens: the readout component (name · bar · value), Lamp for the dominant value, Rain for the rest.
private val Lamp = Color(0xFFD8A15A)
private val Rain = Color(0xFF7E97A3)
private val Fog = Color(0xFF8C9499)
private val Hairline = Color(0xFFEDE6D6).copy(alpha = 0.1f)

/**
 * Where the GPU's time goes, pass by pass, from `timestamp-query`; the frame rate from the heartbeat; and any [extra]
 * rows the page adds. An outline pane, so it hides as little of the scene as possible. [timings] is null when the
 * browser can't time GPU passes.
 */
@Composable
fun ProfilingHud(timings: StateFlow<List<PassTiming>?>, modifier: Modifier = Modifier, extra: List<Pair<String, String>> = emptyList()) {
    val passes by timings.collectEachFrame()
    val fps by rememberFramesPerSecond()

    Column(
        modifier = modifier.width(250.dp).border(1.dp, Hairline).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("GPU TIME", color = Fog, fontSize = 10.sp, letterSpacing = 3.sp)
        val measured = passes
        if (measured == null) {
            Text("this browser doesn't time GPU passes", color = Fog, fontSize = 12.sp)
        } else {
            val slowest = measured.maxOfOrNull { it.milliseconds } ?: 0.0
            for (pass in measured) Readout(pass.name, pass.milliseconds, slowest)
            Readout("total", measured.sumOf { it.milliseconds }, slowest = 0.0)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("frame rate", color = Fog, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("${fps.roundToInt()} fps", color = Rain, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        for ((name, value) in extra) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Fog, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text(value, color = Rain, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** A row: the name, a 1 px bar as long as its share of the slowest pass, and the milliseconds. */
@Composable
private fun Readout(name: String, milliseconds: Double, slowest: Double) {
    val isSlowest = slowest > 0.0 && milliseconds >= slowest
    val colour = if (isSlowest) Lamp else Rain
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Fog, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f).height(1.dp)) {
            if (slowest > 0.0) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((milliseconds / slowest).toFloat().coerceIn(0.02f, 1f)).background(colour))
            }
        }
        Text(milliseconds.asMilliseconds(), color = colour, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp))
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

private fun Double.asMilliseconds(): String {
    val hundredths = (this * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} ms"
}
