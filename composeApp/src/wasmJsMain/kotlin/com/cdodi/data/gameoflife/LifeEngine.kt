package com.cdodi.data.gameoflife

import androidx.compose.ui.unit.IntOffset

/**
 * CPU implementation of Life-like automata on a bounded grid: cells outside
 * `0 until columns` × `0 until rows` are always dead.
 *
 * It drives the page when WebGPU is missing, and it is the reference that GPU results are checked against.
 */
object LifeEngine {

    fun step(cells: Set<Cell>, columns: Int, rows: Int, rule: LifeRule = LifeRule.Conway): Set<Cell> {
        val neighbourCounts = HashMap<Cell, Int>()

        for (cell in cells) {
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue

                    val neighbour = IntOffset(x = cell.x + dx, y = cell.y + dy)
                    neighbourCounts[neighbour] = (neighbourCounts[neighbour] ?: 0) + 1
                }
            }
        }

        // A cell with no live neighbours never shows up in the counts, so rules with B0 or S0 need every cell checked.
        val candidates: Iterable<Cell> = when {
            rule.isAliveNext(isAlive = false, neighbours = 0) -> allCells(columns, rows)
            rule.isAliveNext(isAlive = true, neighbours = 0) -> neighbourCounts.keys + cells
            else -> neighbourCounts.keys
        }

        return buildSet {
            for (cell in candidates) {
                if (cell.x !in 0 until columns || cell.y !in 0 until rows) continue

                if (rule.isAliveNext(isAlive = cell in cells, neighbours = neighbourCounts[cell] ?: 0)) add(cell)
            }
        }
    }

    private fun allCells(columns: Int, rows: Int): List<Cell> =
        (0 until rows).flatMap { y -> (0 until columns).map { x -> IntOffset(x, y) } }
}
