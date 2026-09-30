package com.cdodi.shell

import com.cdodi.adapters.compose.effects.UiEffects
import com.cdodi.core.navigation.DestinationCodec
import com.cdodi.core.navigation.graph.requireRenderable
import com.cdodi.core.navigation.transition.reversed
import com.cdodi.data.gameoflife.LifeRule
import com.cdodi.features.about.AboutDestination
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.home.HomeDestination
import com.cdodi.features.life.LifeDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppGraphTest {

    private val graph = appGraph()

    @Test
    fun theSiteGraphIsValidAndEveryEffectIsRendered() {
        graph.requireRenderable(UiEffects)
    }

    @Test
    fun theMenuOpensPagesAndPagesChangeWithTheMelt() {
        assertEquals(Transitions.openPage, graph.edgeFor(HomeDestination, AboutDestination)?.transition)
        assertEquals(Transitions.openPage.reversed(), graph.edgeFor(LifeDestination, HomeDestination)?.transition)
        assertEquals(Transitions.pageToPage, graph.edgeFor(AboutDestination, BoidsDestination)?.transition)
    }

    @Test
    fun lifeKeepsItsRuleInTheUrl() {
        val codec = DestinationCodec(graph)

        assertEquals("life?rule=B36/S23", codec.encode(LifeDestination(LifeRule.HighLife)))
        assertEquals(LifeDestination(LifeRule.HighLife), codec.decode("life?rule=b36/s23"))
        assertNull(codec.decode("life?rule=B9/S"))
    }
}
