package com.cdodi.features.background

import com.cdodi.design.theme.AutumnTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class StreetLightsTest {

    private val scene = AutumnTheme.scene

    @Test
    fun eachKindIsItsSlotInTheThemesLights() {
        // street.wgsl reads theme.lights[kind]: the kinds must be in the palette's order.
        assertEquals(scene.lights, StreetLights.Kind.entries.map { it.colourIn(scene) })
        assertEquals(listOf(scene.lamps, scene.tailLights, scene.headLights, scene.windows), scene.lights)
    }

    @Test
    fun theStreetHasEveryKindAndPacksIt() {
        assertEquals(StreetLights.Kind.entries.toSet(), StreetLights.all.map { it.kind }.toSet())
        StreetLights.all.forEachIndexed { i, light ->
            assertEquals(light.kind.ordinal.toFloat(), StreetLights.values[i * StreetLights.FLOATS + 4], "light $i's kind")
            assertEquals(light.alpha, StreetLights.values[i * StreetLights.FLOATS + 7], "light $i's alpha")
        }
    }
}
