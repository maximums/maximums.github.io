package com.cdodi.adapters.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.cdodi.core.time.FramePhase
import com.cdodi.core.time.Heartbeat

/**
 * Hands the pointer to the scenes. Place it under everything else: pointer events can't pass through the Compose
 * canvas to the WebGPU one, so the scenes get them from here.
 *
 * It reads each event in the Final pass, after the widgets above have had theirs: a press or scroll a widget consumed
 * (a click on the menu) is not the scene's. The pointer's position is, wherever it is. It only watches and never
 * consumes: Compose cancels a tap when anything consumes its events in the Final pass, so consuming here would break
 * every button. (The page can't scroll anyway; `body` hides its overflow.) Events are collected as they come and
 * applied to [input] once per frame, in the Input phase.
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
                    pending.record(awaitPointerEvent(PointerEventPass.Final))
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
        val isForScene = !change.isConsumed
        when (event.type) {
            PointerEventType.Exit -> hasPointer = false
            PointerEventType.Scroll -> if (isForScene) wheel += change.scrollDelta.y
            else -> {
                x = change.position.x
                y = change.position.y
                hasPointer = true
                if (event.type == PointerEventType.Press && isForScene) presses++
                isPressed = event.buttons.isPrimaryPressed && (isForScene || isPressed)
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
