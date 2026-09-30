package com.cdodi

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import com.cdodi.adapters.compose.HeartbeatDriver
import com.cdodi.adapters.compose.LocalHeartbeat
import com.cdodi.adapters.compose.LocalNavigationBus
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.adapters.input.InputLayer
import com.cdodi.components.*
import com.cdodi.core.bus.NavIntent
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.effect.EffectState
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
import com.cdodi.shell.menuOrder
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

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
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (gpu is GpuStatus.Unavailable) Modifier.fallbackBackground() else Modifier)
                    .padding(24.dp)
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
    // The layout follows where navigation is heading, so the menu starts morphing on the click.
    val target by remember(navState) { derivedStateOf { navState.value.target } }

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

    Box(contentAlignment = Alignment.Center) {
        if (target == HomeDestination) {
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
                PageLayers(navState)
            }
        }
    }
}

private val NavState.target: Destination
    get() = when (this) {
        is NavState.Idle -> at
        is NavState.Transitioning -> to
    }

/** How one page looks this frame: its opacity, and how far it has melted away (and in which direction). */
private data class PageLook(val alpha: Float, val melt: Float = 0f, val meltDirection: Float = 0f)

/**
 * The page on screen, or both pages during a transition, as the transition's effects say: the page being entered
 * fades in, the one being left fades out and melts. Each page is keyed by its destination, so it keeps its state
 * (a running Game of Life) as it goes from being entered to being shown.
 */
@Composable
private fun PageLayers(navState: State<NavState>) {
    val pixelMeltEffect = remember { RuntimeEffect.makeForShader(PIXEL_MELT_SHADER) }

    Box(Modifier.fillMaxSize()) {
        for ((destination, look) in navState.value.pageLooks()) {
            key(destination) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer {
                            alpha = look.alpha
                            if (look.melt > 0f) {
                                val builder = RuntimeShaderBuilder(pixelMeltEffect).apply {
                                    uniform("resolution", size.width, size.height)
                                    uniform("progress", look.melt)
                                    uniform("direction", look.meltDirection, 0.2f)
                                }
                                val skiaImageFilter = ImageFilter.makeRuntimeShader(
                                    runtimeShaderBuilder = builder,
                                    shaderName = "composable",
                                    input = null
                                )

                                renderEffect = skiaImageFilter.asComposeRenderEffect()
                            }
                        }
                ) {
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

private fun NavState.pageLooks(): List<Pair<Destination, PageLook>> = when (this) {
    is NavState.Idle -> listOf(at to PageLook(alpha = 1f))
    is NavState.Transitioning -> {
        // Until its fade starts, the page being entered waits unseen (under the fog, for instance).
        val fade = effects.travelled(UiEffectIds.FADE) ?: 0f
        val melt = effects.travelled(UiEffectIds.PIXEL_MELT) ?: 0f
        // The page melts towards the left when the next one comes later in the menu.
        val direction = if (menuOrder.indexOf(to.route) > menuOrder.indexOf(from.route)) -1f else 1f
        listOf(
            from to PageLook(alpha = 1f - fade, melt = melt, meltDirection = direction),
            to to PageLook(alpha = fade),
        )
    }
}

private fun List<EffectState>.travelled(id: String): Float? = firstOrNull { it.effect.id == id }?.travelled

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
