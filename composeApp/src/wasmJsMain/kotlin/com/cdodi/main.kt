package com.cdodi

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.cdodi.adapters.compose.HeartbeatDriver
import com.cdodi.adapters.compose.LocalGpuWidgets
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.LocalNavigationBus
import com.cdodi.adapters.compose.NavPages
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.compose.effects.LocalMorph
import com.cdodi.adapters.compose.effects.MorphSource
import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.adapters.compose.effects.morph
import com.cdodi.adapters.input.InputLayer
import com.cdodi.components.LocalPaneFill
import com.cdodi.components.MorphingShape
import com.cdodi.components.movableBodyCard
import com.cdodi.core.bus.NavIntent
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.features.about.AboutContent
import com.cdodi.features.about.AboutDestination
import com.cdodi.features.background.FallbackPane
import com.cdodi.features.background.QualityToggle
import com.cdodi.features.background.StillScene
import com.cdodi.features.boids.BoidsControls
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.home.HomeDestination
import com.cdodi.features.life.LifeControls
import com.cdodi.features.life.LifeDestination
import com.cdodi.features.notes.NotesDestination
import com.cdodi.features.notes.NotesPage
import com.cdodi.pages.AboutPage
import com.cdodi.pages.BoidsPage
import com.cdodi.pages.GameOfLifePage
import com.cdodi.shell.AppRoot
import com.cdodi.shell.AppRuntime
import com.cdodi.shell.Colophon
import com.cdodi.shell.CornerHint
import com.cdodi.shell.GpuStatus
import com.cdodi.shell.HomeWindow
import com.cdodi.shell.LocalMenuLayout
import com.cdodi.shell.MenuItem
import com.cdodi.shell.MenuLayout
import com.cdodi.shell.TopBar
import com.cdodi.shell.WelcomeWords
import com.cdodi.shell.bootstrap
import com.cdodi.shell.rememberMenuLabel
import com.cdodi.shell.rememberPageEffects
import com.cdodi.ui.theme.AutumnColors

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

/** The Welcome rhombus's fog (the Home mockup): a little thicker than a fog pane. */
private val WelcomeFog = Color(16, 20, 24).copy(alpha = 0.62f)

@Composable
private fun App(runtime: AppRuntime) {
    val gpu = runtime.gpu.collectAsState().value
    val layout = LocalMenuLayout.current
    LookaheadScope {
        if (gpu is GpuStatus.Unavailable) StillScene()
        AppContent(runtime, gpu, layout)
    }
}

