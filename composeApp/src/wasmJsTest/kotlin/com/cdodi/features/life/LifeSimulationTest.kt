package com.cdodi.features.life

import androidx.compose.ui.unit.IntOffset
import blog.composeapp.generated.resources.Res
import com.cdodi.data.gameoflife.Cell
import com.cdodi.data.gameoflife.LifeEngine
import com.cdodi.data.gameoflife.LifeRule
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.context.requestGpuContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * The GPU simulation against the CPU reference: known patterns run for N generations on both, and the GPU's cells,
 * read back, must be exactly the reference's. Skipped where the browser has no WebGPU adapter.
 */
class LifeSimulationTest {

    private fun cells(vararg xy: Pair<Int, Int>, at: Pair<Int, Int> = 5 to 5): Set<Cell> =
        xy.mapTo(mutableSetOf()) { (x, y) -> IntOffset(x + at.first, y + at.second) }

    private val glider = cells(1 to 0, 2 to 1, 0 to 2, 1 to 2, 2 to 2)
    private val rPentomino = cells(1 to 0, 2 to 0, 0 to 1, 1 to 1, 1 to 2, at = 30 to 30)
    private val gliderGun = cells(
        24 to 0,
        22 to 1, 24 to 1,
        12 to 2, 13 to 2, 20 to 2, 21 to 2, 34 to 2, 35 to 2,
        11 to 3, 15 to 3, 20 to 3, 21 to 3, 34 to 3, 35 to 3,
        0 to 4, 1 to 4, 10 to 4, 16 to 4, 20 to 4, 21 to 4,
        0 to 5, 1 to 5, 10 to 5, 14 to 5, 16 to 5, 17 to 5, 22 to 5, 24 to 5,
        10 to 6, 16 to 6, 24 to 6,
        11 to 7, 15 to 7,
        12 to 8, 13 to 8,
    )
    private val highLifeReplicator = cells(2 to 0, 3 to 0, 4 to 0, 1 to 1, 4 to 1, 0 to 2, 4 to 2, 0 to 3, 3 to 3, 0 to 4, 1 to 4, 2 to 4, at = 28 to 28)

    private fun gpuTest(block: suspend TestScope.(GpuContext) -> Unit) = runTest(timeout = 60.seconds) {
        val gpu = requestGpuContext()
        if (gpu == null) {
            println("LifeSimulationTest: no WebGPU adapter in this browser — skipped")
            return@runTest
        }
        gpu.use { block(it) }
    }

    private suspend fun simulation(gpu: GpuContext, size: Int, scope: CoroutineScope) =
        LifeSimulation.create(gpu, Res.readBytes("files/shaders/life/step.wgsl").decodeToString(), size, size, scope)

    private fun LifeSimulation.run(generations: Int, gpu: GpuContext) {
        val encoder = gpu.device.createCommandEncoder()
        encodeSteps(encoder, generations, slot = 0)
        gpu.queue.submit(listOf(encoder.finish()).toJsArray())
        afterSubmit()
    }

    private fun reference(start: Set<Cell>, generations: Int, size: Int, rule: LifeRule = LifeRule.Conway): Set<Cell> {
        var cells = start
        repeat(generations) { cells = LifeEngine.step(cells, size, size, rule) }
        return cells
    }

    private suspend fun TestScope.assertMatchesReference(gpu: GpuContext, start: Set<Cell>, generations: Int, rule: LifeRule = LifeRule.Conway) {
        simulation(gpu, size = 64, scope = backgroundScope).use { life ->
            life.rule = rule
            life.load(start)
            life.run(generations, gpu)

            assertEquals(reference(start, generations, 64, rule), life.readCells())
            assertEquals(generations.toLong(), life.generation)
        }
    }

    @Test
    fun aGliderMatchesTheReference() = gpuTest { gpu -> assertMatchesReference(gpu, glider, generations = 24) }

    @Test
    fun theRPentominoMatchesTheReference() = gpuTest { gpu -> assertMatchesReference(gpu, rPentomino, generations = 80) }

    @Test
    fun aGliderGunMatchesTheReferenceUntilItsGlidersHitTheWall() = gpuTest { gpu -> assertMatchesReference(gpu, gliderGun, generations = 120) }

    @Test
    fun otherRulesComeFromTheMasks() = gpuTest { gpu ->
        assertMatchesReference(gpu, highLifeReplicator, generations = 40, rule = LifeRule.HighLife)
    }

    @Test
    fun onATorusAGliderComesBackToWhereItStarted() = gpuTest { gpu ->
        simulation(gpu, size = 16, scope = backgroundScope).use { life ->
            life.wrap = true
            life.load(glider)
            life.run(generations = 64, gpu) // a glider moves one cell diagonally every 4 generations: 16 cells

            assertEquals(glider, life.readCells())
        }
    }

    @Test
    fun cellsAgeAndTheDeadLeaveAFadingGhost() = gpuTest { gpu ->
        simulation(gpu, size = 16, scope = backgroundScope).use { life ->
            val block = cells(0 to 0, 1 to 0, 0 to 1, 1 to 1)
            val lonely = cells(8 to 8)
            life.load(block + lonely)
            life.run(generations = 5, gpu)

            val values = life.readValues()
            fun valueAt(cell: Cell) = values[cell.y * 16 + cell.x]
            assertEquals(List(4) { 6 }, block.map(::valueAt), "a still life ages a generation at a time")
            assertEquals((12 - 4) shl 16, valueAt(lonely.single()), "the lonely cell died after one generation; its ghost has faded four")
        }
    }

    @Test
    fun thePopulationIsReadBackWithoutWaiting() = gpuTest { gpu ->
        simulation(gpu, size = 64, scope = backgroundScope).use { life ->
            life.load(gliderGun)
            life.run(generations = 30, gpu)

            assertEquals(reference(gliderGun, 30, 64).size, life.livePopulation.first { it > 0 })
        }
    }
}
