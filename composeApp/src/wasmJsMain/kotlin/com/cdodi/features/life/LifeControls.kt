package com.cdodi.features.life

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.compose.ProfilingHud
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.gpu.PassTiming
import com.cdodi.components.PanelCorner
import com.cdodi.components.SceneOverlay
import com.cdodi.data.gameoflife.LifeRule
import com.cdodi.ui.components.Choice
import com.cdodi.ui.components.DripSlider
import com.cdodi.ui.components.Label
import com.cdodi.ui.components.QuietButton
import com.cdodi.ui.components.RoundButton
import com.cdodi.ui.components.StrokeIcons
import com.cdodi.ui.components.ToggleRow
import com.cdodi.ui.components.formatThousands
import com.cdodi.ui.theme.Autumn
import com.cdodi.ui.theme.AutumnColors
import com.cdodi.ui.theme.LocalViewport
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** The rules on offer, by name. Any other B/S rule still works through the URL (`#/life?rule=B36/S23`). */
private val namedRules = listOf(
    "Conway" to LifeRule.Conway,
    "HighLife" to LifeRule.HighLife,
    "Seeds" to LifeRule.Seeds,
    "Day & Night" to LifeRule.DayAndNight,
)

/**
 * The Life page over its GPU scene (DESIGN.md §8, the Game of Life mockup): the grid is the scene underneath, so this
 * is one fog pane at the bottom left (the rule, play, step, speed and readouts), the GPU readout at the top right and a
 * hint in the corner. The rule comes from the destination, so it's in the URL; picking another one navigates.
 */
@Composable
fun LifeControls(controller: LifeController, rule: LifeRule, gpuTimings: StateFlow<List<PassTiming>?>, onRuleChange: (LifeRule) -> Unit) {
    LaunchedEffect(rule) { controller.setRule(rule) }
    val status by controller.status.collectEachFrame()
    val compact = LocalViewport.current.compact
    val cells = "${status.width} × ${status.height} cells"
    val ruleName = namedRules.firstOrNull { it.second == rule }?.first

    SceneOverlay(
        panelWidth = 392.dp,
        corner = PanelCorner.BottomStart,
        hint = when {
            compact -> if (status.isPaused) "Drag to draw" else "Drag to pan"
            status.isPaused -> "Draw with the pointer · shift erases · scroll zooms"
            else -> "Scroll zooms · drag pans · pause to draw"
        },
        readout = { ProfilingHud(gpuTimings, cells, it) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Simulation")
            Text("Game of Life", style = Autumn.type.title, color = AutumnColors.Parchment)
            Text(listOfNotNull(rule.toString(), ruleName).joinToString(" · "), style = Autumn.type.readout, color = AutumnColors.Lamp)
        }
        Choice(namedRules, rule, onRuleChange, textSize = 17.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            RoundButton(if (status.isPaused) StrokeIcons.Play else StrokeIcons.Pause, if (status.isPaused) "Play" else "Pause", controller::playPause)
            QuietButton(null, controller::step, icon = StrokeIcons.Step, description = "Step one generation", enabled = status.isPaused)
            QuietButton(null, controller::scatter, icon = StrokeIcons.Scatter, description = "Scatter random cells")
            QuietButton("Clear", controller::clear, icon = StrokeIcons.Clear)
            QuietButton(null, controller::fitView, icon = StrokeIcons.Fit, description = "Fit the grid to the screen")
        }
        DripSlider(
            label = "Speed",
            valueText = "${status.generationsPerSecond.roundToInt()} gen/s",
            value = status.generationsPerSecond,
            onChange = controller::setSpeed,
            range = LifeController.MIN_SPEED..LifeController.MAX_SPEED,
            steps = (LifeController.MAX_SPEED - LifeController.MIN_SPEED).roundToInt(),
        )
        ToggleRow("Edges wrap around", status.wrap, controller::setWrap)
        Box(Modifier.fillMaxWidth().height(1.dp).background(AutumnColors.Parchment.copy(alpha = 0.08f)))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure("Generation", formatThousands(status.generation.toInt()), Modifier.weight(1.15f))
            Figure("Alive", formatThousands(status.population), Modifier.weight(0.9f))
            Figure("Grid", "${status.width} × ${status.height}", Modifier.weight(1.45f))
        }
    }
}

/** A readout under a label: the generation, the cells alive, the grid. */
@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        Text(value, style = Autumn.type.readout.copy(fontSize = 15.sp, letterSpacing = 0.sp), color = AutumnColors.Parchment, maxLines = 1)
    }
}
