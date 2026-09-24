package com.cdodi.webgpu

import com.cdodi.webgpu.bindings.GPU
import com.cdodi.webgpu.bindings.GPUAutoLayoutModeEntries
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUComputePipelineDescriptor
import com.cdodi.webgpu.bindings.GPUMapMode
import com.cdodi.webgpu.bindings.GPUProgrammableStage
import com.cdodi.webgpu.bindings.GPUShaderModuleDescriptor
import com.cdodi.webgpu.bindings.mapAsyncSuspend
import com.cdodi.webgpu.bindings.requestAdapterSuspend
import com.cdodi.webgpu.bindings.requestDeviceSuspend
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.toFloat32Array
import kotlin.test.Test
import kotlin.test.assertEquals

/** `navigator.gpu`, or null where the browser has no WebGPU. Replaced by the idiomatic layer in PLAN 1.6. */
@JsFun("() => navigator.gpu ?? null")
private external fun navigatorGpu(): GPU?

// language=wgsl
private const val DOUBLING_SHADER = """
@group(0) @binding(0) var<storage, read_write> data: array<f32>;

@compute @workgroup_size(1) fn double(@builtin(global_invocation_id) id: vec3u) {
    data[id.x] = data[id.x] * 2.0;
}
"""

/**
 * Runs a compute shader through the generated bindings and reads the result back.
 * Skipped (with a message) where the browser has no WebGPU adapter.
 */
class WebGpuSmokeTest {

    @Test
    fun computeShaderDoublesNumbers() = runTest {
        val adapter = navigatorGpu()?.requestAdapterSuspend()
        if (adapter == null) {
            println("WebGpuSmokeTest: no WebGPU adapter in this browser — skipped")
            return@runTest
        }

        val device = adapter.requestDeviceSuspend()
        val input = floatArrayOf(1f, 4f, 5f)
        val byteSize = (input.size * Float.SIZE_BYTES).toDouble()

        val pipeline = device.createComputePipeline(
            GPUComputePipelineDescriptor(
                layout = GPUAutoLayoutModeEntries.auto,
                compute = GPUProgrammableStage(
                    module = device.createShaderModule(GPUShaderModuleDescriptor(code = DOUBLING_SHADER)),
                    entryPoint = "double",
                ),
            )
        )
        val work = device.createBuffer(
            GPUBufferDescriptor(size = byteSize, usage = GPUBufferUsage.STORAGE or GPUBufferUsage.COPY_SRC or GPUBufferUsage.COPY_DST)
        )
        val readback = device.createBuffer(
            GPUBufferDescriptor(size = byteSize, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.COPY_DST)
        )
        device.queue.writeBuffer(work, 0.0, input.toFloat32Array())

        val bindGroup = device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(0),
                entries = listOf(GPUBindGroupEntry(binding = 0, resource = work)),
            )
        )
        val encoder = device.createCommandEncoder()
        encoder.beginComputePass().apply {
            setPipeline(pipeline)
            setBindGroup(0, bindGroup)
            dispatchWorkgroups(input.size)
            end()
        }
        encoder.copyBufferToBuffer(work, 0.0, readback, 0.0, byteSize)
        device.queue.submit(listOf(encoder.finish()).toJsArray())

        readback.mapAsyncSuspend(GPUMapMode.READ)
        val mapped = Float32Array(readback.getMappedRange().unsafeCast<ArrayBuffer>())
        val result = List(input.size) { i -> mapped[i] }
        readback.unmap()

        assertEquals(listOf(2f, 8f, 10f), result)
    }
}
