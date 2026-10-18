package com.cdodi

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import com.cdodi.adapters.compose.HeartbeatDriver
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.LocalGpuWidgets
import com.cdodi.adapters.compose.LocalNavigationBus
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.compose.NavPages
import com.cdodi.adapters.compose.effects.LocalMorph
import com.cdodi.adapters.compose.effects.Morph
import com.cdodi.adapters.compose.effects.MorphSource
import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.adapters.compose.effects.morph
import com.cdodi.adapters.input.InputLayer
import com.cdodi.components.*
import com.cdodi.core.bus.NavIntent
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.features.about.AboutDestination
import com.cdodi.features.background.QualityToggle
import com.cdodi.features.background.StillScene
import com.cdodi.features.boids.BoidsControls
import com.cdodi.features.boids.BoidsController
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.home.HomeDestination
import com.cdodi.features.life.LifeControls
import com.cdodi.features.life.LifeController
import com.cdodi.features.life.LifeDestination
import com.cdodi.features.notes.NotesDestination
import com.cdodi.features.notes.NotesPage
import com.cdodi.pages.AboutPage
import com.cdodi.pages.BoidsPage
import com.cdodi.pages.GameOfLifePage
import com.cdodi.shell.AppRoot
import com.cdodi.shell.AppRuntime
import com.cdodi.shell.GpuStatus
import com.cdodi.shell.LocalMenuLayout
import com.cdodi.shell.MenuLayout
import com.cdodi.shell.bootstrap
import com.cdodi.shell.rememberPageEffects

fun main() {
    val runtime = bootstrap()

    ComposeViewport("compose") {
        HeartbeatDriver(runtime.heartbeat)

        CompositionLocalProvider(
            LocalHeartbeat provides runtime.heartbeat,
            LocalNavigationBus provides runtime.navigation,
        ) {
            AppRoot {
                InputLayer(runtime.input, runtime.heartbeat)
                CompositionLocalProvider(LocalGpuWidgets provides runtime.widgets) {
                    App(runtime)
                }
            }
        }
    }
}

@Composable
private fun App(runtime: AppRuntime) {
    val gpu = runtime.gpu.collectAsState().value
    val layout = LocalMenuLayout.current
    MaterialTheme {
        LookaheadScope {
            // A Box, not a Material Surface: a Surface blocks pointer events from reaching what's behind it, and behind
            // it is the input layer that hands them to the scene. Like a Surface, it passes its size down as the
            // minimum: the menu is placed across the whole screen, and clicks only reach it inside its parents' bounds.
            if (gpu is GpuStatus.Unavailable) StillScene()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(layout.margin),
                propagateMinConstraints = true,
            ) {
                AppContent(runtime, gpu, layout)
            }
            if (gpu is GpuStatus.Unavailable) FallbackNotice()
        }
    }
}

@Composable
private fun FallbackNotice() {
    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = "The rain can't reach this browser: without WebGPU, this is a simpler copy of the scene.",
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            color = Color(0xFF8C9499), // DESIGN.md: Fog
        )
    }
}

