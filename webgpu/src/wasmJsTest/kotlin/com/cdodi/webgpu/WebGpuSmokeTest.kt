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
import com.cdodi.webgpu.runtime.await
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.toFloat32Array
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals

// The generated requestAdapter() cannot express "no adapter" yet (REVIEW W1: nullability), and a null would
// hang the suspend wrapper. So the test first asks in plain JS whether an adapter exists.
@JsFun("() => navigator.gpu ? navigator.gpu.requestAdapter().then(a => a !== null) : Promise.resolve(false)")
private external fun hasWebGpuAdapter(): Promise<JsBoolean>

@JsFun("() => navigator.gpu")
private external fun navigatorGpu(): GPU

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
        if (!hasWebGpuAdapter().await().toBoolean()) {
            println("WebGpuSmokeTest: no WebGPU adapter in this browser — skipped")
            return@runTest
        }

        val device = navigatorGpu().requestAdapterSuspend().requestDeviceSuspend()
        val input = floatArrayOf(1f, 4f, 5f)
        val byteSize = input.size * Float.SIZE_BYTES

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

        val data = input.toFloat32Array()
        // `size` is optional in the IDL but required by the current bindings (REVIEW W1: optional arguments).
        device.queue.writeBuffer(work, 0.toJsNumber(), data, 0.toJsNumber(), input.size.toJsNumber())

        val bindGroup = device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(0.toJsNumber()),
                entries = listOf(GPUBindGroupEntry(binding = 0, resource = work)),
            )
        )
        val encoder = device.createCommandEncoder()
        encoder.beginComputePass().apply {
            setPipeline(pipeline)
            setBindGroup(0.toJsNumber(), bindGroup)
            dispatchWorkgroups(input.size.toJsNumber())
            end()
        }
        encoder.copyBufferToBuffer(work, 0.toJsNumber(), readback, 0.toJsNumber(), byteSize.toJsNumber())
        device.queue.submit(listOf(encoder.finish()).toJsArray())

        readback.mapAsyncSuspend(GPUMapMode.READ.toJsNumber(), 0.toJsNumber(), byteSize.toJsNumber())
        val mapped = Float32Array(readback.getMappedRange(0.toJsNumber(), byteSize.toJsNumber())!!.unsafeCast<ArrayBuffer>())
        val result = List(input.size) { i -> mapped[i] }
        readback.unmap()

        assertEquals(listOf(2f, 8f, 10f), result)
    }
}

