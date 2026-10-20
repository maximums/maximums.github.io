package com.cdodi.adapters.gpu

import blog.composeapp.generated.resources.Res
import com.cdodi.design.theme.AutumnTheme
import com.cdodi.webgpu.bindings.GPUAutoLayoutMode
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUComputePassDescriptor
import com.cdodi.webgpu.bindings.GPUComputePipelineDescriptor
import com.cdodi.webgpu.bindings.GPUMapMode
import com.cdodi.webgpu.bindings.GPUProgrammableStage
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.mapAsyncSuspend
import com.cdodi.webgpu.context.requestGpuContext
import com.cdodi.webgpu.shader.compileShader
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** The theme on its way to the shaders: the struct they declare, what reaches them, and autumn's look kept. */
class ThemeUniformsTest {

    private val declaration = Regex("""(\w+)\s*:\s*(vec4f|array<vec4f,\s*(\d+)>)\s*,""")

    @Test
    fun theShadersDeclareTheThemeAsItIsPacked() = runTest {
        val wgsl = Res.readBytes("files/shaders/common/theme.wgsl").decodeToString()
        val struct = wgsl.substringAfter("struct Theme {").substringBefore("}")
        val declared = declaration.findAll(struct).map { it.groupValues[1] to (it.groupValues[3].toIntOrNull() ?: 1) }.toList()
        val packed = ThemeUniforms.fields(AutumnTheme).map { (name, colours) -> name to colours.size }
        assertEquals(packed, declared)
    }

    @Test
    fun whatReachesAShaderIsWhatWasPacked() = runTest(timeout = 60.seconds) {
        val gpu = requestGpuContext() ?: return@runTest println("ThemeUniformsTest: no WebGPU adapter in this browser — skipped")
        gpu.use {
            GpuTheme.create(gpu) { AutumnTheme }.use { theme ->
                // A shader that copies the theme it's given into a buffer this test reads back.
                val code = theme.shader("@group(0) @binding(0) var<storage, read_write> copy: Theme;\n@compute @workgroup_size(1) fn main() { copy = theme; }")
                val pipeline = gpu.device.createComputePipeline(
                    GPUComputePipelineDescriptor(compute = GPUProgrammableStage(gpu.compileShader(code, label = "theme copy"), "main"), layout = GPUAutoLayoutMode.auto)
                )
                val bytes = ThemeUniforms.FLOATS * 4.0
                val copy = gpu.device.createBuffer(GPUBufferDescriptor(size = bytes, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_SRC))
                val readback = gpu.device.createBuffer(GPUBufferDescriptor(size = bytes, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.COPY_DST))
                val group = gpu.device.createBindGroup(
                    GPUBindGroupDescriptor(layout = pipeline.getBindGroupLayout(0), entries = listOf(GPUBindGroupEntry(0, GPUBufferBinding(copy))))
                )
                val encoder = gpu.device.createCommandEncoder()
                encoder.beginComputePass(GPUComputePassDescriptor()).apply {
                    setPipeline(pipeline)
                    setBindGroup(0, group)
                    setBindGroup(GpuTheme.GROUP, theme.bindGroup(pipeline.getBindGroupLayout(GpuTheme.GROUP)))
                    dispatchWorkgroups(1)
                    end()
                }
                encoder.copyBufferToBuffer(copy, 0.0, readback, 0.0, bytes)
                gpu.queue.submit(listOf(encoder.finish()).toJsArray())
                readback.mapAsyncSuspend(GPUMapMode.READ)
                val mapped = Float32Array(readback.getMappedRange().unsafeCast<ArrayBuffer>())
                val expected = FloatArray(ThemeUniforms.FLOATS).also { ThemeUniforms.pack(AutumnTheme, it) }
                assertEquals(expected.toList(), List(ThemeUniforms.FLOATS) { mapped[it] })
                listOf(copy, readback).forEach { it.destroy() }
            }
        }
    }

    @Test
    fun autumnLooksAsTheShadersHadIt() {
        // The colours the shaders wrote out before the theme reached them: the autumn theme must give them back.
        val before = mapOf(
            "ground" to listOf(13, 16, 19),
            "accent" to listOf(216, 161, 90),
            "heat" to listOf(180, 83, 46),
            "cold" to listOf(126, 151, 163),
            "sky" to listOf(10, 13, 16, 16, 21, 26, 23, 22, 19, 30, 24, 18),
            "lights" to listOf(216, 161, 90, 180, 83, 46, 237, 230, 214, 126, 151, 163),
            "streetFog" to listOf(170, 178, 180),
            "fog" to listOf(140, 148, 152),
            "fallingNear" to listOf(222, 228, 230),
            "fallingFar" to listOf(200, 210, 214),
            "shade" to listOf(8, 10, 12),
            "ash" to listOf(64, 28, 20),
            "figure" to listOf(16, 19, 23),
        )
        val now = ThemeUniforms.fields(AutumnTheme).associate { (name, colours) ->
            name to colours.flatMap { listOf(it.red, it.green, it.blue).map { channel -> (channel * 255).roundToInt() } }
        }
        assertEquals(before, now)
    }
}
