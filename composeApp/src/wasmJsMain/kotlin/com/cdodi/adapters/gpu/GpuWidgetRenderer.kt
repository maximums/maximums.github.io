package com.cdodi.adapters.gpu

import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPURenderPassEncoder
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.context.GpuContext

/**
 * Draws one [kind] of GPU-backed widget ([GpuWidgets]) for the scene they're shown over. The shell hands the scene its
 * renderers, so the scene draws widgets without knowing the features they come from. Every frame, for the visible
 * widgets of its kind, a renderer first encodes passes of its own ([encodeOffscreen]: a model's depth pass, into its
 * own target), then draws inside the scene's final pass, over the street ([draw]).
 */
interface GpuWidgetRenderer : AutoCloseable {

    val kind: GpuWidgets.Kind

    /**
     * Compiles pipelines and loads what it draws. The scene doesn't wait for it: the street goes up first, and a
     * renderer draws once it's prepared (if it fails, its widgets simply show nothing).
     */
    suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat)

    /** Passes of its own for [widgets], before the scene's final pass. */
    fun encodeOffscreen(encoder: GPUCommandEncoder, widgets: List<GpuWidgets.Widget>, frame: WidgetFrame) {}

    /** Draws [widgets] in the scene's final pass, whose target is [frame]'s size. */
    fun draw(pass: GPURenderPassEncoder, widgets: List<GpuWidgets.Widget>, frame: WidgetFrame)
}

/** The final pass's target, in device pixels like the widgets' rectangles, and the scene's time (folded seconds). */
class WidgetFrame(val width: Int, val height: Int, val time: Float)
