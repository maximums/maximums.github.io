package com.cdodi.features.life

import com.cdodi.data.gameoflife.Cell
import com.cdodi.data.gameoflife.LifeRule
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBindGroupLayout
import com.cdodi.webgpu.bindings.GPUBindGroupLayoutDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupLayoutEntry
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferBindingLayout
import com.cdodi.webgpu.bindings.GPUBufferBindingType
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUComputePipeline
import com.cdodi.webgpu.bindings.GPUComputePipelineDescriptor
import com.cdodi.webgpu.bindings.GPUMapMode
import com.cdodi.webgpu.bindings.GPUPipelineLayoutDescriptor
import com.cdodi.webgpu.bindings.GPUProgrammableStage
import com.cdodi.webgpu.bindings.GPUShaderStage
import com.cdodi.webgpu.bindings.mapAsyncSuspend
import com.cdodi.webgpu.bindings.readOnlyStorage
import com.cdodi.webgpu.bindings.storage
import com.cdodi.webgpu.bindings.uniform
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.runtime.Uint32Staging
import com.cdodi.webgpu.shader.compileShader
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.get
import org.khronos.webgl.set

/**
 * Life on the GPU, compute only. Two state buffers take turns ("ping-pong"): each generation reads one and writes the
 * other. The rule is a pair of bitmasks in a uniform, so every Life-like rule works. [current] is what the scene
 * draws; tests read it back and compare it with `LifeEngine`.
 *
 * The population is counted on the GPU (per workgroup, then one atomic add each) and read back through two rotating
 * `MAP_READ` buffers a frame or two behind, so the GPU never waits for the CPU: a buffer still on its way back is
 * skipped, and that frame's count is simply not read.
 */
