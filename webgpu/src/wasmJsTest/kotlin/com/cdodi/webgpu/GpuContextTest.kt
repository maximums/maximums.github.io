package com.cdodi.webgpu

import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUCommandBuffer
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.context.requestGpuContext
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.shader.ShaderCompilationException
import com.cdodi.webgpu.shader.compileShader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.toFloat32Array
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Each test runs on a real GPU device, or is skipped (with a message) where the browser has none. */
class GpuContextTest {

    private fun gpuTest(name: String, body: suspend (GpuContext) -> Unit) = runTest {
        val context = requestGpuContext()
        if (context == null) {
            println("GpuContextTest.$name: no WebGPU adapter in this browser — skipped")
            return@runTest
        }
        context.use { body(it) }
    }

    @Test
    fun providesDeviceQueueAndFormat() = gpuTest("providesDeviceQueueAndFormat") { gpu ->
        assertTrue(gpu.preferredFormat.unsafeCast<JsString>().toString().isNotEmpty())
        assertFalse(gpu.hasFeature("certainly-not-a-real-feature"))
    }

    @Test
    fun shaderErrorsCarryTheirLine() = gpuTest("shaderErrorsCarryTheirLine") { gpu ->
        val broken = "@compute @workgroup_size(1)\nfn main() {\n    let x: f32 = undefined_value;\n}\n"

        val error = assertFailsWith<ShaderCompilationException> { gpu.compileShader(broken, label = "broken") }

        assertEquals("broken", error.label)
        assertEquals(3, error.messages.first().line)
    }

    @Test
    fun validShadersCompile() = gpuTest("validShadersCompile") { gpu ->
        gpu.compileShader("@compute @workgroup_size(1) fn main() {}", label = "empty")
    }

    @Test
    fun errorScopeCatchesValidationErrors() = gpuTest("errorScopeCatchesValidationErrors") { gpu ->
        // MAP_READ may only be combined with COPY_DST, so this buffer is invalid.
        val invalid = gpu.errorScope {
            gpu.device.createBuffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.STORAGE))
        }
        val valid = gpu.errorScope {
            gpu.device.createBuffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.COPY_DST))
        }

        assertNotNull(invalid.error)
        assertNull(valid.error)
    }

    @Test
    fun resourceScopeDestroysWhatItOwns() = gpuTest("resourceScopeDestroysWhatItOwns") { gpu ->
        val scope = gpu.resourceScope()
        val buffer = scope.buffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.COPY_DST))
        scope.close()

        // Writing to a destroyed buffer is a validation error.
        val write = gpu.errorScope { gpu.queue.writeBuffer(buffer, 0.0, floatArrayOf(1f).toFloat32Array()) }

        assertTrue(scope.isClosed)
        assertNotNull(write.error)
    }

    @Test
    fun uncapturedErrorsReachTheHandler() = gpuTest("uncapturedErrorsReachTheHandler") { gpu ->
        val received = CompletableDeferred<String>()
        gpu.onUncapturedError { received.complete(it) }.use {
            gpu.device.createBuffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.STORAGE))
            // Commands are batched until something flushes them to the GPU process; in the site every frame does.
            gpu.queue.submit(emptyList<GPUCommandBuffer>().toJsArray())
            assertTrue(received.await().isNotEmpty())
        }
    }

    @Test
    fun closedHandlersStopReceivingErrors() = gpuTest("closedHandlersStopReceivingErrors") { gpu ->
        var closedHandlerCalls = 0
        gpu.onUncapturedError { closedHandlerCalls++ }.close()

        val received = CompletableDeferred<String>()
        gpu.onUncapturedError { received.complete(it) }.use {
            gpu.device.createBuffer(GPUBufferDescriptor(size = 16.0, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.STORAGE))
            gpu.queue.submit(emptyList<GPUCommandBuffer>().toJsArray())
            received.await()
        }

        assertEquals(0, closedHandlerCalls, "a closed handler must be removed, not just forgotten")
    }
}
