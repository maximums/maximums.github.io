package com.cdodi.features.background

import kotlin.math.floor
import kotlin.math.pow

/**
 * The lights of the street behind the glass, placed as the design canvas's background study places them ("Rain", in
 * DESIGN.md's canvas), by the same seeded generator, in its 1440 × 900 space:
 *
 * - street lamps in Lamp amber, lining both sides towards the middle; the nearer, the higher, bigger and softer;
 * - tail lights in Ember, low on the right; head lights in Parchment, low on the left (both reflected in the wet road);
 * - a few cold distant windows in Rain, anywhere along the middle band.
 *
 * [values] packs them for `street.wgsl`'s `array<Light>`.
 */
object StreetLights {

    /** A light centred at [x], [y], [size] across, its rim at [alpha] (the middle at 55 % of that), [blur] soft. */
    class Light(
        val x: Float,
        val y: Float,
        val size: Float,
        val rgb: Int,
        val alpha: Float,
        val blur: Float,
        /** Where in its slow breath (7 s each way) it starts, in seconds. */
        val delay: Float,
        val reflected: Boolean,
    )

    val all: List<Light> = generate()

    /** struct Light { place: vec4f (x, y, size, blur), colour: vec4f (rgb, alpha), extra: vec4f (delay, reflected) }. */
    val values: FloatArray = FloatArray(all.size * FLOATS).also { out ->
        all.forEachIndexed { i, light ->
            val at = i * FLOATS
            out[at] = light.x
            out[at + 1] = light.y
            out[at + 2] = light.size
            out[at + 3] = light.blur
            out[at + 4] = (light.rgb shr 16 and 0xFF) / 255f
            out[at + 5] = (light.rgb shr 8 and 0xFF) / 255f
            out[at + 6] = (light.rgb and 0xFF) / 255f
            out[at + 7] = light.alpha
            out[at + 8] = light.delay
            out[at + 9] = if (light.reflected) 1f else 0f
        }
    }

    const val FLOATS = 12

    private const val LAMP = 0xD8A15A
    private const val EMBER = 0xB4532E
    private const val PARCHMENT = 0xEDE6D6
    private const val RAIN = 0x7E97A3

    private fun generate(): List<Light> {
        // The study's generator: Park–Miller's minimal standard, seeded 4201. The calls happen in the study's order
        // (JavaScript evaluates arguments left to right), so the street is the one on the canvas.
        var seed = 4201L
        fun next(): Double {
            seed = seed * 16807 % 2147483647
            return (seed - 1) / 2147483646.0
        }
        // Math.round, which rounds halves up (Kotlin's round() rounds them to even).
        fun jsRound(value: Double) = floor(value + 0.5)

        val lights = mutableListOf<Light>()
        val cx = 720.0
        val cy = 470.0
        fun push(x: Double, y: Double, size: Double, rgb: Int, alpha: Double, blur: Double, reflected: Boolean = false) {
            val s = jsRound(size)
            val left = jsRound(x - size / 2)
            val top = jsRound(y - size / 2)
            lights += Light(
                x = (left + s / 2).toFloat(),
                y = (top + s / 2).toFloat(),
                size = s.toFloat(),
                rgb = rgb,
                alpha = alpha.toFloat(),
                blur = jsRound(blur).toFloat(),
                delay = (next() * 7).toFloat(),
                reflected = reflected,
            )
        }

        repeat(16) { i ->
            val side = if (i % 2 == 0) -1 else 1
            val t = next().pow(0.9)
            val x = cx + side * (30 + t * 780)
            val y = cy - 40 - t * 360 + (next() - 0.5) * 20
            push(x, y, 8 + t * 150, LAMP, 0.18 + (1 - t) * 0.3, 1 + t * 9)
        }
        repeat(10) {
            val t = next().pow(0.8)
            push(cx + 20 + t * 440, cy + 10 + t * 240, 6 + t * 96, EMBER, 0.25 + (1 - t) * 0.35, 1 + t * 8, reflected = true)
        }
        repeat(8) {
            val t = next().pow(0.8)
            push(cx - 20 - t * 420, cy + 10 + t * 250, 6 + t * 110, PARCHMENT, 0.16 + (1 - t) * 0.26, 1 + t * 10, reflected = true)
        }
        repeat(8) {
            val x = next() * 1440
            val y = 260 + next() * 420
            val size = 10 + next() * 50
            val alpha = 0.08 + next() * 0.14
            val blur = 3 + next() * 6
            push(x, y, size, RAIN, alpha, blur)
        }
        return lights
    }
}
