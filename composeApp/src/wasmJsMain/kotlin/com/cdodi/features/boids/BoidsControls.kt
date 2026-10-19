package com.cdodi.features.boids

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.compose.ProfilingHud
import com.cdodi.adapters.compose.ProfilingHudWidth
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.gpu.PassTiming
import com.cdodi.ui.components.DripSlider
import com.cdodi.ui.components.Label
import com.cdodi.ui.components.PanelCorner
import com.cdodi.ui.components.QuietButton
import com.cdodi.ui.components.RoundButton
import com.cdodi.ui.components.SceneOverlay
import com.cdodi.ui.components.StrokeIcons
import com.cdodi.ui.components.formatDecimal
import com.cdodi.ui.components.formatThousands
import com.cdodi.ui.theme.LocalViewport
import com.cdodi.ui.theme.SiteTheme
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The Boids page over its GPU scene (DESIGN.md §8, the Boids mockup): the flock is the scene underneath, so this is a
 * fog pane of rules on the right, the GPU readout at the bottom left and a hint in the corner. Changes reach the scene
 * in its next frame.
 */
@Composable
fun BoidsControls(controller: BoidsController, gpuTimings: StateFlow<List<PassTiming>?>) {
    val status by controller.status.collectEachFrame()
    val params = status.params
    val compact = LocalViewport.current.compact
    val title = "${formatThousands(status.count)} boids"

    SceneOverlay(
        panelWidth = 340.dp,
        corner = PanelCorner.TopEnd,
        hint = if (compact) "Drag to gather the flock" else "Left button gathers · right button scatters · scroll sets how far",
        readoutWidth = ProfilingHudWidth,
        readout = { ProfilingHud(gpuTimings, title, it) },
        panelSpacing = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Simulation")
            Text("Boids", style = SiteTheme.type.title, color = SiteTheme.colors.bright)
            Text("a murmuration under the rain", style = SiteTheme.type.verse.copy(fontSize = SiteTheme.type.verse.fontSize * 0.95f), color = SiteTheme.colors.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(if (status.isPaused) StrokeIcons.Play else StrokeIcons.Pause, if (status.isPaused) "Play" else "Pause", controller::playPause)
            QuietButton("Scatter", controller::scatter, icon = StrokeIcons.Scatter)
        }
        FlockSize(status.count, controller::setCount)
        DripSlider("Separation", formatDecimal(params.separationWeight.toDouble(), 1), params.separationWeight, { v -> controller.setParams { it.copy(separationWeight = v) } }, 0f..4f, steps = 40)
        DripSlider("Alignment", formatDecimal(params.alignmentWeight.toDouble(), 1), params.alignmentWeight, { v -> controller.setParams { it.copy(alignmentWeight = v) } }, 0f..4f, steps = 40)
        DripSlider("Cohesion", formatDecimal(params.cohesionWeight.toDouble(), 1), params.cohesionWeight, { v -> controller.setParams { it.copy(cohesionWeight = v) } }, 0f..4f, steps = 40)
        DripSlider("Perception", "${params.perception.roundToInt()} px", params.perception, { v ->
            controller.setParams { it.copy(perception = v, separationDistance = v * 0.4f) }
        }, 12f..64f, steps = 52)
        DripSlider("Max speed", "${params.maxSpeed.roundToInt()} px/s", params.maxSpeed, { v ->
            controller.setParams { it.copy(maxSpeed = v, minSpeed = v / 2f) }
        }, 60f..400f, steps = 34)
    }
}

/** The flock size on a log scale, 1,024 to 1,048,576; applied when the slider is let go, since it scatters a new flock. */
@Composable
private fun FlockSize(count: Int, onChange: (Int) -> Unit) {
    var exponent by remember(count) { mutableFloatStateOf(log2(count.toFloat())) }
    val shown = 2f.pow(exponent).roundToInt()
    DripSlider(
        label = "Flock size",
        valueText = formatThousands(shown),
        value = exponent,
        onChange = { exponent = it },
        range = 10f..20f,
        steps = 10,
        onChangeFinished = { onChange(shown) },
    )
}
