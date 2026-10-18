package com.cdodi.adapters.gpu

import blog.composeapp.generated.resources.Res
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUMapMode
import com.cdodi.webgpu.bindings.mapAsyncSuspend
import com.cdodi.webgpu.context.requestGpuContext
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** The GPU scan against a CPU scan, on random input, around every block boundary. Skipped without a WebGPU adapter. */
class GpuScanTest {

    private fun cpuExclusiveScan(values: IntArray): IntArray {
        var sum = 0
        return IntArray(values.size) { i -> sum.also { sum += values[i] } }
    }

    @Test
    fun matchesACpuScanAcrossBlockBoundaries() = runTest(timeout = 60.seconds) {
        val gpu = requestGpuContext() ?: return@runTest println("GpuScanTest: no WebGPU adapter in this browser — skipped")
        gpu.use {
            GpuScan.create(gpu, Res.readBytes("files/shaders/common/scan.wgsl").decodeToString()).use { scan ->
                val random = Random(42)
                for (size in listOf(1, 7, 511, 512, 513, 1024, 5_000, 100_000, GpuScan.MAX_COUNT)) {
                    val values = IntArray(size) { random.nextInt(0, 64) }
                    val bytes = size * 4.0
                    val usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_DST or GPUBufferUsage.COPY_SRC
                    val input = gpu.device.createBuffer(GPUBufferDescriptor(size = bytes, usage = usage))
                    val output = gpu.device.createBuffer(GPUBufferDescriptor(size = bytes, usage = usage))
                    val readback = gpu.device.createBuffer(GPUBufferDescriptor(size = bytes, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.COPY_DST))
                    gpu.queue.writeBuffer(input, 0.0, Uint32Array(size).apply { values.forEachIndexed { i, v -> this[i] = v } })

                    val encoder = gpu.device.createCommandEncoder()
                    scan.encode(encoder, input, output, size)
                    encoder.copyBufferToBuffer(output, 0.0, readback, 0.0, bytes)
                    gpu.queue.submit(listOf(encoder.finish()).toJsArray())
                    readback.mapAsyncSuspend(GPUMapMode.READ)
                    val mapped = Uint32Array(readback.getMappedRange().unsafeCast<ArrayBuffer>())
                    val result = IntArray(size) { mapped[it] }

                    assertEquals(cpuExclusiveScan(values).toList(), result.toList(), "scan of $size values")
                    listOf(input, output, readback).forEach { it.destroy() }
                }
            }
        }
    }
}
