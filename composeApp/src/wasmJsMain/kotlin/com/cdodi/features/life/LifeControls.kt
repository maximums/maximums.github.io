package com.cdodi.features.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.cdodi.data.gameoflife.LifeRule
import kotlin.math.roundToInt

/** The rules on offer, by name. Any other B/S rule still works through the URL (`#/life?rule=B36/S23`). */
private val namedRules = listOf(
    "Conway" to LifeRule.Conway,
    "HighLife" to LifeRule.HighLife,
    "Seeds" to LifeRule.Seeds,
    "Day & Night" to LifeRule.DayAndNight,
)

private val Quiet = Color.White.copy(alpha = 0.72f)
private val Faint = Color.White.copy(alpha = 0.45f)

/**
 * The Life page over its GPU scene: the grid itself is the scene underneath, so this is only the controls and the
 * readouts. The rule comes from the destination, so it is in the URL; picking another one navigates.
 */
@Composable
fun LifeControls(controller: LifeController, rule: LifeRule, gpuTimings: StateFlow<List<PassTiming>?>, onRuleChange: (LifeRule) -> Unit) {
    LaunchedEffect(rule) { controller.setRule(rule) }
    val status by controller.status.collectEachFrame()

    val compact = LocalMenuLayout.current.compact
    val extra = listOf("cells" to "${status.width} × ${status.height}")

    Box(Modifier.fillMaxSize().padding(if (compact) 8.dp else 16.dp)) {
        if (!compact) ProfilingHud(gpuTimings, Modifier.align(Alignment.TopEnd).padding(top = 104.dp), extra = extra)
        // Rows that wrap: on a phone the buttons, the speed and the rules take a few lines each.
        CompactPanel {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                QuietButton(if (status.isPaused) "Play" else "Pause", onClick = controller::playPause)
                QuietButton("Step", enabled = status.isPaused, onClick = controller::step)
                QuietButton("Scatter", onClick = controller::scatter)
                QuietButton("Clear", onClick = controller::clear)
                QuietButton("Fit", onClick = controller::fitView)
                Slider(
                    value = status.generationsPerSecond,
                    onValueChange = controller::setSpeed,
                    valueRange = LifeController.MIN_SPEED..LifeController.MAX_SPEED,
                    modifier = Modifier.width(220.dp),
                    colors = SliderDefaults.colors(activeTrackColor = Color.LightGray.copy(alpha = 0.3f), thumbColor = Color.LightGray.copy(alpha = 0.7f)),
                )
                Text("${status.generationsPerSecond.roundToInt()} gen/s", color = Quiet, fontSize = 16.sp)
                QuietButton(if (status.wrap) "Edges wrap" else "Edges are walls") { controller.setWrap(!status.wrap) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                for ((name, namedRule) in namedRules) {
                    QuietButton(name, selected = namedRule == rule) { onRuleChange(namedRule) }
                }
                Text(rule.toString(), color = Quiet, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            }
            if (compact) ProfilingHud(gpuTimings, extra = extra)
        }

        FlowRow(
            Modifier.fillMaxWidth().align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = when {
                    compact -> if (status.isPaused) "drag to draw" else "drag to pan"
                    status.isPaused -> "drag to draw · shift-drag to erase · scroll to zoom · right-drag to pan"
                    else -> "scroll to zoom · drag to pan"
                },
                color = Faint,
                fontSize = 13.sp,
            )
            Text(
                text = "generation ${status.generation} · ${status.population} alive · ${status.width} × ${status.height}",
                color = Quiet,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun QuietButton(text: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.LightGray.copy(alpha = if (selected) 0.45f else 0.18f),
            contentColor = Color.White,
            disabledBackgroundColor = Color.LightGray.copy(alpha = 0.08f),
            disabledContentColor = Color.White.copy(alpha = 0.4f),
        ),
    ) {
        Text(text)
    }
}
