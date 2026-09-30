package com.cdodi.adapters.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import com.cdodi.adapters.compose.effects.PageEffect
import com.cdodi.adapters.compose.effects.PageRole
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.graph.Destination

/**
 * The navigation renderer of the UI layer: the current destination's page, or both pages during a transition, with
 * the page effects the transition plays.
 *
 * Which pages are shown changes only when a transition starts or ends, and only that recomposes. The effects are read
 * inside each page's graphics layer, so a frame's progress costs no recomposition and appears in the same frame. (A
 * `SeekableTransitionState` seeked to the progress would lag a frame, since the seek runs in a coroutine after the
 * frame that computed it, and it adds catch-up animations whenever the target changes.) Each page is keyed by its
 * destination, so it keeps its state from being entered to being shown.
 */
@Composable
fun NavPages(
    navState: State<NavState>,
    effects: List<PageEffect>,
    modifier: Modifier = Modifier,
    page: @Composable (Destination) -> Unit,
) {
    val layers by remember(navState) { derivedStateOf { navState.value.layers() } }

    Box(modifier) {
        for ((destination, role) in layers) {
            key(destination) {
                Box(Modifier.fillMaxSize().graphicsLayer { style(navState.value, role, effects) }) {
                    page(destination)
                }
            }
        }
    }
}

private fun NavState.layers(): List<Pair<Destination, PageRole?>> = when (this) {
    is NavState.Idle -> listOf(at to null)
    is NavState.Transitioning -> listOf(from to PageRole.Leaving, to to PageRole.Entering)
}

private fun GraphicsLayerScope.style(nav: NavState, role: PageRole?, effects: List<PageEffect>) {
    if (role == null || nav !is NavState.Transitioning) return
    alpha = if (role == PageRole.Entering) 0f else 1f
    for (state in nav.effects) {
        val effect = effects.firstOrNull { it.id == state.effect.id } ?: continue
        with(effect) { apply(state, role, nav) }
    }
}
