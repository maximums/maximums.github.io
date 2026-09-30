package com.cdodi.adapters.input

/**
 * The pointer as scenes see it: updated once per frame, in the heartbeat's Input phase, from the events that reached
 * the input layer (so never from a click on a widget). Positions are in device pixels, like the GPU canvas.
 */
class InputState {
    /** Where the pointer is; meaningful while [hasPointer] (false once it has left the page). */
    var pointerX: Float = 0f
        internal set
    var pointerY: Float = 0f
        internal set
    var hasPointer: Boolean = false
        internal set

    var isPressed: Boolean = false
        internal set

    /** Presses that started this frame. */
    var presses: Int = 0
        internal set

    /** Scrolling this frame, in the wheel's own units (positive is down). */
    var wheel: Float = 0f
        internal set
}
