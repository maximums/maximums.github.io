package com.cdodi.data.gameoflife

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastRoundToInt
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
    private val gameRules: Set<GameRule> = setOf(
        ConwaySurvivalRule(),
        ConwayReproductionRule()
//        In my case death is implicit, because I only draw the alive cells
//        ConwayUnderpopulationRule(),
//        ConwayOverpopulationRule(),
    )
) : EvolutionEngine, Manager(bus) {
    private val _state = MutableStateFlow(LifeState())
    val state: StateFlow<LifeState> = _state.asStateFlow()
    private var accumulator = 0f

    override val managerScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("GameOfLifeManager"))

    override fun loop(timeStep: Float) {
        if (!state.value.isRunning) return

        accumulator += (timeStep * 1.seconds.inWholeMilliseconds * state.value.evolutionSpeed)

        while (accumulator >= TICK_RATE_MS) {
            _state.update { lifeState ->
                val cells = evaluateNextGeneration(lifeState.aliveCells, gameRules)
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
        if (state.value.isRunning) return // user can't modify population when the game is running | need to add a UI indication when game is running

        val x = (offset.x / CELL_SIZE_PX).toInt()
        val y = (offset.y / CELL_SIZE_PX).toInt()
        val newCell = IntOffset(x, y)

        _state.update { currentState ->
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
            val cells = currentState.aliveCells
                .filter { it.x in 0 until updatedGrid.width && it.y in 0 until newGridSize.height }
                .toSet()

            currentState.copy(aliveCells = cells, grid = updatedGrid)
        }
    }

    fun updateEvolutionSpeed(speed: Float) {
        _state.update { currentState -> currentState.copy(evolutionSpeed = speed) }
    }

    fun resetGrid() {
        _state.update { currentState -> currentState.copy(isRunning = false, aliveCells = emptySet(), evolutionSpeed = .5f) }
    }

    override fun evaluateNextGeneration(
        currentGeneration: Set<Cell>,
        rules: Collection<GameRule>
    ): Set<Cell> {
        val neighborCounts = getNeighborCounts(currentGeneration)

        return buildSet {
            neighborCounts.forEach { (cell, count) ->
                if (!(cell isIn state.value.grid)) return@forEach

                val isAlive = cell in currentGeneration
                val survived = rules.any { it(isAlive, count) }

                if (survived) add(cell)
            }
        }
    }

    private fun getNeighborCounts(population: Set<Cell>): Map<Cell, Int> {
        val neighborCounts = mutableMapOf<Cell, Int>()

        population.forEach { cell ->
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) continue // skip myself

                    val neighbor = IntOffset(x = cell.x + dx, y = cell.y + dy)
                    neighborCounts[neighbor] = neighborCounts.getOrElse(neighbor) { 0 } + 1
                }
            }
        }

        return neighborCounts
    }

    private fun Grid.refresh(screenSize: IntSize): Grid {
        val cellSizeInt = CELL_SIZE_PX.toInt()
        val columns = (screenSize.width / CELL_SIZE_PX).fastRoundToInt()
        val rows = (screenSize.height / CELL_SIZE_PX).fastRoundToInt()
        val cleanWidth = cellSizeInt * columns
        val cleanHeight = cellSizeInt * rows

        return copy(columns = columns, rows = rows, width = cleanWidth, height = cleanHeight)
    }

    private infix fun Cell.isIn(grid: Grid): Boolean =
        x in 0 until grid.width && y in 0 until grid.height

    private inline val Grid.isUnspecified: Boolean
        get() = width <= 0 && height <= 0
}