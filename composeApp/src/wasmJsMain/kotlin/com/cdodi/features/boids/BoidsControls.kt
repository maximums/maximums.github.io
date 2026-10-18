package com.cdodi.features.boids

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.compose.ProfilingHud
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.gpu.PassTiming
import com.cdodi.components.CompactPanel
import com.cdodi.shell.LocalMenuLayout
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

private val Quiet = Color.White.copy(alpha = 0.72f)
private val Faint = Color.White.copy(alpha = 0.45f)

/**
 * The Boids page over its GPU scene: the flock is the scene underneath, so this is a panel of rules on the right
 * (DESIGN.md §8) and a line of hints. Changes reach the scene in its next frame.
 */
@Composable
fun BoidsControls(controller: BoidsController, gpuTimings: StateFlow<List<PassTiming>?>) {
    val status by controller.status.collectEachFrame()
    val params = status.params

    val compact = LocalMenuLayout.current.compact
    val extra = listOf("boids" to formatThousands(status.count), "grid" to "${status.grid.first} × ${status.grid.second}")

    Box(Modifier.fillMaxSize().padding(if (compact) 8.dp else 16.dp)) {
        if (!compact) ProfilingHud(gpuTimings, Modifier.align(Alignment.TopStart), extra = extra)
        CompactPanel(
            modifier = Modifier.align(Alignment.TopEnd).then(if (compact) Modifier else Modifier.width(300.dp)),
            spacing = 4.dp,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuietButton(if (status.isPaused) "Play" else "Pause", controller::playPause)
                QuietButton("Scatter", controller::scatter)
            }
            FlockSize(status.count, controller::setCount)
            Labelled("separation", params.separationWeight, 0f..4f, "%.1f") { v -> controller.setParams { it.copy(separationWeight = v) } }
            Labelled("alignment", params.alignmentWeight, 0f..4f, "%.1f") { v -> controller.setParams { it.copy(alignmentWeight = v) } }
            Labelled("cohesion", params.cohesionWeight, 0f..4f, "%.1f") { v -> controller.setParams { it.copy(cohesionWeight = v) } }
            Labelled("perception", params.perception, 12f..64f, "%.0f px") { v ->
                controller.setParams { it.copy(perception = v, separationDistance = v * 0.4f) }
            }
            Labelled("max speed", params.maxSpeed, 60f..400f, "%.0f px/s") { v -> controller.setParams { it.copy(maxSpeed = v, minSpeed = v / 2f) } }
            if (compact) ProfilingHud(gpuTimings, Modifier.padding(top = 8.dp), extra = extra)
        }

        Text(
            text = if (compact) "drag to draw the flock in" else "left-drag draws the flock in · right-drag scatters it · scroll sets how far",
            color = Faint,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** The flock size on a log scale, 1,024 to 1,048,576; applied when the slider is let go, since it scatters a new flock. */
@Composable
private fun FlockSize(count: Int, onChange: (Int) -> Unit) {
    var exponent by remember(count) { mutableFloatStateOf(log2(count.toFloat())) }
    val shown = 2f.pow(exponent).roundToInt()
    Column {
        Text("flock  ${formatThousands(shown)} boids", color = Quiet, fontSize = 14.sp)
        Slider(
            value = exponent,
            onValueChange = { exponent = it },
            onValueChangeFinished = { onChange(shown) },
            valueRange = 10f..20f,
            colors = sliderColors(),
        )
    }
}

@Composable
private fun Labelled(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, onChange: (Float) -> Unit) {
    Column {
        Text("$label  ${format.replace("%.1f", value.oneDecimal()).replace("%.0f", value.roundToInt().toString())}", color = Quiet, fontSize = 14.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = sliderColors())
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(activeTrackColor = Color.LightGray.copy(alpha = 0.3f), thumbColor = Color.LightGray.copy(alpha = 0.75f))

@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray.copy(alpha = 0.18f), contentColor = Color.White),
    ) {
        Text(text)
    }
}

private fun Float.oneDecimal(): String = ((this * 10).roundToInt() / 10f).toString()

private fun formatThousands(value: Int): String = value.toString().reversed().chunked(3).joinToString(",").reversed()