@Composable
private fun LookaheadScope.AppContent(runtime: AppRuntime, gpu: GpuStatus, layout: MenuLayout) {
    val navigation = LocalNavigationBus.current
    val navState = navigation.state.collectEachFrame()
    // The layout switches sides when the menu morph starts: at the click going to a page, but only after the page has
    // faded going back to Home. Until then it stays on the side being left.
    val layoutAt by remember(navState) { derivedStateOf { navState.value.layoutAt() } }
    val target by remember(navState) { derivedStateOf { navState.value.target() } }
    val homeInvolved by remember(navState) { derivedStateOf { navState.value.involves(HomeDestination) } }
    val morph = remember(navState) { MorphSource { navState.value.morph(UiEffectIds.MENU_MORPH) } }
    // How much of Home is on the glass: its window dissolves as the top bar condenses, and back.
    val homeness = remember(navState) { { navState.value.homeness() } }
    val pageFog = if (gpu is GpuStatus.Running) AutumnColors.ClearedPane else AutumnColors.FogPane
    val paneFill = remember(homeness, pageFog) { { lerp(pageFog, WelcomeFog, homeness()) } }
    val pageEffects = rememberPageEffects()

    fun open(destination: Destination) = navigation.send(NavIntent.NavigateTo(destination))

    val atHome = layoutAt == HomeDestination
    val interactions = remember { MenuItem.entries.associateWith { MutableInteractionSource() } }
    val labels = MenuItem.entries.associateWith { item -> rememberMenuLabel(item, interactions.getValue(item)) { open(item.destination()) } }
    val bodyCard = movableBodyCard()

    CompositionLocalProvider(LocalMorph provides morph, LocalPaneFill provides paneFill) {
        Box(Modifier.fillMaxSize()) {
            if (atHome && gpu is GpuStatus.Unavailable) {
                // DESIGN.md §8, No WebGPU: one pane over the still scene instead of the window.
                FallbackPane(
                    onPlayLife = { open(LifeDestination()) },
                    onAbout = { open(AboutDestination) },
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = layout.margin),
                )
            } else if (atHome || homeInvolved) {
                HomeWindow(
                    layout = layout,
                    interactions = interactions,
                    labels = if (atHome) { item, modifier -> labels.getValue(item)(modifier, true, false) } else null,
                    onOpen = { open(it.destination()) },
                    alpha = homeness,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    if (atHome) {
                        bodyCard(Modifier.size(layout.rhombus).align(Alignment.Center), MorphingShape.Rhombus, false) {
                            WelcomeWords(layout, AboutContent.NAME)
                        }
                    }
                }
            }

            if (atHome) {
                if (gpu !is GpuStatus.Unavailable) {
                    CornerHint("Choose a pane", Modifier.align(Alignment.Center).offset(y = layout.window / 2 + 60.dp))
                }
                if (!layout.compact && gpu !is GpuStatus.Unavailable) Colophon(Modifier.align(Alignment.BottomStart).padding(start = layout.margin, bottom = 36.dp))
            } else {
                bodyCard(Modifier.fillMaxSize().padding(top = layout.pageTop), MorphingShape.Rectangle, true) {
                    NavPages(navState, pageEffects, Modifier.fillMaxSize()) { destination ->
                        when (destination) {
                            AboutDestination -> AboutPage()
                            is NotesDestination -> NotesPage(destination.slug) { open(it) }
                            BoidsDestination -> if (gpu is GpuStatus.Running) BoidsControls(runtime.boids, runtime.gpuTimings) else BoidsPage()
                            // The grid is the GPU scene underneath; without WebGPU, the CPU page draws it.
                            is LifeDestination -> if (gpu is GpuStatus.Running) {
                                LifeControls(runtime.life, destination.rule, runtime.gpuTimings) { rule -> open(LifeDestination(rule)) }
                            } else {
                                GameOfLifePage(destination.rule)
                            }
                            else -> Unit
                        }
                    }
                }
                TopBar(layout, AboutContent.NAME, onHome = { open(HomeDestination) }, solid = { 1f - homeness() }) {
                    for (item in MenuItem.entries) labels.getValue(item)(Modifier, false, item.isAt(target))
                }
            }

            if (gpu is GpuStatus.Running && atHome) {
                QualityToggle(runtime.background, runtime.gpuTimings, Modifier.align(Alignment.BottomEnd).padding(end = layout.margin, bottom = if (layout.compact) 12.dp else 36.dp))
            }
        }
    }
}

private fun MenuItem.destination(): Destination = when (this) {
    MenuItem.About -> AboutDestination
    MenuItem.Life -> LifeDestination()
    MenuItem.Boids -> BoidsDestination
    MenuItem.Notes -> NotesDestination()
}

private fun MenuItem.isAt(destination: Destination): Boolean = when (this) {
    MenuItem.About -> destination == AboutDestination
    MenuItem.Life -> destination is LifeDestination
    MenuItem.Boids -> destination == BoidsDestination
    MenuItem.Notes -> destination is NotesDestination
}

/** Which side of the menu morph the layout is on. */
private fun NavState.layoutAt(): Destination = when (this) {
    is NavState.Idle -> at
    is NavState.Transitioning -> if (effects.any { it.effect.id == UiEffectIds.MENU_MORPH }) to else from
}

/** Where navigation is going: the top bar's thread moves at the click. */
private fun NavState.target(): Destination = when (this) {
    is NavState.Idle -> at
    is NavState.Transitioning -> to
}

private fun NavState.involves(destination: Destination): Boolean =
    this is NavState.Transitioning && (from == destination || to == destination)

/** 1 on Home, 0 elsewhere, and in between while the menu morphs (DESIGN.md §9: the window dissolves, the bar condenses). */
private fun NavState.homeness(): Float = when (this) {
    is NavState.Idle -> if (at == HomeDestination) 1f else 0f
    is NavState.Transitioning -> {
        val travelled = effects.firstOrNull { it.effect.id == UiEffectIds.MENU_MORPH }?.travelled
        when {
            from == HomeDestination && to != HomeDestination -> 1f - (travelled ?: 0f)
            to == HomeDestination && from != HomeDestination -> travelled ?: 0f
            else -> if (from == HomeDestination) 1f else 0f
        }
    }
}
