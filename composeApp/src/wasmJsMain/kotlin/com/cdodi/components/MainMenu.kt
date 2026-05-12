package com.cdodi.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MovableContent
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.pages.MY_TRY
import com.cdodi.pages.ONE_SECOND_NANOS
import com.cdodi.vw
import kotlinx.coroutines.isActive
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

val topBarModifier = Modifier.size(300.dp, 100.dp)

@Composable
fun movableCard(
    text: String,
    onClick: () -> Unit,
): @Composable LookaheadScope.(Modifier, MorphingShape) -> Unit {

    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)

    return remember {
        movableContentWithReceiverOf { modifier, targetShape ->
            Box(
                contentAlignment = targetShape.contentAlignment,
                modifier = modifier
                    .animatePlacement(lookaheadScope = this)
                    .morphingShape(targetShape)
                    .background(color = Color(0xA0_00_00_00))
//                    .background(color = Color.White)
                    .clickable(onClick = currentOnClick)
                    .padding(8.dp)
            ) {
                Text(
                    modifier = Modifier.animatePlacement(this@movableContentWithReceiverOf),
                    text = text,
                    fontSize = 24.sp,
                    color = Color(0xa0_5a_d6_ff),
                )
            }
        }
    }
}

@Composable
fun movableBodyCard(): @Composable LookaheadScope.(Modifier, MorphingShape, Boolean, @Composable () -> Unit) -> Unit {
    val effect = remember { RuntimeEffect.makeForShader(MY_TRY) }
    val time = remember { mutableStateOf(0f) }
    LaunchedEffect(effect) {
        val startTime = withFrameNanos { it }
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                time.value = (frameTimeNanos - startTime) / ONE_SECOND_NANOS
            }
        }
    }
    return remember {
        movableContentWithReceiverOf { modifier, targetShape, isExpanded, content ->
            Box(
                contentAlignment = targetShape.contentAlignment,
                modifier = modifier
                    .animatePlacement(lookaheadScope = this)
                    .morphingShape(targetShape)
                    .background(color = Color(0xA0_00_00_00))
                    .then(if (isExpanded) Modifier else Modifier.graphicsLayer {
                        val builder = RuntimeShaderBuilder(effect).apply {
                            uniform("resolution", size.width, size.height)
                            uniform("time", time.value)
                            uniform("flag", 1.0f)
                        }

                        val skiaFilter = ImageFilter.makeRuntimeShader(
                            runtimeShaderBuilder = builder,
                            shaderName = "composable",
                            input = null
                        )

                        renderEffect = skiaFilter.asComposeRenderEffect()
                    })
                    .padding(8.dp)
            ) {
                content()
            }
        }
    }
}

private data class MovableArgs<R, P1, P2, P3, P4>(
    val receiver: R, val p1: P1, val p2: P2, val p3: P3, val p4: P4,
)

@OptIn(InternalComposeApi::class)
fun <R, P1, P2, P3, P4> movableContentWithReceiverOf(
    content: @Composable R.(P1, P2, P3, P4) -> Unit
): @Composable R.(P1, P2, P3, P4) -> Unit {

    val movableContent = MovableContent<MovableArgs<R, P1, P2, P3, P4>> {
        it.receiver.content(it.p1, it.p2, it.p3, it.p4)
    }

    return { p1, p2, p3, p4 ->
        currentComposer.insertMovableContent(
            movableContent,
            MovableArgs(this, p1, p2, p3, p4)
        )
    }
}

@Composable
fun MainMenu(
    body: @Composable BoxScope.() -> Unit,
    menuContent: @Composable BoxScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(30.vw)
    ) {
//        FlowRow(
//            modifier = Modifier.fillMaxSize(),
//            maxItemsInEachRow = 2,
//            horizontalArrangement = Arrangement.spacedBy(8.dp),
//            verticalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
           menuContent()
//        }

        body()
    }
}
