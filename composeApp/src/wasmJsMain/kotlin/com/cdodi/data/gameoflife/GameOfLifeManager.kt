package com.cdodi.data.gameoflife

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cdodi.buses.TimeBus
import com.cdodi.data.Manager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.seconds

internal const val CELL_SIZE_PX = 20f
private const val TICK_RATE_MS = 100L

class GameOfLifeManager(
    bus: TimeBus,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val rule: LifeRule = LifeRule.Conway,
) : Manager(bus) {
    private val _state = MutableStateFlow(LifeState())
    val state: StateFlow<LifeState> = _state.asStateFlow()
    private var accumulator = 0f

    override val managerScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("GameOfLifeManager"))

    override fun loop(timeStep: Float) {
        if (!state.value.isRunning) return

        accumulator += (timeStep * 1.seconds.inWholeMilliseconds * state.value.evolutionSpeed)

        while (accumulator >= TICK_RATE_MS) {
            _state.update { lifeState ->
                val cells = LifeEngine.step(lifeState.aliveCells, lifeState.grid.columns, lifeState.grid.rows, rule)
                lifeState.copy(isRunning = cells.isNotEmpty(), aliveCells = cells)
            }
            accumulator -= TICK_RATE_MS
        }
    }

    init { start() }

    fun playPause() {
        if (state.value.grid.isUnspecified) return

        _state.update { it.copy(isRunning = !it.isRunning) }
    }

    fun addCell(offset: Offset) {
        if (state.value.isRunning) return // user can't modify population when the game is running

        val x = (offset.x / CELL_SIZE_PX).toInt()
        val y = (offset.y / CELL_SIZE_PX).toInt()
        val newCell = IntOffset(x, y)

        _state.update { currentState ->
            if (!(newCell isIn currentState.grid)) return@update currentState // tap below or right of the drawn grid

            val cells = when (newCell) {
                in currentState.aliveCells -> currentState.aliveCells - newCell
                else -> currentState.aliveCells + newCell
            }

            currentState.copy(aliveCells = cells)
        }
    }

    fun updateGridBounds(newGridSize: IntSize) {
        _state.update { currentState ->
            val updatedGrid = currentState.grid.refresh(newGridSize)
            val cells = currentState.aliveCells.filterTo(mutableSetOf()) { it isIn updatedGrid }

            currentState.copy(aliveCells = cells, grid = updatedGrid)
        }
    }

    fun updateEvolutionSpeed(speed: Float) {
        _state.update { currentState -> currentState.copy(evolutionSpeed = speed) }
    }

    fun resetGrid() {
        _state.update { currentState -> currentState.copy(isRunning = false, aliveCells = emptySet(), evolutionSpeed = .5f) }
    }

    private fun Grid.refresh(screenSize: IntSize): Grid {
        val cellSizeInt = CELL_SIZE_PX.toInt()
        val columns = (screenSize.width / CELL_SIZE_PX).toInt()
        val rows = (screenSize.height / CELL_SIZE_PX).toInt()
        val cleanWidth = cellSizeInt * columns
        val cleanHeight = cellSizeInt * rows

        return copy(columns = columns, rows = rows, width = cleanWidth, height = cleanHeight)
    }

    private infix fun Cell.isIn(grid: Grid): Boolean =
        x in 0 until grid.columns && y in 0 until grid.rows

    private inline val Grid.isUnspecified: Boolean
        get() = columns <= 0 || rows <= 0
}
