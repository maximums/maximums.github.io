package com.cdodi.adapters.gpu.effects

import com.cdodi.core.navigation.effect.Effect
import com.cdodi.core.navigation.effect.EffectRegistry
import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.effect.Layer
import com.cdodi.core.time.Frame
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.context.GpuContext

object GpuEffectIds {
    const val FOG_ROLL = "fog-roll"
    const val FOG_HOLD = "fog-hold"
    const val FOG_CLEAR = "fog-clear"
}

/**
 * Draws transition effects on the WebGPU layer. It gets both scenes already rendered (the one being left and the one
 * being entered; the same texture when they share a scene) and blends them into the target as the effect's state says.
 */
interface GpuEffectRenderer : AutoCloseable {
    val ids: Set<String>

    suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat)

    fun resize(width: Int, height: Int)

    fun encode(encoder: GPUCommandEncoder, target: GPUTextureView, from: GPUTextureView, to: GPUTextureView, state: EffectState, frame: Frame)
}

/**
 * The effects this build draws on the GPU layer. Graph validation checks against it whether or not WebGPU is available
 * at runtime: without it, GPU effects are simply not drawn, and the UI layer carries the transition.
 */
object GpuEffects : EffectRegistry {

    fun renderers(): List<GpuEffectRenderer> = listOf(FogEffect())

    private val ids = FogEffect.IDS

    override fun canRender(effect: Effect): Boolean = effect.layer == Layer.Gpu && effect.id in ids
}
