package com.cdodi

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.window.ComposeViewport
import com.cdodi.buses.AppEventBusImpl
import com.cdodi.buses.AppLifecycleBusImp
import com.cdodi.buses.LocalAppEventBus
import com.cdodi.buses.LocalLifeCycleBus
import com.cdodi.buses.LocalTimeBus
import com.cdodi.buses.TimeBusImpl
import com.cdodi.components.*
import com.cdodi.pages.AboutPage
import com.cdodi.pages.BoidsPage
import com.cdodi.pages.GameOfLifePage
import com.cdodi.pages.SmallScreenPage
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

enum class Page {
    Home,
    About,
    Boids,
    GameOfLife,
}

private const val ONE_SECOND_IN_NANO = 1_000_000_000f

fun main() {
    val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("Application Scope"))
    val timeBus = TimeBusImpl(appScope)
    val eventBus = AppEventBusImpl(appScope)
    val lifeCycleBus = AppLifecycleBusImp(appScope)

    ComposeViewport(document.body!!) {
        heartBeat(timeBus)

        CompositionLocalProvider(
            LocalTimeBus provides timeBus,
            LocalAppEventBus provides eventBus,
            LocalLifeCycleBus provides lifeCycleBus
        ) {
            App()
        }
    }
}

@Composable
private fun heartBeat(timeBus: TimeBusImpl) {
    LaunchedEffect(timeBus) {
        var lastTime = 0L
        while (isActive) {
            withFrameNanos { currentTime ->
                if (lastTime != 0L) {
                    val deltaTime = (currentTime - lastTime) / ONE_SECOND_IN_NANO
                    timeBus.onFrame(deltaTime.fastCoerceAtMost(0.1f))
                }

                lastTime = currentTime
            }
        }
    }
}

@Composable
private fun App() {
    val runtimeShader by rememberShader("bokeh")

    MaterialTheme {
        LookaheadScope {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .backgroundShader(runtimeShader)
            ) {
                if (LocalIsSmallWindow.current) {
                    SmallScreenPage()
                } else {
                    AppContent()
                }
            }
        }
    }
}

@Composable
private fun LookaheadScope.AppContent() {
    var currentPage by remember { mutableStateOf(Page.Home) }

    val pixelMeltEffect = remember { RuntimeEffect.makeForShader(PIXEL_MELT_SHADER) }
    val transition: Transition<Page> = updateTransition(
        targetState = currentPage,
        label = "ScreenRouter"
    )

    val cardModifier = Modifier.size(15.vw)
    val homeButton = movableCard(
        text = "Home",
        onClick = { currentPage = Page.Home }
    )
    val aboutButton = movableCard(
        text = "About",
        onClick = { currentPage = Page.About }
    )
    val contactsButton = movableCard(
        text = "Boids",
        onClick = { currentPage = Page.Boids }
    )
    val sketchButton = movableCard(
        text = "Game Of Life",
        onClick = { currentPage = Page.GameOfLife }
    )
    val bodyCard = movableBodyCard()

    Box(contentAlignment = Alignment.Center) {
        if (currentPage == Page.Home) {
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
                    contactsButton(cardModifier.align(Alignment.BottomStart), MorphingShape.TriangleBottomStart)
                    sketchButton(cardModifier.align(Alignment.BottomEnd), MorphingShape.TriangleBottomEnd)
                },
            )
        } else {
            TopBarForm {
                homeButton(topBarModifier, MorphingShape.Rectangle)
                aboutButton(topBarModifier, MorphingShape.Rectangle)
                contactsButton(topBarModifier, MorphingShape.Rectangle)
                sketchButton(topBarModifier, MorphingShape.Rectangle)
            }

            bodyCard(
                Modifier.fillMaxWidth().height(80f.vh).align(Alignment.BottomCenter),
                MorphingShape.Rectangle,
                true
            ) {
                transition.AnimatedContent(
                    transitionSpec = {
                        fadeIn(tween(durationMillis = 2000)) togetherWith fadeOut(tween(durationMillis = 2000))
                    },
                    contentKey = { it }
                ) { targetScreen ->
                    val meltProgress by transition.animateFloat(
                        transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                        label = "Melting progress"
                    ) { state ->
                        if (state == targetScreen) 0f else 1f
                    }

                    Box(
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer {
                                val isGoingForward = transition.targetState.ordinal > targetScreen.ordinal
                                val dirX = if (isGoingForward) -1.0f else 1.0f
                                val builder = RuntimeShaderBuilder(pixelMeltEffect).apply {
                                    uniform("resolution", size.width, size.height)
                                    uniform("progress", meltProgress)
                                    uniform("direction", dirX, 0.2f)
                                }
                                val skiaImageFilter = ImageFilter.makeRuntimeShader(
                                    runtimeShaderBuilder = builder,
                                    shaderName = "composable",
                                    input = null
                                )

                                renderEffect = skiaImageFilter.asComposeRenderEffect()
                            }
                    ) {
                        // Render your actual pages inside the shaded Box
                        when(targetScreen) {
                            Page.About -> AboutPage()
                            Page.Boids -> BoidsPage()
                            Page.GameOfLife -> GameOfLifePage()
                            Page.Home -> Unit
                        }
                    }
                }

//                AnimatedContent(
//                    targetState = currentScreen,
//                    contentKey = { it },
//                    transitionSpec = {
//                        if (targetState.ordinal > initialState.ordinal) {
//                            slideInHorizontally(tween(400)) { width -> width } togetherWith
//                                    slideOutHorizontally(tween(400)) { width -> -width }
//                        } else {
//                            slideInHorizontally(tween(400)) { width -> -width } togetherWith
//                                    slideOutHorizontally(tween(400)) { width -> width }
//                        }
//                    }
//                ) {
//                    when(currentScreen) {
//                        Screen.About -> AboutPage()
//                        Screen.Boids -> BoidsPage()
//                        Screen.GameOfLife -> GameOfLifePage()
//                        Screen.Home -> Unit
//                    }
//                }
            }
        }
    }
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
