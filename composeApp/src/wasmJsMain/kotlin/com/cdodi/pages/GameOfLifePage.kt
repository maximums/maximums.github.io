package com.cdodi.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import blog.composeapp.generated.resources.Res
import blog.composeapp.generated.resources.game_of_life_pause_button
import blog.composeapp.generated.resources.game_of_life_play_button
import blog.composeapp.generated.resources.game_of_life_reset_button
import blog.composeapp.generated.resources.game_of_life_population
import blog.composeapp.generated.resources.game_of_life_evolution_speed
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.data.gameoflife.CELL_SIZE_PX
import com.cdodi.data.gameoflife.Cell
import com.cdodi.data.gameoflife.GameOfLifeManager
import com.cdodi.data.gameoflife.LifeRule
import com.cdodi.features.life.LifeController
import com.cdodi.data.gameoflife.Grid
import com.cdodi.ui.components.DripSlider
import com.cdodi.ui.components.QuietButton
import com.cdodi.ui.components.RoundButton
import com.cdodi.ui.components.StrokeIcons
import com.cdodi.ui.theme.Autumn
import com.cdodi.ui.theme.AutumnColors
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round



@Composable
internal fun rememberLifeManager(rule: LifeRule): GameOfLifeManager {
    val clock = LocalHeartbeat.current.child(LifeController.CLOCK)
    val manager = remember(clock, rule) { GameOfLifeManager(clock, rule) }

    DisposableEffect(manager) {
        onDispose { manager.close() }
    }

    return manager
}

@Composable
fun GameOfLifePage(rule: LifeRule = LifeRule.Conway) {
    val manager = rememberLifeManager(rule)
    val state by manager.state.collectEachFrame()
    val population by remember { derivedStateOf { state.aliveCells.size } }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {

        GridHeader(
            isPlaying = state.isRunning,
            population = population,
            evolutionSpeed = state.evolutionSpeed,
            onPlayPause = manager::playPause,
            onSpeedChange = manager::updateEvolutionSpeed,
            onReset = manager::resetGrid
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Transparent)
                .onSizeChanged(manager::updateGridBounds)
                .pointerInput(manager) { detectTapGestures { offset -> manager.addCell(offset) } }
        ) {
            drawAliveCells(state.aliveCells)
            drawGrid(state.grid)
        }
    }
}

/** The CPU page's controls, in the site's look: play, clear, speed, and the readouts. */
@Composable
private fun GridHeader(
    isPlaying: Boolean,
    population: Int,
    evolutionSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onReset: () -> Unit,
) {
    val playText = stringResource(if (isPlaying) Res.string.game_of_life_pause_button else Res.string.game_of_life_play_button)
    val resetText = stringResource(Res.string.game_of_life_reset_button)
    val populationText = stringResource(Res.string.game_of_life_population, population)
    val speedText = stringResource(Res.string.game_of_life_evolution_speed, evolutionSpeed.twoDecimals)

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundButton(if (isPlaying) StrokeIcons.Pause else StrokeIcons.Play, playText, onPlayPause)
        QuietButton(resetText, onReset, icon = StrokeIcons.Clear, enabled = !isPlaying && population > 0)
        DripSlider("Speed", speedText, evolutionSpeed, onSpeedChange, 0f..1f, Modifier.width(240.dp), steps = 10)
        Text(populationText, style = Autumn.type.readout.copy(fontSize = 14.sp), color = AutumnColors.Mist)
    }
}

private fun DrawScope.drawGrid(grid: Grid) {
    for (i in 0..grid.columns) {
        drawLine(
            color = GridLine,
            start = Offset(x = i * CELL_SIZE_PX, y = 0f),
            end = Offset(x = i * CELL_SIZE_PX, y = grid.height.toFloat()),
            strokeWidth = 1.dp.toPx()
        )
    }

    for (j in 0..grid.rows) {
        drawLine(
            color = GridLine,
            start = Offset(x = 0f, y = j * CELL_SIZE_PX),
            end = Offset(x = grid.width.toFloat(), y = j * CELL_SIZE_PX),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawAliveCells(cells: Set<Cell>) {
    for (cell in cells) {
        drawRect(
            color = AutumnColors.Lamp.copy(alpha = 0.9f),
            topLeft = Offset(cell.x * CELL_SIZE_PX, cell.y * CELL_SIZE_PX),
            size = Size(CELL_SIZE_PX, CELL_SIZE_PX),
        )
    }
}

/** DESIGN.md §8: a faint Rain-coloured grid; the cells are embers. */
private val GridLine = AutumnColors.Rain.copy(alpha = 0.12f)

inline val Float.twoDecimals: String
    get() {
        val rounded = round(this * 100).toInt()
        val whole = rounded / 100
        val fraction = rounded % 100

        return "$whole.${fraction.toString().padStart(2, '0')}"
    }