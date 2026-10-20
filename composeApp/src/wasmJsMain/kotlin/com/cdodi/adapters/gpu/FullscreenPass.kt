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
import com.cdodi.webgpu.bindings.GPURenderPassTimestampWrites
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

/** Overwritten by the full-screen triangle: never seen. */
private val UNSEEN = GPUColorDict(0.0, 0.0, 0.0, 1.0)

/**
 * One full-screen triangle and a fragment shader, the shape of every background and every blend. The WGSL module
 * provides `vs` and `fs`; its bindings live in group 0, laid out automatically. Drawn in a [GpuTheme], it reads the
 * theme's colours too, in group 1.
 */
class FullscreenPass private constructor(private val pipeline: GPURenderPipeline, private val themed: GPUBindGroup?) {

    val bindGroupLayout: GPUBindGroupLayout get() = pipeline.getBindGroupLayout(0)

    fun encode(encoder: GPUCommandEncoder, target: GPUTextureView, bindGroup: GPUBindGroup, timestamps: GPURenderPassTimestampWrites? = null) {
        encoder.beginRenderPass(passInto(target, timestamps = timestamps)).apply {
            setPipeline(pipeline)
            setBindGroup(0, bindGroup)
            if (themed != null) setBindGroup(GpuTheme.GROUP, themed)
            draw(3)
            end()
        }
    }

    companion object {
        suspend fun create(gpu: GpuContext, code: String, format: GPUTextureFormat, label: String, theme: GpuTheme? = null): FullscreenPass {
            val module = gpu.compileShader(theme?.shader(code) ?: code, label = label)
            val pipeline = gpu.device.createRenderPipeline(
                GPURenderPipelineDescriptor(
                    layout = GPUAutoLayoutMode.auto,
                    vertex = GPUVertexState(module = module, entryPoint = "vs"),
                    fragment = GPUFragmentState(module = module, entryPoint = "fs", targets = listOf(GPUColorTargetState(format = format))),
                    label = label,
                )
            )
            return FullscreenPass(pipeline, theme?.bindGroup(pipeline.getBindGroupLayout(GpuTheme.GROUP)))
        }

        /** Clears [target] to [ground] (the theme's): a scene that isn't ready yet. */
        fun clear(encoder: GPUCommandEncoder, target: GPUTextureView, ground: GPUColorDict) {
            encoder.beginRenderPass(passInto(target, ground)).end()
        }

        private fun passInto(target: GPUTextureView, clear: GPUColorDict = UNSEEN, timestamps: GPURenderPassTimestampWrites? = null) = GPURenderPassDescriptor(
            colorAttachments = listOf(
                GPURenderPassColorAttachment(view = target, clearValue = clear, loadOp = GPULoadOp.clear, storeOp = GPUStoreOp.store)
            ),
            timestampWrites = timestamps,
        )
    }
}
