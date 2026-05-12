package com.cdodi.data.gameoflife

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlin.collections.emptySet

typealias Cell = IntOffset
typealias Grid = Pair<Int, Int>

private const val TICK_RATE_MS = 100L

class GameOfLifeManager : EvolutionEngine {
    private val isRunning = MutableStateFlow(false)
    private val cells = MutableStateFlow(emptySet<Cell>())
    val aliveCells: StateFlow<Set<Cell>> = cells.asStateFlow()

    private var grid: Grid = 0 to 0

    private val gameRules = setOf(
        ConwaySurvivalRule(),
        ConwayReproductionRule(),
    )

    fun playPause() {
        if (grid.isUnspecified) return

        isRunning.update { !it }
        println("Playing $${isRunning.value}")
    }

    fun addCell(offset: Offset) {
        if (isRunning.value) return // user can't modify population when the game is running | need to add a UI indication when game is running

        val x = (offset.x / CELL_SIZE_PX).toInt()
        val y = (offset.y / CELL_SIZE_PX).toInt()
        val newCell = IntOffset(x, y)

        cells.update { currentCells ->
            if (newCell in currentCells) currentCells - newCell else currentCells + newCell
        }
    }

    fun updateGridBounds(newGrid: Grid) {
        grid = newGrid

        println("Grid updated to $grid")

        cells.update { current ->
            current.filter { it.x in 0 until grid.first && it.y in 0 until grid.second }.toSet()
        }
    }

    suspend fun loop() {
        isRunning.collectLatest { running ->
            if (!running) return@collectLatest

            var previousTime = 0L
            var accumulatedTime = 0L

            while (currentCoroutineContext().isActive) {
                withInfiniteAnimationFrameMillis { currentTime ->
                    if (previousTime == 0L) previousTime = currentTime

                    val deltaTime = currentTime - previousTime

                    previousTime = currentTime
                    accumulatedTime += deltaTime

                    while (accumulatedTime >= TICK_RATE_MS) {
                        cells.update {evaluateNextGeneration(it, gameRules) }
                        println("Population: ${aliveCells.value.size}")
                        accumulatedTime -= TICK_RATE_MS
                    }
                }
            }
        }
    }

    override fun evaluateNextGeneration(
        currentGeneration: Set<Cell>,
        rules: Collection<GameRule>
    ): Set<Cell> {
        val neighborCounts = getNeighborCounts(currentGeneration)

        return buildSet {
            neighborCounts.forEach { (cell, count) ->
                if (!cell.isInGrid) return@forEach

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

    private inline val Cell.isInGrid: Boolean
        get() = x in 0 until grid.first && y in 0 until grid.second

    private inline val Grid.isUnspecified: Boolean
        get() = first <= 0 && second <= 0

    companion object {
        const val CELL_SIZE_PX = 20f
    }
}