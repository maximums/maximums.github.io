package com.cdodi.shell

import com.cdodi.adapters.compose.effects.UiEffects
import com.cdodi.adapters.gpu.effects.GpuEffects
import com.cdodi.core.navigation.effect.EffectRegistry
import com.cdodi.core.navigation.graph.Interrupt
import com.cdodi.core.navigation.graph.NavGraph
import com.cdodi.core.navigation.graph.navGraph
import com.cdodi.features.about.AboutDestination
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.home.HomeDestination
import com.cdodi.features.life.LifeDestination

/** The order of the menu, which also decides which way a page melts. */
val menuOrder = listOf(HomeDestination, AboutDestination, BoidsDestination, LifeDestination)

/**
 * Every effect the site can draw, on either layer. GPU effects count whether or not WebGPU turns out to be available,
 * so a misspelt id fails at startup rather than mid-transition.
 */
val siteEffects = EffectRegistry { UiEffects.canRender(it) || GpuEffects.canRender(it) }

/**
 * The site's navigation graph. Every edge replaces its transition when interrupted, so a click never waits for a
 * transition to finish.
 */
fun appGraph(): NavGraph = navGraph(start = HomeDestination) {
    menuOrder.forEach { destination(it) }

    edge(from = any, to = any, transition = Transitions.pageToPage, onInterrupt = Interrupt.Replace)
    edge(from = HomeDestination, to = any, transition = Transitions.openPage, onInterrupt = Interrupt.Replace).andBack()

    reducedMotion = Transitions.reducedMotion
}