class LifeSimulation private constructor(
    private val gpu: GpuContext,
    val width: Int,
    val height: Int,
    private val stepPipeline: GPUComputePipeline,
    private val countPipeline: GPUComputePipeline,
    layout: GPUBindGroupLayout,
    private val scope: CoroutineScope,
) : AutoCloseable {

    var rule: LifeRule = LifeRule.Conway
    var wrap: Boolean = false

    /** Generations stepped since the grid was last loaded or cleared. */
    var generation: Long = 0
        private set

    private val resources = gpu.resourceScope()
    private val cellCount = width * height
    private val states = List(2) { i ->
        resources.buffer(
            GPUBufferDescriptor(
                size = cellCount * 4.0,
                usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_SRC or GPUBufferUsage.COPY_DST,
                label = "life state $i",
            )
        )
    }
    private val params = resources.buffer(GPUBufferDescriptor(size = 32.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "life params"))
    private val counter = resources.buffer(
        GPUBufferDescriptor(size = 4.0, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_SRC or GPUBufferUsage.COPY_DST, label = "life population")
    )
    private val readbacks = List(2) { i ->
        Readback(resources.buffer(GPUBufferDescriptor(size = 4.0, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.COPY_DST, label = "life population readback $i")))
    }
    private var currentIndex = 0
    private val bindGroups = List(2) { i -> bindGroup(layout, from = states[i], to = states[1 - i]) }

    // struct Params { width, height, birth, survive, wrap, _pad0, _pad1, _pad2: u32 }
    private val paramValues = IntArray(8)
    private val paramStaging = Uint32Staging(paramValues.size)
    private val oneCell = Uint32Staging(1)

    private var pendingReadback: Readback? = null
    private var readbacksStarted = 0L
    private var newestCountShown = -1L
    private val population = MutableStateFlow(0)

    /** Live cells, a frame or two behind the GPU. */
    val livePopulation: StateFlow<Int> = population.asStateFlow()

    /** The buffer holding the current generation, one `u32` per cell (see step.wgsl). */
    val current: GPUBuffer get() = states[currentIndex]

    private val groupsX = (width + 7) / 8
    private val groupsY = (height + 7) / 8

    /**
     * Records [steps] generations into [encoder], then the copy of the last one's population into the readback buffer
     * picked by [slot] (the frame index). Call [afterSubmit] once the encoder's commands are submitted.
     */
    fun encodeSteps(encoder: GPUCommandEncoder, steps: Int, slot: Long) {
        if (steps <= 0) return
        writeParams()
        repeat(steps) {
            encoder.clearBuffer(counter)
            dispatch(encoder, stepPipeline)
            currentIndex = 1 - currentIndex
            generation++
        }
        encodeReadback(encoder, slot)
    }

    /** Records a count of the current generation's live cells, for after cells were drawn or cleared. */
    fun encodeCount(encoder: GPUCommandEncoder, slot: Long) {
        writeParams()
        encoder.clearBuffer(counter)
        dispatch(encoder, countPipeline)
        encodeReadback(encoder, slot)
    }

    /** Starts mapping the readback buffer the last encode copied into; its count arrives a frame or two later. */
    fun afterSubmit() {
        val readback = pendingReadback ?: return
        pendingReadback = null
        val order = readbacksStarted++
        scope.launch {
            readback.buffer.mapAsyncSuspend(GPUMapMode.READ)
            val count = Uint32Array(readback.buffer.getMappedRange().unsafeCast<ArrayBuffer>())[0]
            readback.buffer.unmap()
            readback.isBusy = false
            // Maps resolve in order in practice; this guard keeps a late one from showing an older count.
            if (order > newestCountShown) {
                newestCountShown = order
                population.value = count
            }
        }
    }

    /** Makes [cells] alive (age 1) or dead in the current generation, one small write per cell. */
    fun write(cells: Iterable<Cell>, alive: Boolean) {
        for (cell in cells) {
            if (cell.x !in 0 until width || cell.y !in 0 until height) continue
            gpu.queue.writeBuffer(current, (cell.y * width + cell.x) * 4.0, oneCell.fill(intArrayOf(if (alive) 1 else 0)))
        }
    }

    /** Replaces the whole grid: [cells] alive, everything else dead. */
    fun load(cells: Set<Cell>) {
        val values = Uint32Array(cellCount)
        for (cell in cells) {
            if (cell.x in 0 until width && cell.y in 0 until height) values[cell.y * width + cell.x] = 1
        }
        gpu.queue.writeBuffer(current, 0.0, values)
        generation = 0
    }

    /** Fills a centred square covering [fraction] of each side at random: each cell alive with probability [density]. */
    fun seed(density: Float, fraction: Float = 1f, random: Random = Random) {
        val values = Uint32Array(cellCount)
        val left = ((1f - fraction) / 2f * width).toInt()
        val top = ((1f - fraction) / 2f * height).toInt()
        for (y in top until height - top) {
            for (x in left until width - left) {
                if (random.nextFloat() < density) values[y * width + x] = 1
            }
        }
        gpu.queue.writeBuffer(current, 0.0, values)
        generation = 0
    }

    fun clear(encoder: GPUCommandEncoder) {
        encoder.clearBuffer(current)
        generation = 0
    }

    /** Every cell's value (see step.wgsl), read back now. For tests: it waits for the GPU. */
    suspend fun readValues(): IntArray {
        val staging = gpu.device.createBuffer(GPUBufferDescriptor(size = cellCount * 4.0, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.COPY_DST))
        try {
            val encoder = gpu.device.createCommandEncoder()
            encoder.copyBufferToBuffer(current, 0.0, staging, 0.0, cellCount * 4.0)
            gpu.queue.submit(listOf(encoder.finish()).toJsArray())
            staging.mapAsyncSuspend(GPUMapMode.READ)
            val values = Uint32Array(staging.getMappedRange().unsafeCast<ArrayBuffer>())
            return IntArray(cellCount) { values[it] }
        } finally {
            staging.destroy()
        }
    }

    /** The live cells of the current generation, read back now. For tests. */
    suspend fun readCells(): Set<Cell> = readValues().withIndex()
        .filter { (_, value) -> value and 0xFFFF != 0 }
        .mapTo(mutableSetOf()) { (index, _) -> IntOffset(index % width, index / width) }

    override fun close() = resources.close()

    private fun writeParams() {
        paramValues[0] = width
        paramValues[1] = height
        paramValues[2] = rule.birth
        paramValues[3] = rule.survive
        paramValues[4] = if (wrap) 1 else 0
        gpu.queue.writeBuffer(params, 0.0, paramStaging.fill(paramValues))
    }

    private fun dispatch(encoder: GPUCommandEncoder, pipeline: GPUComputePipeline) {
        encoder.beginComputePass().apply {
            setPipeline(pipeline)
            setBindGroup(0, bindGroups[currentIndex])
            dispatchWorkgroups(groupsX, groupsY)
            end()
        }
    }

    private fun encodeReadback(encoder: GPUCommandEncoder, slot: Long) {
        val readback = readbacks[(slot % readbacks.size).toInt()]
        if (readback.isBusy) return // still coming back from an earlier frame; this frame's count is skipped
        encoder.copyBufferToBuffer(counter, 0.0, readback.buffer, 0.0, 4.0)
        readback.isBusy = true
        pendingReadback = readback
    }

    private fun bindGroup(layout: GPUBindGroupLayout, from: GPUBuffer, to: GPUBuffer): GPUBindGroup = gpu.device.createBindGroup(
        GPUBindGroupDescriptor(
            layout = layout,
            entries = listOf(
                GPUBindGroupEntry(binding = 0, resource = GPUBufferBinding(params)),
                GPUBindGroupEntry(binding = 1, resource = GPUBufferBinding(from)),
                GPUBindGroupEntry(binding = 2, resource = GPUBufferBinding(to)),
                GPUBindGroupEntry(binding = 3, resource = GPUBufferBinding(counter)),
            ),
            label = "life step",
        )
    )

    private class Readback(val buffer: GPUBuffer) {
        var isBusy = false
    }

    companion object {
        /** Compiles the step shader (`files/shaders/life/step.wgsl`) and allocates a [width] × [height] grid. */
        suspend fun create(gpu: GpuContext, code: String, width: Int, height: Int, scope: CoroutineScope): LifeSimulation {
            val module = gpu.compileShader(code, label = "life step")
            // One layout for both entry points, so they share bind groups.
            val layout = gpu.device.createBindGroupLayout(
                GPUBindGroupLayoutDescriptor(
                    entries = listOf(
                        GPUBindGroupLayoutEntry(0, GPUShaderStage.COMPUTE, buffer = GPUBufferBindingLayout(type = GPUBufferBindingType.uniform)),
                        GPUBindGroupLayoutEntry(1, GPUShaderStage.COMPUTE, buffer = GPUBufferBindingLayout(type = GPUBufferBindingType.readOnlyStorage)),
                        GPUBindGroupLayoutEntry(2, GPUShaderStage.COMPUTE, buffer = GPUBufferBindingLayout(type = GPUBufferBindingType.storage)),
                        GPUBindGroupLayoutEntry(3, GPUShaderStage.COMPUTE, buffer = GPUBufferBindingLayout(type = GPUBufferBindingType.storage)),
                    ),
                    label = "life step",
                )
            )
            val pipelineLayout = gpu.device.createPipelineLayout(GPUPipelineLayoutDescriptor(bindGroupLayouts = listOf(layout)))
            fun pipeline(entryPoint: String) = gpu.device.createComputePipeline(
                GPUComputePipelineDescriptor(compute = GPUProgrammableStage(module, entryPoint), layout = pipelineLayout, label = "life $entryPoint")
            )
            return LifeSimulation(gpu, width, height, pipeline("step"), pipeline("count"), layout, scope)
        }
    }
}
