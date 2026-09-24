package com.cdodi.core.navigation.effect

/**
 * Where an effect is drawn. Compose content can only be transformed by Skia (the UI layer), and the WebGPU scene only
 * by WebGPU; the two canvases cannot share textures, so every effect names its layer.
 */
enum class Layer { Ui, Gpu }

/**
 * A description of something to play during a transition. The model never draws it: adapters register a renderer per
 * effect id and layer, and graph validation checks that one exists. Open, so new kinds of effect need no model change.
 */
interface Effect {
    val id: String
    val layer: Layer
}

/** A property animation (fade, morph, condense, ...). */
data class AnimatedEffect(
    override val id: String,
    override val layer: Layer = Layer.Ui,
    val params: Map<String, String> = emptyMap(),
) : Effect

/** A shader driven by the transition's progress (fog, melt, dissolve, ...). */
data class ShaderEffect(
    override val id: String,
    override val layer: Layer = Layer.Gpu,
    val uniforms: Map<String, Float> = emptyMap(),
) : Effect

/**
 * What one effect should show this frame.
 *
 * @param progress this effect's own segment progress in `[0, 1]`, eased and already mirrored for reversed playback;
 * null while the segment waits for a condition (the effect should loop, e.g. breathing fog).
 * @param elapsed seconds since the segment started, for looping effects.
 */
data class EffectState(val effect: Effect, val progress: Float?, val elapsed: Double)

/** Implemented by adapters; used by graph validation to check every effect can be drawn. */
fun interface EffectRegistry {
    fun canRender(effect: Effect): Boolean
}
