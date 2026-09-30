package com.cdodi.adapters.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Heartbeat

/**
 * Catches the pointer where no widget does and hands it to the scenes. Place it under everything else: pointer events
 * can't pass through the Compose canvas to the WebGPU one, and Compose hit-tests siblings from the top down, stopping
 * at the first that takes the event, so any widget above wins and the scene gets the rest.
 *
 * Events are collected as they come and applied to [input] once per frame, in the Input phase.
 */
@Composable
fun InputLayer(input: InputState, heartbeat: Heartbeat) {
    val pending = remember { PendingInput() }
    DisposableEffect(heartbeat, input) {
        val subscription = heartbeat.subscribe(FramePhase.Input) { pending.applyTo(input) }
        onDispose(subscription::close)
    }

    Box(
        Modifier.fillMaxSize().pointerInput(pending) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    pending.record(event)
                    // Consumed, so the wheel and touch don't also scroll the page.
                    event.changes.forEach { it.consume() }
                }
            }
        }
    )
}

/** Pointer events since the last frame. Positions are already in device pixels on web. */
private class PendingInput {
    private var x = 0f
    private var y = 0f
    private var hasPointer = false
    private var isPressed = false
    private var presses = 0
    private var wheel = 0f

    fun record(event: PointerEvent) {
        val change = event.changes.firstOrNull() ?: return
        when (event.type) {
            PointerEventType.Exit -> hasPointer = false
            PointerEventType.Scroll -> wheel += change.scrollDelta.y
            else -> {
                x = change.position.x
                y = change.position.y
                hasPointer = true
                if (event.type == PointerEventType.Press) presses++
                isPressed = event.buttons.isPrimaryPressed
            }
        }
    }

    fun applyTo(input: InputState) {
        input.pointerX = x
        input.pointerY = y
        input.hasPointer = hasPointer
        input.isPressed = isPressed
        input.presses = presses
        input.wheel = wheel
        presses = 0
        wheel = 0f
    }
}
