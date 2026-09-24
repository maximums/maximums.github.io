package com.cdodi.data.gameoflife

import androidx.compose.ui.unit.IntOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LifeEngineTest {

    private fun cells(vararg xy: Pair<Int, Int>): Set<Cell> = xy.mapTo(mutableSetOf()) { (x, y) -> IntOffset(x, y) }

    private fun Set<Cell>.shifted(dx: Int, dy: Int): Set<Cell> = mapTo(mutableSetOf()) { IntOffset(it.x + dx, it.y + dy) }

    private fun Set<Cell>.steps(n: Int, columns: Int = 20, rows: Int = 20, rule: LifeRule = LifeRule.Conway): Set<Cell> =
        (1..n).fold(this) { generation, _ -> LifeEngine.step(generation, columns, rows, rule) }

    private val glider = cells(1 to 0, 2 to 1, 0 to 2, 1 to 2, 2 to 2)

    @Test
    fun blockIsStable() {
        val block = cells(1 to 1, 2 to 1, 1 to 2, 2 to 2)

        assertEquals(block, block.steps(1))
    }

    @Test
    fun blinkerOscillatesWithPeriodTwo() {
        val horizontal = cells(1 to 2, 2 to 2, 3 to 2)
        val vertical = cells(2 to 1, 2 to 2, 2 to 3)

        assertEquals(vertical, horizontal.steps(1))
        assertEquals(horizontal, horizontal.steps(2))
    }

    @Test
    fun gliderMovesOneCellDiagonallyEveryFourGenerations() {
        val start = glider.shifted(3, 3)

        assertEquals(start.shifted(1, 1), start.steps(4))
        assertEquals(start.shifted(3, 3), start.steps(12))
    }

    @Test
    fun cellsNeverLeaveTheGrid() {
        // Regression test: the grid used to be checked against its size in pixels, so a glider
        // kept living far outside the visible cells and the game never stopped on its own.
        val columns = 10
        val rows = 8
        var generation = glider.shifted(5, 3)

        repeat(60) {
            generation = LifeEngine.step(generation, columns, rows)
            assertTrue(generation.all { it.x in 0 until columns && it.y in 0 until rows }, "left the grid: $generation")
        }
    }

    @Test
    fun gliderHittingTheCornerSettlesIntoABlock() {
        // With dead cells beyond the edge, a glider running into the bottom-right corner becomes a still block.
        val settled = glider.shifted(5, 3).steps(60, columns = 10, rows = 8)

        assertEquals(cells(8 to 6, 9 to 6, 8 to 7, 9 to 7), settled)
    }

    @Test
    fun loneCellDiesUnderConwayButSurvivesWhenTheRuleAllowsZeroNeighbours() {
        val lone = cells(4 to 4)

        assertEquals(emptySet(), lone.steps(1))
        assertEquals(lone, lone.steps(1, rule = LifeRule.parse("B3/S0")))
    }

    @Test
    fun highLifeBirthsOnSixNeighbours() {
        // A dead cell surrounded by six live cells is born under HighLife, not under Conway.
        val ring = cells(0 to 0, 1 to 0, 2 to 0, 0 to 2, 1 to 2, 2 to 2)
        val centre = IntOffset(1, 1)

        assertTrue(centre in LifeEngine.step(ring, 10, 10, LifeRule.HighLife))
        assertTrue(centre !in LifeEngine.step(ring, 10, 10, LifeRule.Conway))
    }
}
