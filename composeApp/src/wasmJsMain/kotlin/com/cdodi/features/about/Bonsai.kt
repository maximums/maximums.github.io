package com.cdodi.features.about

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.cdodi.adapters.compose.gpuBacked
import com.cdodi.adapters.gpu.GpuWidgets

/**
 * Where the maple stands on the About page: an empty box that the scene draws the tree behind ([BonsaiRenderer]),
 * filling it at any size, fading with its page (`LocalPageVisibility`).
 */
@Composable
fun Bonsai(modifier: Modifier = Modifier) {
    Box(modifier.gpuBacked(GpuWidgets.Kind.Bonsai))
}

/**
 * Turns the maple by dragging across this element, with a mouse or a finger: its front follows the pointer, a drag
 * across the element's whole width turning it [TURN_PER_WIDTH] radians ([Orbit.turn]). [held] says whether the pointer
 * is over the element or pressing on it.
 */
fun Modifier.turnsBonsai(orbit: Orbit, held: (Boolean) -> Unit): Modifier = pointerInput(orbit) {
    var over = false
    var pressed = false
    var lastX = 0f
    fun tell() = held(over || pressed)
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                when (event.type) {
                    PointerEventType.Enter -> over = true
                    PointerEventType.Exit -> over = false
                    PointerEventType.Press -> {
                        pressed = true
                        lastX = change.position.x
                    }
                    PointerEventType.Release -> pressed = false
                    PointerEventType.Move -> if (pressed && size.width > 0) {
                        orbit.turn(-(change.position.x - lastX) / size.width * TURN_PER_WIDTH)
                        lastX = change.position.x
                        change.consume()
                    }
                }
                tell()
            }
        }
    } finally {
        // Cancelled (the page left mid-drag): nothing is held any more.
        held(false)
    }
}

/** A drag across the whole element: a little more than the arc, end to end. */
private const val TURN_PER_WIDTH = 2.4f
