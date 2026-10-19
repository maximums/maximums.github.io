package com.cdodi.shell

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.LocalNavigationBus
import com.cdodi.adapters.compose.NavPages
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.compose.effects.LocalMorph
import com.cdodi.adapters.compose.effects.MorphSource
import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.adapters.compose.effects.morph
import com.cdodi.adapters.compose.morph.MorphingShape
import com.cdodi.core.bus.NavIntent
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.core.time.FramePhase
import com.cdodi.features.about.AboutContent
import com.cdodi.features.about.AboutDestination
import com.cdodi.features.about.AboutPage
import com.cdodi.features.background.FallbackPane
import com.cdodi.features.background.QualityToggle
import com.cdodi.features.background.SoundToggle
import com.cdodi.features.background.StillScene
import com.cdodi.features.boids.BoidsControls
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.boids.BoidsPage
import com.cdodi.features.home.HomeDestination
import com.cdodi.features.life.GameOfLifePage
import com.cdodi.features.life.LifeControls
import com.cdodi.features.life.LifeDestination
import com.cdodi.features.notes.NotesDestination
import com.cdodi.features.notes.NotesPage
import com.cdodi.ui.components.CornerHint
import com.cdodi.ui.components.drawThread
import com.cdodi.ui.components.glass
import com.cdodi.ui.theme.Autumn
import com.cdodi.ui.theme.AutumnColors

/** The Welcome rhombus's fog (the Home mockup): a little thicker than a fog pane. */
private val WelcomeFog = Color(16, 20, 24).copy(alpha = 0.62f)

@Composable
fun App(runtime: AppRuntime) {
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
    val paneEffects = remember(navState) { navState.paneEffects() }

    fun open(destination: Destination) = navigation.send(NavIntent.NavigateTo(destination))

    val atHome = layoutAt == HomeDestination
    val interactions = remember { MenuItem.entries.associateWith { MutableInteractionSource() } }
    val labels = MenuItem.entries.associateWith { item -> rememberMenuLabel(item, interactions.getValue(item)) { open(item.destination) } }
    val bodyCard = movableBodyCard()

    // The rain is heard through the glass: muffled behind Home's fogged window, clearer on the pages.
    val heartbeat = LocalHeartbeat.current
    DisposableEffect(runtime.sound, heartbeat) {
        val subscription = heartbeat.subscribe(FramePhase.Ui) { runtime.sound.setOpenness(1f - navState.value.homeness()) }
        onDispose(subscription::close)
    }

    CompositionLocalProvider(LocalMorph provides morph, LocalPaneFill provides paneFill, LocalPaneEffects provides paneEffects) {
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
                    onOpen = { open(it.destination) },
                    alpha = homeness,
                    modifier = Modifier.align(Alignment.Center).offset(y = layout.windowOffset),
                ) {
                    if (atHome) {
                        bodyCard(Modifier.size(layout.rhombus).align(Alignment.Center), MorphingShape.Rhombus, false) {
                            WelcomeWords(layout, AboutContent.NAME)
                        }
                    }
                }
            }

            if (atHome) {
                SoundToggle(runtime.sound, Modifier.align(Alignment.TopEnd).padding(top = layout.toggleTop, end = layout.margin))
                if (gpu !is GpuStatus.Unavailable) {
                    layout.hintDrop?.let { CornerHint("Choose a pane", Modifier.align(Alignment.Center).offset(y = it)) }
                    // A phone, or a phone on its side, has no room for it.
                    if (!layout.compact && !layout.short) Colophon(Modifier.align(Alignment.BottomStart).padding(start = layout.margin, bottom = layout.toggleBottom))
                }
            } else {
                bodyCard(Modifier.fillMaxSize().padding(top = layout.pageTop), MorphingShape.Rectangle, true) {
                    NavPages(navState, pageEffects, Modifier.fillMaxSize()) { destination ->
                        when (destination) {
                            AboutDestination -> AboutPage()
                            is NotesDestination -> NotesPage(destination.slug) { open(it) }
                            BoidsDestination -> ScenePage(gpu, fallback = { BoidsPage() }) { BoidsControls(runtime.boids, runtime.gpuTimings) }
                            // The grid is the GPU scene underneath; without WebGPU, the CPU page draws it.
                            is LifeDestination -> ScenePage(gpu, fallback = { GameOfLifePage(destination.rule) }) {
                                LifeControls(runtime.life, destination.rule, runtime.gpuTimings) { rule -> open(LifeDestination(rule)) }
                            }
                            else -> Unit
                        }
                    }
                }
                TopBar(
                    layout, AboutContent.NAME,
                    onHome = { open(HomeDestination) },
                    solid = { navState.value.barSolidity() },
                    end = { SoundToggle(runtime.sound, compact = layout.compact) },
                ) {
                    for (item in MenuItem.entries) labels.getValue(item)(Modifier, false, item.isAt(target))
                }
            }

            val caption by remember(navState) { derivedStateOf { captionFor(navState.value) } }
            // Derived, so the screen recomposes when the caption changes, not on every frame of the transition.
            caption?.let {
                // It fades in while the fog holds, and out as the page arrives.
                Caption(it, alpha = { navState.value.captionAlpha() }, modifier = Modifier.align(Alignment.Center))
            }

            if (gpu is GpuStatus.Running && atHome) {
                QualityToggle(runtime.background, runtime.gpuTimings, Modifier.align(Alignment.BottomEnd).padding(end = layout.margin, bottom = layout.toggleBottom))
            }
        }
    }
}

/**
 * A scene page: its [controls] over the GPU scene, or the [fallback] page in a browser without WebGPU. While the GPU
 * starts (or starts again after losing its device), nothing: the scene is on its way, and the fallback would flash.
 */
@Composable
private fun ScenePage(gpu: GpuStatus, fallback: @Composable () -> Unit, controls: @Composable () -> Unit) {
    when (gpu) {
        GpuStatus.Running -> controls()
        is GpuStatus.Unavailable -> fallback()
        GpuStatus.Starting -> Unit
    }
}

/**
 * A line of verse over the fog while the next scene gathers itself (DESIGN.md §9, `holdFog`), with a thread of light
 * under it. [alpha] is read while drawing.
 */
@Composable
fun Caption(text: String, alpha: () -> Float, modifier: Modifier = Modifier) {
    Column(
        modifier.graphicsLayer { this.alpha = alpha() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = Autumn.type.verse.copy(fontSize = 24.sp), color = AutumnColors.Parchment)
        Box(Modifier.width(56.dp).height(1.dp).drawBehind { drawThread(Offset(0f, 0.5f), Offset(size.width, 0.5f)) })
    }
}
