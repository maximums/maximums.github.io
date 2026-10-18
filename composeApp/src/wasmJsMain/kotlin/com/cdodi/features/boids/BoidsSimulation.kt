package com.cdodi.features.boids

import com.cdodi.adapters.gpu.GpuScan
import com.cdodi.features.boids.reference.Boid
import com.cdodi.features.boids.reference.Vec2
import com.cdodi.webgpu.bindings.GPUAutoLayoutMode
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUComputePassDescriptor
import com.cdodi.webgpu.bindings.GPUComputePassTimestampWrites
import com.cdodi.webgpu.bindings.GPUComputePipeline
import com.cdodi.webgpu.bindings.GPUComputePipelineDescriptor
import com.cdodi.webgpu.bindings.GPUMapMode
import com.cdodi.webgpu.bindings.GPUProgrammableStage
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.mapAsyncSuspend
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.runtime.Float32Staging
import com.cdodi.webgpu.shader.compileShader
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A flock on the GPU (`shaders/boids/step.wgsl`). Boids are `{ pos: vec2f, vel: vec2f }`, in two buffers that take
 * turns. Each step either compares every pair ([encodeNaiveStep]) or goes through the spatial grid ([encodeGridStep]):
 * count boids per cell, scan the counts into cell starts, scatter the boids into cell order, then search the 3×3
 * cells around each one. [current] is what the scene draws.
 *
 * @param capacity the most boids the buffers hold; a bigger flock needs a new simulation.
 */
