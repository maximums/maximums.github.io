package com.cdodi.features.life

import androidx.compose.ui.unit.IntOffset
import blog.composeapp.generated.resources.Res
import com.cdodi.adapters.compose.SHADER_TIME_PERIOD
import com.cdodi.adapters.gpu.FullscreenPass
import com.cdodi.adapters.gpu.GpuProfiler
import com.cdodi.adapters.gpu.Scene
import com.cdodi.adapters.input.InputState
import com.cdodi.core.time.Clock
import com.cdodi.core.time.FixedStepper
import com.cdodi.core.time.Frame
import com.cdodi.data.gameoflife.Cell
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.ResourceScope
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.resource.Float32Staging
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The Game of Life as a scene: a [LifeSimulation] stepped on the life clock at the page's speed, drawn as embers by
 * one full-screen pass, with zoom and pan.
 *
 * Input, while the pointer isn't on a widget:
 * - the wheel zooms around the pointer;
 * - dragging draws cells while paused (with Shift, erases them), and pans while running;
 * - dragging with the secondary or middle button pans at any time.
 */
class LifeScene(
    override val clock: Clock,
    private val controller: LifeController,
    private val scope: CoroutineScope,
    private val profiler: GpuProfiler? = null,
    private val gridWidth: Int = 2048,
    private val gridHeight: Int = 2048,
) : Scene {

    private lateinit var gpu: GpuContext
    private lateinit var resources: ResourceScope
    private lateinit var simulation: LifeSimulation
    private lateinit var pass: FullscreenPass
    private lateinit var viewBuffer: GPUBuffer
    private val bindGroups = mutableMapOf<GPUBuffer, GPUBindGroup>()

    // struct View { resolution: vec2f, origin: vec2f, cellSize, time, width, height: f32 }
    private val view = FloatArray(8)
    private val viewStaging = Float32Staging(view.size)

    private var stepper = FixedStepper(1f / controller.status.value.generationsPerSecond)
    private var pendingSteps = 0
    private var needsCount = true
    private var frameIndex = 0L

    // The view: pixels per cell, and the grid position at the target's top-left corner.
    private var targetWidth = 0
    private var targetHeight = 0
    private var cellSize = 0f
    private var originX = 0f
    private var originY = 0f

    // The drag in progress.
    private var lastX = 0f
    private var lastY = 0f
    private var wasDragging = false
    private var lastDrawn: Cell? = null

    override suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat) {
        this.gpu = gpu
        resources = gpu.resourceScope()
        simulation = LifeSimulation.create(gpu, Res.readBytes("files/shaders/life/step.wgsl").decodeToString(), gridWidth, gridHeight, scope)
        pass = FullscreenPass.create(gpu, Res.readBytes("files/shaders/life/render.wgsl").decodeToString(), format, label = "life")
        viewBuffer = resources.buffer(
            GPUBufferDescriptor(size = (view.size * Float.SIZE_BYTES).toDouble(), usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "life view")
        )
        simulation.seed(density = 0.2f, fraction = 0.3f)
    }

    override fun resize(width: Int, height: Int) {
        if (cellSize == 0f) {
            targetWidth = width
            targetHeight = height
            fitView()
            return
        }
        // Keep the same grid position in the middle of the screen.
        val centreX = originX + targetWidth / 2f / cellSize
        val centreY = originY + targetHeight / 2f / cellSize
        targetWidth = width
        targetHeight = height
        originX = centreX - width / 2f / cellSize
        originY = centreY - height / 2f / cellSize
    }

    override fun update(frame: Frame, input: InputState) {
        val status = controller.status.value
        simulation.rule = status.rule
        simulation.wrap = status.wrap
        if (abs(stepper.step - 1f / status.generationsPerSecond) > 1e-6f) stepper = FixedStepper(1f / status.generationsPerSecond)

        if (controller.fitRequested) {
            controller.fitRequested = false
            fitView()
        }
        if (controller.scatterRequested) {
            controller.scatterRequested = false
            simulation.seed(density = 0.2f, fraction = 0.3f)
            needsCount = true
        }
        pendingSteps += controller.stepsRequested
        controller.stepsRequested = 0
        // Paused, the clock's dt is zero, so the stepper adds nothing.
        pendingSteps += stepper.advance(frame.dt) {}

        handleInput(input, isPaused = status.isPaused)
        view[5] = (frame.elapsed % SHADER_TIME_PERIOD).toFloat()
    }

    override fun encode(encoder: GPUCommandEncoder, target: GPUTextureView) {
        if (controller.clearRequested) {
            controller.clearRequested = false
            simulation.clear(encoder)
            needsCount = true
        }
        if (pendingSteps > 0) {
            simulation.encodeSteps(encoder, pendingSteps, frameIndex, profiler?.span("step"))
        } else if (needsCount) {
            simulation.encodeCount(encoder, frameIndex)
        }
        pendingSteps = 0
        needsCount = false
        frameIndex++

        view[0] = targetWidth.toFloat()
        view[1] = targetHeight.toFloat()
        view[2] = originX
        view[3] = originY
        view[4] = cellSize
        view[6] = gridWidth.toFloat()
        view[7] = gridHeight.toFloat()
        gpu.queue.writeBuffer(viewBuffer, 0.0, viewStaging.fill(view))
        pass.encode(encoder, target, bindGroupFor(simulation.current), profiler?.span("draw")?.render())
    }

    override fun afterSubmit() {
        simulation.afterSubmit()
        controller.report(simulation.generation, simulation.livePopulation.value, gridWidth, gridHeight)
    }

    override fun close() {
        if (::simulation.isInitialized) simulation.close()
        if (::resources.isInitialized) resources.close()
    }

    /** About 120 cells across the shorter side of the screen, centred on the grid. */
    private fun fitView() {
        cellSize = max(2f, min(targetWidth, targetHeight) / 120f)
        originX = gridWidth / 2f - targetWidth / 2f / cellSize
        originY = gridHeight / 2f - targetHeight / 2f / cellSize
    }

    private fun handleInput(input: InputState, isPaused: Boolean) {
        if (!input.hasPointer) {
            wasDragging = false
            lastDrawn = null
            return
        }
        val x = input.pointerX
        val y = input.pointerY

        if (input.wheel != 0f) {
            // Zoom around the pointer: the grid position under it stays under it.
            val gridX = originX + x / cellSize
            val gridY = originY + y / cellSize
            cellSize = (cellSize * exp(-input.wheel * 0.0015f)).coerceIn(MIN_CELL_SIZE, MAX_CELL_SIZE)
            originX = gridX - x / cellSize
            originY = gridY - y / cellSize
        }

        // A quick click can press and release within one frame: count its press even though the button is up again.
        val pressing = input.isPressed || input.presses > 0
        val panning = input.isPanPressed || (input.isPressed && !isPaused)
        val drawing = pressing && isPaused && !input.isPanPressed
        if (panning && wasDragging) {
            originX -= (x - lastX) / cellSize
            originY -= (y - lastY) / cellSize
        }
        if (drawing) {
            val cell = IntOffset(floor(originX + x / cellSize).toInt(), floor(originY + y / cellSize).toInt())
            // A new press starts a new stroke: two quick clicks are never joined by a line.
            val from = lastDrawn?.takeIf { input.presses == 0 } ?: cell
            simulation.write(line(from, cell), alive = !input.isShiftPressed)
            lastDrawn = if (input.isPressed) cell else null
            needsCount = true
        } else {
            lastDrawn = null
        }

        wasDragging = panning || drawing
        lastX = x
        lastY = y
    }

    private fun bindGroupFor(cells: GPUBuffer): GPUBindGroup = bindGroups.getOrPut(cells) {
        gpu.device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = pass.bindGroupLayout,
                entries = listOf(
                    GPUBindGroupEntry(binding = 0, resource = GPUBufferBinding(viewBuffer)),
                    GPUBindGroupEntry(binding = 1, resource = GPUBufferBinding(cells)),
                ),
                label = "life render",
            )
        )
    }

    private companion object {
        const val MIN_CELL_SIZE = 0.5f
        const val MAX_CELL_SIZE = 96f

        /** The cells on the line from [from] to [to] (Bresenham), so a fast stroke leaves no gaps. */
        fun line(from: Cell, to: Cell): List<Cell> {
            val cells = mutableListOf<Cell>()
            var x = from.x
            var y = from.y
            val dx = abs(to.x - x)
            val dy = -abs(to.y - y)
            val stepX = if (x < to.x) 1 else -1
            val stepY = if (y < to.y) 1 else -1
            var error = dx + dy
            while (true) {
                cells += IntOffset(x, y)
                if (x == to.x && y == to.y) return cells
                val doubled = 2 * error
                if (doubled >= dy) {
                    error += dy
                    x += stepX
                }
                if (doubled <= dx) {
                    error += dx
                    y += stepY
                }
            }
        }
    }
}
