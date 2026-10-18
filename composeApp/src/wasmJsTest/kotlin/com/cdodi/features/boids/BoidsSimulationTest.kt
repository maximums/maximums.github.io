package com.cdodi.features.boids

import blog.composeapp.generated.resources.Res
import com.cdodi.features.boids.reference.Boid
import com.cdodi.features.boids.reference.BoidsReference
import com.cdodi.features.boids.reference.Vec2
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.context.requestGpuContext
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * One GPU flocking step against the CPU reference (`BoidsReference`, the old `Rule`/`Vec2` code). The naive step must
 * match boid by boid; the grid step writes boids in cell order, so each reference boid is matched to the closest one.
 * Both differ from the CPU only by f32 rounding. Skipped without a WebGPU adapter.
 */
class BoidsSimulationTest {

    // A small world, crowded enough that every rule and the wrap-around are exercised.
    private val params = BoidsParams(worldWidth = 160f, worldHeight = 120f, maxNeighbours = 10_000)
    private val dt = 1f / 60f

    private fun flock(count: Int, seed: Int) = Random(seed).let { random ->
        List(count) {
            val angle = random.nextDouble(2 * PI)
            val speed = params.minSpeed + random.nextFloat() * (params.maxSpeed - params.minSpeed)
            Boid(
                Vec2(random.nextFloat() * params.worldWidth, random.nextFloat() * params.worldHeight),
                Vec2((cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat()),
            )
        }
    }

    private fun gpuTest(block: suspend (GpuContext, BoidsSimulation) -> Unit) = runTest(timeout = 60.seconds) {
        val gpu = requestGpuContext() ?: return@runTest println("BoidsSimulationTest: no WebGPU adapter in this browser — skipped")
        gpu.use {
            BoidsSimulation.create(
                gpu,
                Res.readBytes("files/shaders/boids/step.wgsl").decodeToString(),
                Res.readBytes("files/shaders/common/scan.wgsl").decodeToString(),
                capacity = 512,
            ).use { block(gpu, it) }
        }
    }

    private fun GpuContext.submit(record: (com.cdodi.webgpu.bindings.GPUCommandEncoder) -> Unit) {
        val encoder = device.createCommandEncoder()
        record(encoder)
        queue.submit(listOf(encoder.finish()).toJsArray())
    }

    private fun assertClose(expected: Boid, actual: Boid, what: String) {
        // Positions near an edge may wrap to the other side by a hair: compare across the edge.
        val position = (actual.position - expected.position).wrappedDelta(Vec2(params.worldWidth, params.worldHeight))
        assertTrue(position.magnitude < 1e-2f, "$what: position ${actual.position} vs ${expected.position}")
        assertTrue((actual.velocity - expected.velocity).magnitude < 1e-2f, "$what: velocity ${actual.velocity} vs ${expected.velocity}")
    }

    @Test
    fun theNaiveStepMatchesTheReferenceBoidByBoid() = gpuTest { gpu, simulation ->
        val start = flock(64, seed = 1)
        simulation.load(start)
        gpu.submit { simulation.encodeNaiveStep(it, params, dt) }

        val expected = BoidsReference.step(start, params, dt)
        val actual = simulation.readBoids()
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEachIndexed { i, (e, a) -> assertClose(e, a, "boid $i") }
    }

    @Test
    fun theGridStepFindsTheSameNeighbours() = gpuTest { gpu, simulation ->
        val start = flock(256, seed = 2)
        simulation.load(start)
        gpu.submit { simulation.encodeGridStep(it, params, dt) }

        val expected = BoidsReference.step(start, params, dt)
        val actual = simulation.readBoids().toMutableList()
        assertEquals(expected.size, actual.size)
        for ((i, e) in expected.withIndex()) {
            val match = actual.minByOrNull { (it.position - e.position).wrappedDelta(Vec2(params.worldWidth, params.worldHeight)).magnitude }
                ?: fail("no boid left for $i")
            assertClose(e, match, "boid $i")
            actual.remove(match)
        }
        assertEquals(5 to 4, simulation.grid, "160 × 120 with a perception of 28: 5 × 4 cells")
    }

    @Test
    fun aFlockOutgrowingTheBuffersGetsBiggerOnes() = gpuTest { gpu, simulation ->
        simulation.ensureCapacity(300)
        assertEquals(512, simulation.capacity, "already fits")
        simulation.ensureCapacity(1500)
        assertEquals(2048, simulation.capacity, "the next power of two")
        assertEquals(0, simulation.count, "growing empties the flock")

        val start = flock(1500, seed = 4)
        simulation.load(start)
        gpu.submit { simulation.encodeNaiveStep(it, params, dt) }
        val expected = BoidsReference.step(start, params, dt)
        expected.zip(simulation.readBoids()).forEachIndexed { i, (e, a) -> assertClose(e, a, "boid $i") }
    }

    @Test
    fun boidsKeepTheirSpeedBetweenTheLimits() = gpuTest { gpu, simulation ->
        simulation.scatter(512, params, Random(3))
        repeat(30) { gpu.submit { simulation.encodeGridStep(it, params, dt) } }

        for (boid in simulation.readBoids()) {
            val speed = boid.velocity.magnitude
            assertTrue(speed >= params.minSpeed - 1e-3f && speed <= params.maxSpeed + 1e-3f, "speed $speed")
            assertTrue(boid.position.x in 0f..params.worldWidth && boid.position.y in 0f..params.worldHeight, "inside the world: ${boid.position}")
        }
        assertTrue(abs(simulation.count - 512) == 0)
    }
}
