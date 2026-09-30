package com.cdodi

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import com.cdodi.adapters.compose.HeartbeatDriver
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.LocalNavigationBus
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.compose.NavPages
import com.cdodi.adapters.compose.effects.LocalMorph
import com.cdodi.adapters.compose.effects.MorphSource
import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.adapters.compose.effects.morph
import com.cdodi.adapters.input.InputLayer
import com.cdodi.components.*
import com.cdodi.core.bus.NavIntent
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.features.about.AboutDestination
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.home.HomeDestination
import com.cdodi.features.life.LifeDestination
import com.cdodi.pages.AboutPage
import com.cdodi.pages.BoidsPage
import com.cdodi.pages.GameOfLifePage
import com.cdodi.pages.SmallScreenPage
import com.cdodi.shell.AppRoot
import com.cdodi.shell.GpuStatus
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
                App(runtime.gpu.collectAsState().value)
            }
        }
    }
}

@Composable
private fun App(gpu: GpuStatus) {
    MaterialTheme {
        LookaheadScope {
            // A Box, not a Material Surface: a Surface blocks pointer events from reaching what's behind it, and behind
            // it is the input layer that hands them to the scene. Like a Surface, it passes its size down as the
            // minimum: the menu is placed across the whole screen, and clicks only reach it inside its parents' bounds.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (gpu is GpuStatus.Unavailable) Modifier.fallbackBackground() else Modifier)
                    .padding(24.dp),
                propagateMinConstraints = true,
            ) {
                if (LocalIsSmallWindow.current) {
                    SmallScreenPage()
                } else {
                    AppContent()
                }
            }
            if (gpu is GpuStatus.Unavailable) FallbackNotice()
        }
    }
}

/** Without WebGPU, Compose draws the old Skia version of the background. */
@Composable
private fun Modifier.fallbackBackground(): Modifier {
    val runtimeShader by rememberShader("bokeh")
    return backgroundShader(runtimeShader)
}

@Composable
private fun FallbackNotice() {
    Box(Modifier.fillMaxSize().padding(bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = "The rain can't reach this browser: without WebGPU, this is a simpler copy of the scene.",
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF8C9499), // DESIGN.md: Fog
        )
    }
}

@Composable
private fun LookaheadScope.AppContent() {
    val navigation = LocalNavigationBus.current
    val navState = navigation.state.collectEachFrame()
    // The layout switches sides when the menu morph starts: at the click going to a page, but only after the page has
    // faded going back to Home. Until then it stays on the side being left.
    val layoutAt by remember(navState) { derivedStateOf { navState.value.layoutAt() } }
    val morph = remember(navState) { MorphSource { navState.value.morph(UiEffectIds.MENU_MORPH) } }
    val pageEffects = rememberPageEffects()

    fun open(destination: Destination): () -> Unit = { navigation.send(NavIntent.NavigateTo(destination)) }

    val cardModifier = Modifier.size(15.vw)
    val homeButton = movableCard(
        text = "Home",
        onClick = open(HomeDestination)
    )
    val aboutButton = movableCard(
        text = "About",
        onClick = open(AboutDestination)
    )
    val boidsButton = movableCard(
        text = "Boids",
        onClick = open(BoidsDestination)
    )
    val lifeButton = movableCard(
        text = "Game Of Life",
        onClick = open(LifeDestination())
    )
    val bodyCard = movableBodyCard()

    CompositionLocalProvider(LocalMorph provides morph) {
    Box(contentAlignment = Alignment.Center) {
        if (layoutAt == HomeDestination) {
            MainMenu(
                body = {
                    bodyCard(Modifier.size(20.vw).align(Alignment.Center), MorphingShape.Rhombus, false) {
                        Text(
                            text = "Welcome",
                            fontSize = 30.sp, color = Color(0xa0_5a_d6_ff),
                        )
                    }
                },
                menuContent = {
                    homeButton(cardModifier.align(Alignment.TopStart), MorphingShape.TriangleTopStart)
                    aboutButton(cardModifier.align(Alignment.TopEnd), MorphingShape.TriangleTopEnd)
                    boidsButton(cardModifier.align(Alignment.BottomStart), MorphingShape.TriangleBottomStart)
                    lifeButton(cardModifier.align(Alignment.BottomEnd), MorphingShape.TriangleBottomEnd)
                },
            )
        } else {
            TopBarForm {
                homeButton(topBarModifier, MorphingShape.Rectangle)
                aboutButton(topBarModifier, MorphingShape.Rectangle)
                boidsButton(topBarModifier, MorphingShape.Rectangle)
                lifeButton(topBarModifier, MorphingShape.Rectangle)
            }

            bodyCard(
                Modifier.fillMaxWidth().height(80f.vh).align(Alignment.BottomCenter),
                MorphingShape.Rectangle,
                true
            ) {
                NavPages(navState, pageEffects, Modifier.fillMaxSize()) { destination ->
                    when (destination) {
                        AboutDestination -> AboutPage()
                        BoidsDestination -> BoidsPage()
                        is LifeDestination -> GameOfLifePage(destination.rule)
                        else -> Unit
                    }
                }
            }
        }
    }
    }
}

/** Which side of the menu morph the layout is on. */
private fun NavState.layoutAt(): Destination = when (this) {
    is NavState.Idle -> at
    is NavState.Transitioning -> if (effects.any { it.effect.id == UiEffectIds.MENU_MORPH }) to else from
}

@Composable
private fun BoxScope.TopBarForm(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.align(Alignment.TopCenter),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}
