package com.cdodi.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blog.composeapp.generated.resources.Res
import blog.composeapp.generated.resources.game_of_life_pause_button
import blog.composeapp.generated.resources.game_of_life_play_button
import blog.composeapp.generated.resources.game_of_life_reset_button
import blog.composeapp.generated.resources.game_of_life_population
import blog.composeapp.generated.resources.game_of_life_evolution_speed
import com.cdodi.buses.LocalTimeBus
import com.cdodi.data.gameoflife.CELL_SIZE_PX
import com.cdodi.data.gameoflife.Cell
import com.cdodi.data.gameoflife.GameOfLifeManager
import com.cdodi.data.gameoflife.Grid
import com.cdodi.vw
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round


@Composable
internal fun rememberLifeManager(): GameOfLifeManager {
    val timeBus = LocalTimeBus.current
    val manager = remember(timeBus) { GameOfLifeManager(timeBus) }

    DisposableEffect(manager) {
        onDispose { manager.close() }
    }

    return manager
}

@Composable
fun GameOfLifePage() {
    val manager = rememberLifeManager()
    val state by manager.state.collectAsStateWithLifecycle()
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

@Composable
private fun GridHeader(
    isPlaying: Boolean,
    population: Int,
    evolutionSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onReset: () -> Unit,
) {
    val playBtnTxtId = if (isPlaying) Res.string.game_of_life_pause_button else Res.string.game_of_life_play_button
    val playBtnText = stringResource(playBtnTxtId)
    val isPlayBtnEnabled = population > 0 || isPlaying
    val resetBtnTxt = stringResource(Res.string.game_of_life_reset_button)
    val isResetBtnEnabled = !isPlaying && population > 0
    val populationTxt = stringResource(Res.string.game_of_life_population, population)
    val speed = remember(evolutionSpeed) { evolutionSpeed.twoDecimals }
    val evolutionSpeedTxt = stringResource(Res.string.game_of_life_evolution_speed, speed)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onPlayPause,
            enabled = isPlayBtnEnabled,
            modifier = Modifier.width(10.vw),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.LightGray.copy(alpha = 0.2f),
                contentColor = Color.White,
                disabledBackgroundColor = Color.LightGray.copy(alpha = 0.7f),
                disabledContentColor = Color.White,
            )
        ) {
            Text(playBtnText)
        }

        Button(
            onClick = onReset,
            enabled = isResetBtnEnabled,
            modifier = Modifier.width(10.vw),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.LightGray.copy(alpha = 0.2f),
                contentColor = Color.White,
                disabledBackgroundColor = Color.LightGray.copy(alpha = 0.7f),
                disabledContentColor = Color.White,
            )
        ) {
            Text(resetBtnTxt)
        }

        Slider(
            value = evolutionSpeed,
            onValueChange = onSpeedChange,
            steps = 9,
            modifier = Modifier.width(25.vw),
            colors = SliderDefaults.colors(
                activeTrackColor = Color.LightGray.copy(alpha = 0.2f),
                thumbColor = Color.LightGray.copy(alpha = 0.5f),
                activeTickColor = Color.White
            )
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = evolutionSpeedTxt,
            color = Color.White,
            fontSize = 20.sp
        )

        Text(
            text = populationTxt,
            color = Color.White,
            fontSize = 20.sp
        )
    }
}

private fun DrawScope.drawGrid(grid: Grid) {
    for (i in 0..grid.columns) {
        drawLine(
            color = Color.Green,
            start = Offset(x = i * CELL_SIZE_PX, y = 0f),
            end = Offset(x = i * CELL_SIZE_PX, y = grid.height.toFloat()),
            strokeWidth = 1.dp.toPx()
        )
    }

    for (j in 0..grid.rows) {
        drawLine(
            color = Color.Green,
            start = Offset(x = 0f, y = j * CELL_SIZE_PX),
            end = Offset(x = grid.width.toFloat(), y = j * CELL_SIZE_PX),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawAliveCells(cells: Set<Cell>) {
    for (cell in cells) {
        drawRect(
            color = Color.Red,
            topLeft = Offset(cell.x * CELL_SIZE_PX, cell.y * CELL_SIZE_PX),
            size = Size(CELL_SIZE_PX, CELL_SIZE_PX),
        )
    }
}

inline val Float.twoDecimals: String
    get() {
        val rounded = round(this * 100).toInt()
        val whole = rounded / 100
        val fraction = rounded % 100

        return "$whole.${fraction.toString().padStart(2, '0')}"
    }