class BoidsSimulation private constructor(
    private val gpu: GpuContext,
    val capacity: Int,
    private val pipelines: Map<String, GPUComputePipeline>,
    private val scan: GpuScan,
) : AutoCloseable {

    /** How many boids fly. */
    var count: Int = 0
        private set

    private val resources = gpu.resourceScope()
    private val boids = List(2) { boidBuffer("boids $it") }
    private val sorted = boidBuffer("boids by cell")
    private val boidCell = storage(capacity * 4, "boid cell")
    private val boidRank = storage(capacity * 4, "boid rank in cell")
    private val cellCounts = storage(MAX_CELLS * 4, "cell counts", copyDestination = true)
    private val cellStarts = storage(MAX_CELLS * 4, "cell starts")
    private val params = resources.buffer(
        GPUBufferDescriptor(size = BoidsParams.UNIFORM_FLOATS * 4.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "boids params")
    )
    private val paramValues = FloatArray(BoidsParams.UNIFORM_FLOATS)
    private val paramStaging = Float32Staging(paramValues.size)
    private var currentIndex = 0
    private val bindGroups = mutableMapOf<Pair<String, Int>, GPUBindGroup>()

    /** The cells of the last grid step, across and down. */
    var grid: Pair<Int, Int> = 1 to 1
        private set

    /** The buffer holding the current boids. */
    val current: GPUBuffer get() = boids[currentIndex]

    /** Every boid against every other: O(N²), for small flocks and as the check for the grid. */
    fun encodeNaiveStep(encoder: GPUCommandEncoder, params: BoidsParams, dt: Float) {
        if (count == 0) return
        writeParams(params, dt, 1, 1)
        dispatch(encoder, "stepNaive", "boids naive step")
        currentIndex = 1 - currentIndex
    }

    /**
     * The grid step. Cells are at least the perception radius wide (so the 3×3 around a boid holds everything it can
     * see) and there are at least 3 in each direction (so the 3×3 never visits a cell twice).
     *
     * @param timestamps the writes for each of the four compute passes, in order, when profiling.
     */
    fun encodeGridStep(encoder: GPUCommandEncoder, params: BoidsParams, dt: Float, timestamps: (String) -> GPUComputePassTimestampWrites? = { null }) {
        if (count == 0) return
        val gridX = (params.worldWidth / params.perception).toInt().coerceIn(MIN_CELLS_ACROSS, MAX_CELLS_ACROSS)
        val gridY = (params.worldHeight / params.perception).toInt().coerceIn(MIN_CELLS_ACROSS, MAX_CELLS_ACROSS)
        grid = gridX to gridY
        val cells = gridX * gridY
        writeParams(params, dt, gridX, gridY)

        encoder.clearBuffer(cellCounts, 0.0, cells * 4.0)
        dispatch(encoder, "countCells", "count", timestamps("count"))
        scan.encode(encoder, cellCounts, cellStarts, cells)
        dispatch(encoder, "scatter", "scatter", timestamps("scatter"))
        dispatch(encoder, "stepGrid", "neighbours", timestamps("neighbours"))
        currentIndex = 1 - currentIndex
    }

    /** Replaces the flock. */
    fun load(flock: List<Boid>) {
        require(flock.size <= capacity) { "${flock.size} boids don't fit in $capacity" }
        val values = Float32Array(max(flock.size, 1) * 4)
        flock.forEachIndexed { i, boid ->
            values[i * 4] = boid.position.x
            values[i * 4 + 1] = boid.position.y
            values[i * 4 + 2] = boid.velocity.x
            values[i * 4 + 3] = boid.velocity.y
        }
        gpu.queue.writeBuffer(current, 0.0, values)
        count = flock.size
    }

    /** [count] boids at random places in the world, flying in random directions between the min and max speed. */
    fun scatter(count: Int, params: BoidsParams, random: Random = Random) {
        require(count <= capacity) { "$count boids don't fit in $capacity" }
        val values = Float32Array(max(count, 1) * 4)
        for (i in 0 until count) {
            val angle = random.nextDouble(2 * PI)
            val speed = params.minSpeed + random.nextFloat() * (params.maxSpeed - params.minSpeed)
            values[i * 4] = random.nextFloat() * params.worldWidth
            values[i * 4 + 1] = random.nextFloat() * params.worldHeight
            values[i * 4 + 2] = (cos(angle) * speed).toFloat()
            values[i * 4 + 3] = (sin(angle) * speed).toFloat()
        }
        gpu.queue.writeBuffer(current, 0.0, values)
        this.count = count
    }

    /** Every boid, read back now. For tests: it waits for the GPU. */
    suspend fun readBoids(): List<Boid> {
        if (count == 0) return emptyList()
        val bytes = count * 16.0
        val staging = gpu.device.createBuffer(GPUBufferDescriptor(size = bytes, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.COPY_DST))
        try {
            val encoder = gpu.device.createCommandEncoder()
            encoder.copyBufferToBuffer(current, 0.0, staging, 0.0, bytes)
            gpu.queue.submit(listOf(encoder.finish()).toJsArray())
            staging.mapAsyncSuspend(GPUMapMode.READ)
            val values = Float32Array(staging.getMappedRange().unsafeCast<ArrayBuffer>())
            return List(count) { i -> Boid(Vec2(values[i * 4], values[i * 4 + 1]), Vec2(values[i * 4 + 2], values[i * 4 + 3])) }
        } finally {
            staging.destroy()
        }
    }

    override fun close() {
        resources.close()
        scan.close()
    }

    private fun writeParams(params: BoidsParams, dt: Float, gridX: Int, gridY: Int) {
        params.writeTo(paramValues, count, dt, gridX, gridY)
        gpu.queue.writeBuffer(this.params, 0.0, paramStaging.fill(paramValues))
    }

    private fun dispatch(encoder: GPUCommandEncoder, entryPoint: String, label: String, timestamps: GPUComputePassTimestampWrites? = null) {
        encoder.beginComputePass(GPUComputePassDescriptor(label = label, timestampWrites = timestamps)).apply {
            setPipeline(pipelines.getValue(entryPoint))
            setBindGroup(0, bindGroupFor(entryPoint))
            dispatchWorkgroups((count + WORKGROUP - 1) / WORKGROUP)
            end()
        }
    }

    /** Each entry point binds only what it uses (auto layouts), with the two boid buffers in their current roles. */
    private fun bindGroupFor(entryPoint: String): GPUBindGroup = bindGroups.getOrPut(entryPoint to currentIndex) {
        val boidsIn = boids[currentIndex]
        val boidsOut = boids[1 - currentIndex]
        val bindings: List<Pair<Int, GPUBuffer>> = when (entryPoint) {
            "stepNaive" -> listOf(0 to params, 1 to boidsIn, 2 to boidsOut)
            "countCells" -> listOf(0 to params, 1 to boidsIn, 4 to boidCell, 5 to boidRank, 6 to cellCounts)
            "scatter" -> listOf(0 to params, 1 to boidsIn, 3 to sorted, 4 to boidCell, 5 to boidRank, 7 to cellStarts)
            else -> listOf(0 to params, 2 to boidsOut, 3 to sorted, 6 to cellCounts, 7 to cellStarts)
        }
        gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = pipelines.getValue(entryPoint).getBindGroupLayout(0),
                entries = bindings.map { (binding, buffer) -> GPUBindGroupEntry(binding, GPUBufferBinding(buffer)) },
                label = entryPoint,
            )
        )
    }

    private fun boidBuffer(label: String) = resources.buffer(
        GPUBufferDescriptor(size = capacity * 16.0, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_SRC or GPUBufferUsage.COPY_DST, label = label)
    )

    private fun storage(bytes: Int, label: String, copyDestination: Boolean = false) = resources.buffer(
        GPUBufferDescriptor(size = bytes.toDouble(), usage = GPUBufferUsage.STORAGE or (if (copyDestination) GPUBufferUsage.COPY_DST else 0), label = label)
    )

    companion object {
        private const val WORKGROUP = 256
        private const val MIN_CELLS_ACROSS = 3
        private const val MAX_CELLS_ACROSS = 512
        private val MAX_CELLS = min(MAX_CELLS_ACROSS * MAX_CELLS_ACROSS, GpuScan.MAX_COUNT)

        /** Compiles the step ([stepCode]) and scan ([scanCode]) shaders for a flock of up to [capacity] boids. */
        suspend fun create(gpu: GpuContext, stepCode: String, scanCode: String, capacity: Int): BoidsSimulation {
            val module = gpu.compileShader(stepCode, label = "boids step")
            val pipelines = listOf("stepNaive", "countCells", "scatter", "stepGrid").associateWith { entryPoint ->
                gpu.device.createComputePipeline(
                    GPUComputePipelineDescriptor(compute = GPUProgrammableStage(module, entryPoint), layout = GPUAutoLayoutMode.auto, label = "boids $entryPoint")
                )
            }
            return BoidsSimulation(gpu, capacity, pipelines, GpuScan.create(gpu, scanCode))
        }
    }
}