@Composable
private fun LookaheadScope.AppContent(runtime: AppRuntime, gpu: GpuStatus, layout: MenuLayout) {
    val navigation = LocalNavigationBus.current
    val navState = navigation.state.collectEachFrame()
    // The layout switches sides when the menu morph starts: at the click going to a page, but only after the page has
    // faded going back to Home. Until then it stays on the side being left.
    val layoutAt by remember(navState) { derivedStateOf { navState.value.layoutAt() } }
    val morph = remember(navState) { MorphSource { navState.value.morph(UiEffectIds.MENU_MORPH) } }
    val paneFill = remember(navState, gpu) { { navState.value.paneFill(scenesRun = gpu is GpuStatus.Running) } }
    val pageEffects = rememberPageEffects()

    fun open(destination: Destination): () -> Unit = { navigation.send(NavIntent.NavigateTo(destination)) }

    val atHome = layoutAt == HomeDestination
    val homeButton = movableCard("Home", layout.label, open(HomeDestination))
    val aboutButton = movableCard("About", layout.label, open(AboutDestination))
    val notesButton = movableCard("Notes", layout.label, open(NotesDestination()))
    val boidsButton = movableCard("Boids", layout.label, open(BoidsDestination))
    // A phone's top row has no room for the full name.
    val lifeButton = movableCard(if (layout.compact && !atHome) "Life" else "Game Of Life", layout.label, open(LifeDestination()))
    val bodyCard = movableBodyCard()

    CompositionLocalProvider(LocalMorph provides morph, LocalPaneFill provides paneFill) {
    Box(contentAlignment = Alignment.Center) {
        if (gpu is GpuStatus.Running && (layoutAt == HomeDestination || layoutAt == AboutDestination)) {
            QualityToggle(
                runtime.background,
                runtime.gpuTimings,
                Modifier.align(Alignment.BottomEnd).offset(y = 20.dp),
            )
        }
        if (atHome) {
            val pane = Modifier.size(layout.pane)
            MainMenu(
                size = layout.window,
                body = {
                    bodyCard(Modifier.size(layout.rhombus).align(Alignment.Center), MorphingShape.Rhombus, false) {
                        Text(
                            text = "Welcome",
                            fontSize = layout.welcome, color = Color(0xa0_5a_d6_ff),
                        )
                    }
                },
                menuContent = {
                    aboutButton(pane.align(Alignment.TopStart), MorphingShape.TriangleTopStart)
                    notesButton(pane.align(Alignment.TopEnd), MorphingShape.TriangleTopEnd)
                    boidsButton(pane.align(Alignment.BottomStart), MorphingShape.TriangleBottomStart)
                    lifeButton(pane.align(Alignment.BottomEnd), MorphingShape.TriangleBottomEnd)
                },
            )
        } else {
            val pane = Modifier.size(layout.bar)
            TopBarForm(layout.barGap) {
                // Home has no pane on Home, so it has nothing to morph from: it fades in with the morph instead.
                homeButton(pane.graphicsLayer { alpha = (morph.current() as? Morph.Moving)?.fraction ?: 1f }, MorphingShape.Rectangle)
                aboutButton(pane, MorphingShape.Rectangle)
                notesButton(pane, MorphingShape.Rectangle)
                boidsButton(pane, MorphingShape.Rectangle)
                lifeButton(pane, MorphingShape.Rectangle)
            }

            bodyCard(
                Modifier.fillMaxSize().padding(top = layout.pageTop),
                MorphingShape.Rectangle,
                true
            ) {
                NavPages(navState, pageEffects, Modifier.fillMaxSize()) { destination ->
                    when (destination) {
                        AboutDestination -> AboutPage()
                        is NotesDestination -> NotesPage(destination.slug) { navigation.send(NavIntent.NavigateTo(it)) }
                        BoidsDestination -> if (gpu is GpuStatus.Running) BoidsControls(runtime.boids, runtime.gpuTimings) else BoidsPage()
                        // The grid is the GPU scene underneath; without WebGPU, the CPU page draws it.
                        is LifeDestination -> if (gpu is GpuStatus.Running) {
                            LifeControls(runtime.life, destination.rule, runtime.gpuTimings) { rule -> navigation.send(NavIntent.NavigateTo(LifeDestination(rule))) }
                        } else {
                            GameOfLifePage(destination.rule)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
    }
}

/**
 * The glass over a page: cleared where what the page shows is drawn by the scene behind it (Life, Boids, the About
 * sphere), following the page fade in between.
 */
private fun NavState.paneFill(scenesRun: Boolean): Color {
    fun fillOf(destination: Destination) =
        if (scenesRun && (destination is LifeDestination || destination == BoidsDestination || destination == AboutDestination)) ClearedPane else FoggedPane
    return when (this) {
        is NavState.Idle -> fillOf(at)
        is NavState.Transitioning -> lerp(fillOf(from), fillOf(to), effects.firstOrNull { it.effect.id == UiEffectIds.FADE }?.travelled ?: 0f)
    }
}

/** Which side of the menu morph the layout is on. */
private fun NavState.layoutAt(): Destination = when (this) {
    is NavState.Idle -> at
    is NavState.Transitioning -> if (effects.any { it.effect.id == UiEffectIds.MENU_MORPH }) to else from
}

@Composable
private fun BoxScope.TopBarForm(gap: Dp, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.align(Alignment.TopCenter),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        content()
    }
}
