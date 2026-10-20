package com.cdodi.adapters.gpu

import androidx.compose.ui.graphics.Color
import blog.composeapp.generated.resources.Res
import com.cdodi.design.theme.Theme
import com.cdodi.webgpu.bindings.GPUBindGroup
import com.cdodi.webgpu.bindings.GPUBindGroupDescriptor
import com.cdodi.webgpu.bindings.GPUBindGroupEntry
import com.cdodi.webgpu.bindings.GPUBindGroupLayout
import com.cdodi.webgpu.bindings.GPUBufferBinding
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUColorDict
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.Float32Staging

/** How the theme is laid out for the shaders: `common/theme.wgsl`'s `struct Theme`, one `vec4f` per colour. */
object ThemeUniforms {
    /** How many colours the struct holds. */
    const val COLOURS = 19

    const val FLOATS = COLOURS * 4

    /** The struct's fields in order, each with its colours: one, or an array's. */
    fun fields(theme: Theme): List<Pair<String, List<Color>>> = with(theme) {
        listOf(
            "ground" to listOf(colors.ground),
            "accent" to listOf(colors.accent),
            "heat" to listOf(colors.heat),
            "cold" to listOf(colors.cold),
            "sky" to scene.sky,
            "lights" to scene.lights,
            "streetFog" to listOf(scene.streetFog),
            "fog" to listOf(scene.fog),
            "fallingNear" to listOf(scene.fallingNear),
            "fallingFar" to listOf(scene.fallingFar),
            "shade" to listOf(scene.shade),
            "ash" to listOf(scene.ash),
            "figure" to listOf(scene.figure),
        )
    }

    /** Writes [theme]'s colours into [out], as the struct has them. */
    fun pack(theme: Theme, out: FloatArray) {
        val colours = fields(theme).flatMap { it.second }
        check(colours.size == COLOURS) { "The theme struct holds $COLOURS colours, not ${colours.size}" }
        colours.forEachIndexed { i, colour ->
            out[i * 4] = colour.red
            out[i * 4 + 1] = colour.green
            out[i * 4 + 2] = colour.blue
            out[i * 4 + 3] = colour.alpha
        }
    }
}

/**
 * The theme's colours on the GPU: one uniform buffer, declared by `common/theme.wgsl` in [GROUP], that every themed
 * shader reads. It's written when the theme changes, which the scene host checks at the start of each frame
 * ([update]); a pipeline that draws in the theme binds it with [bindGroup].
 */
class GpuTheme private constructor(private val gpu: GpuContext, private val theme: () -> Theme, private val declaration: String) : AutoCloseable {

    private val buffer = gpu.device.createBuffer(
        GPUBufferDescriptor(size = ThemeUniforms.FLOATS * 4.0, usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST, label = "theme")
    )
    private val values = FloatArray(ThemeUniforms.FLOATS)
    private val staging = Float32Staging(values.size)
    private var written: Theme? = null

    /** The theme's ground: what a target shows before its scene is ready. */
    var ground: GPUColorDict = GPUColorDict(0.0, 0.0, 0.0, 1.0)
        private set

    init {
        update()
    }

    /** Writes the theme's colours if the theme has changed since they were last written. */
    fun update() {
        val current = theme()
        if (current === written) return
        ThemeUniforms.pack(current, values)
        gpu.queue.writeBuffer(buffer, 0.0, staging.fill(values))
        val g = current.colors.ground
        ground = GPUColorDict(g.red.toDouble(), g.green.toDouble(), g.blue.toDouble(), 1.0)
        written = current
    }

    /** [code], with the theme declared before it: for a shader that draws in the theme. */
    fun shader(code: String): String = declaration + "\n" + code

    /** The theme's bind group for a pipeline's [layout] of [GROUP]. */
    fun bindGroup(layout: GPUBindGroupLayout): GPUBindGroup = gpu.device.createBindGroup(
        GPUBindGroupDescriptor(layout = layout, entries = listOf(GPUBindGroupEntry(0, GPUBufferBinding(buffer))), label = "theme")
    )

    override fun close() {
        buffer.destroy()
    }

    companion object {
        /** The bind group the theme is declared in; a shader's own bindings are in group 0. */
        const val GROUP = 1

        suspend fun create(gpu: GpuContext, theme: () -> Theme): GpuTheme =
            GpuTheme(gpu, theme, Res.readBytes("files/shaders/common/theme.wgsl").decodeToString())
    }
}
