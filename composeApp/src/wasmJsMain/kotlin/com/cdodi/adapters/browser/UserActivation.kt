package com.cdodi.adapters.browser

import kotlinx.browser.window
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget

/**
 * The events that can give a page user activation (HTML, "activation triggering input event"): a key pressed, and a
 * pointer pressed, but a touch or a pen only when it's lifted. Which one counts is up to the browser, so all are heard.
 */
private val ActivationEvents = listOf("keydown", "pointerdown", "pointerup", "touchend")

/** Whether the page is handling a click, a tap or a key right now (`navigator.userActivation`; true where unknown). */
fun hasUserActivation(): Boolean = js("navigator.userActivation ? navigator.userActivation.isActive : true")

/**
 * Calls [then] once, on the first event on [target] that gives the page user activation: the only moment a browser
 * lets a page start sound. Events that don't give it (a finger put down) are let pass. All the listeners go together,
 * on that event or on [AutoCloseable.close].
 */
fun awaitUserActivation(
    target: EventTarget = window,
    activated: () -> Boolean = ::hasUserActivation,
    then: () -> Unit,
): AutoCloseable {
    // Removed by one abort, not one by one: a Kotlin lambda handed to JS twice isn't the same JS function twice.
    val controller = abortController()
    val listener: (Event) -> Unit = {
        if (activated()) {
            abort(controller)
            then()
        }
    }
    for (type in ActivationEvents) target.addEventListener(type, listener, listenerOptions(controller))
    return AutoCloseable { abort(controller) }
}

private fun abortController(): JsAny = js("new AbortController()")

private fun abort(controller: JsAny): Unit = js("controller.abort()")

private fun listenerOptions(controller: JsAny): AddEventListenerOptions = js("({ signal: controller.signal, passive: true })")
