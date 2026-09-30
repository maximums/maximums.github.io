package com.cdodi.adapters.gpu

import com.cdodi.webgpu.bindings.GPUAutoLayoutMode
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupLayout
import com.cdodi.webgpu.bindings.GPUColorDict
import com.cdodi.webgpu.bindings.GPUColorTargetState
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUFragmentState
import com.cdodi.webgpu.bindings.GPULoadOp
import com.cdodi.webgpu.bindings.GPURenderPassColorAttachment
import com.cdodi.webgpu.bindings.GPURenderPassDescriptor
import com.cdodi.webgpu.bindings.GPURenderPipeline
import com.cdodi.webgpu.bindings.GPURenderPipelineDescriptor
import com.cdodi.webgpu.bindings.GPUStoreOp
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.bindings.GPUVertexState
import com.cdodi.webgpu.bindings.auto
import com.cdodi.webgpu.bindings.clear
import com.cdodi.webgpu.bindings.store
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.shader.compileShader

/** `Night` from DESIGN.md: what a target shows before its scene is ready. */
private val NIGHT = GPUColorDict(r = 0x0D / 255.0, g = 0x10 / 255.0, b = 0x13 / 255.0, a = 1.0)

/**
 * One full-screen triangle and a fragment shader, the shape of every background and every blend. The WGSL module
 * provides `vs` and `fs`; its bindings live in group 0, laid out automatically.
 */
class FullscreenPass private constructor(private val pipeline: GPURenderPipeline) {

    val bindGroupLayout: GPUBindGroupLayout get() = pipeline.getBindGroupLayout(0)

    fun encode(encoder: GPUCommandEncoder, target: GPUTextureView, bindGroup: GPUBindGroup) {
        encoder.beginRenderPass(passInto(target)).apply {
            setPipeline(pipeline)
            setBindGroup(0, bindGroup)
            draw(3)
            end()
        }
    }

    companion object {
        suspend fun create(gpu: GpuContext, code: String, format: GPUTextureFormat, label: String): FullscreenPass {
            val module = gpu.compileShader(code, label = label)
            val pipeline = gpu.device.createRenderPipeline(
                GPURenderPipelineDescriptor(
                    layout = GPUAutoLayoutMode.auto,
                    vertex = GPUVertexState(module = module, entryPoint = "vs"),
                    fragment = GPUFragmentState(module = module, entryPoint = "fs", targets = listOf(GPUColorTargetState(format = format))),
                    label = label,
                )
            )
            return FullscreenPass(pipeline)
        }

        /** Clears [target] to Night: a scene that isn't ready yet. */
        fun clear(encoder: GPUCommandEncoder, target: GPUTextureView) {
            encoder.beginRenderPass(passInto(target)).end()
        }

        private fun passInto(target: GPUTextureView) = GPURenderPassDescriptor(
            colorAttachments = listOf(
                GPURenderPassColorAttachment(view = target, clearValue = NIGHT, loadOp = GPULoadOp.clear, storeOp = GPUStoreOp.store)
            )
        )
    }
}
