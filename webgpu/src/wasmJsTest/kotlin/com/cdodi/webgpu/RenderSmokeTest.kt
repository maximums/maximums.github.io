package com.cdodi.webgpu

import com.cdodi.webgpu.bindings.GPUAutoLayoutMode
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUColorDict
import com.cdodi.webgpu.bindings.GPUColorTargetState
import com.cdodi.webgpu.bindings.GPUExtent3DDict
import com.cdodi.webgpu.bindings.GPUFragmentState
import com.cdodi.webgpu.bindings.GPULoadOp
import com.cdodi.webgpu.bindings.GPUMapMode
import com.cdodi.webgpu.bindings.GPURenderPassColorAttachment
import com.cdodi.webgpu.bindings.GPURenderPassDescriptor
import com.cdodi.webgpu.bindings.GPURenderPipelineDescriptor
import com.cdodi.webgpu.bindings.GPUStoreOp
import com.cdodi.webgpu.bindings.GPUTexelCopyBufferInfo
import com.cdodi.webgpu.bindings.GPUTexelCopyTextureInfo
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureUsage
import com.cdodi.webgpu.bindings.GPUVertexState
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.clear
import com.cdodi.webgpu.bindings.mapAsyncSuspend
import com.cdodi.webgpu.bindings.rgba8unorm
import com.cdodi.webgpu.bindings.store
import com.cdodi.webgpu.context.requestGpuContext
import com.cdodi.webgpu.resource.resourceScope
import com.cdodi.webgpu.shader.compileShader
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// language=wgsl
private const val TRIANGLE_SHADER = """
@vertex fn vs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
    // One triangle larger than the viewport, so it covers every pixel.
    let corners = array(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
    return vec4f(corners[i], 0.0, 1.0);
}

@fragment fn fs() -> @location(0) vec4f {
    return vec4f(1.0, 0.5, 0.0, 1.0);
}
"""

private const val SIZE = 8
private const val BYTES_PER_ROW = 256 // copies must align rows to 256 bytes

/**
 * Renders a triangle into an offscreen texture and reads the pixels back: render pipeline, texture, render pass and
 * texture-to-buffer copy, all through the generated bindings and the idiomatic layer. Skipped without an adapter.
 */
class RenderSmokeTest {

    @Test
    fun triangleFillsTheTexture() = runTest {
        val gpu = requestGpuContext()
        if (gpu == null) {
            println("RenderSmokeTest: no WebGPU adapter in this browser — skipped")
            return@runTest
        }

        gpu.use {
            gpu.resourceScope().use { resources ->
                val shader = gpu.compileShader(TRIANGLE_SHADER, label = "triangle")
                val pipeline = gpu.device.createRenderPipeline(
                    GPURenderPipelineDescriptor(
                        layout = GPUAutoLayoutMode.auto,
                        vertex = GPUVertexState(module = shader, entryPoint = "vs"),
                        fragment = GPUFragmentState(
                            module = shader,
                            entryPoint = "fs",
                            targets = listOf(GPUColorTargetState(format = GPUTextureFormat.rgba8unorm)),
                        ),
                    )
                )
                val target = resources.texture(
                    GPUTextureDescriptor(
                        size = GPUExtent3DDict(width = SIZE, height = SIZE),
                        format = GPUTextureFormat.rgba8unorm,
                        usage = GPUTextureUsage.RENDER_ATTACHMENT or GPUTextureUsage.COPY_SRC,
                    )
                )
                val readback = resources.buffer(
                    GPUBufferDescriptor(size = (BYTES_PER_ROW * SIZE).toDouble(), usage = GPUBufferUsage.COPY_DST or GPUBufferUsage.MAP_READ)
                )

                val encoder = gpu.device.createCommandEncoder()
                encoder.beginRenderPass(
                    GPURenderPassDescriptor(
                        colorAttachments = listOf(
                            GPURenderPassColorAttachment(
                                view = target.createView(),
                                clearValue = GPUColorDict(r = 0.0, g = 0.0, b = 0.0, a = 0.0),
                                loadOp = GPULoadOp.clear,
                                storeOp = GPUStoreOp.store,
                            )
                        )
                    )
                ).apply {
                    setPipeline(pipeline)
                    draw(3)
                    end()
                }
                encoder.copyTextureToBuffer(
                    GPUTexelCopyTextureInfo(texture = target),
                    GPUTexelCopyBufferInfo(buffer = readback, bytesPerRow = BYTES_PER_ROW),
                    GPUExtent3DDict(width = SIZE, height = SIZE),
                )
                gpu.queue.submit(listOf(encoder.finish()).toJsArray())

                readback.mapAsyncSuspend(GPUMapMode.READ)
                val bytes = Uint8Array(readback.getMappedRange().unsafeCast<ArrayBuffer>())
                fun pixel(x: Int, y: Int) = List(4) { channel -> bytes[y * BYTES_PER_ROW + x * 4 + channel].toInt() and 0xFF }

                for ((x, y) in listOf(0 to 0, SIZE - 1 to 0, 0 to SIZE - 1, SIZE - 1 to SIZE - 1, SIZE / 2 to SIZE / 2)) {
                    val (r, g, b, a) = pixel(x, y)
                    assertEquals(255, r, "red at $x,$y")
                    assertTrue(g in 127..128, "green at $x,$y was $g")
                    assertEquals(0, b, "blue at $x,$y")
                    assertEquals(255, a, "alpha at $x,$y")
                }
                readback.unmap()
            }
        }
    }
}
