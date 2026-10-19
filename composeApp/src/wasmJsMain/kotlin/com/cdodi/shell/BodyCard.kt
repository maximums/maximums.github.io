package com.cdodi.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.MovableContent
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.compose.morph.MorphingQuad
import com.cdodi.adapters.compose.morph.MorphingShape
import com.cdodi.adapters.compose.morph.animatePlacement
import com.cdodi.adapters.compose.morph.morphingShape
import com.cdodi.ui.components.glass

/**
 * The page's glass, read while drawing so it can follow a transition frame by frame: Home's fog, then the page's.
 * Without one, the theme's fog pane.
 */
val LocalPaneFill = staticCompositionLocalOf<(() -> Color)?> { null }

/**
 * The Welcome rhombus on Home, and the page's glass everywhere else: one pane that morphs between the two shapes
 * ([morphingShape]) while it moves ([animatePlacement]), its fog following [LocalPaneFill].
 */
@Composable
fun movableBodyCard(): @Composable LookaheadScope.(Modifier, MorphingShape, Boolean, @Composable () -> Unit) -> Unit {
    return remember {
        movableContentWithReceiverOf { modifier, targetShape, isExpanded, content ->
            val paneFill = LocalPaneFill.current
            val quad = remember { MorphingQuad() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .animatePlacement(lookaheadScope = this)
                    .morphingShape(targetShape, quad)
                    .glass(shape = RectangleShape, hairline = false, shadow = false, fill = paneFill, outline = quad.outline)
                    .padding(if (isExpanded) 0.dp else 8.dp)
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
