package com.cdodi.data.gameoflife

import androidx.compose.ui.unit.IntOffset

typealias Cell = IntOffset

data class LifeState(
    val isRunning: Boolean = false,
    val aliveCells: Set<Cell> = emptySet(),
    val evolutionSpeed: Float = .5f,
    val grid: Grid = Grid(),
)

data class Grid(
    val columns: Int = 0,
    val rows: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
)
