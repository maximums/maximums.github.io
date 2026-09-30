package com.cdodi.adapters.browser

import com.cdodi.core.bus.LifecycleBus
import com.cdodi.core.bus.LifecycleEvent
import kotlinx.browser.document
import kotlinx.browser.window

/** Not in kotlinx-browser's `Document`. */
private fun isDocumentHidden(): Boolean = js("document.hidden")

/** Reports the page's visibility now and whenever it changes (a hidden tab, a minimised window). */
fun followVisibility(lifecycle: LifecycleBus) {
    fun report() = lifecycle.send(if (isDocumentHidden()) LifecycleEvent.Background else LifecycleEvent.Foreground)
    document.addEventListener("visibilitychange", { report() })
    report()
}

/** Reports `prefers-reduced-motion` now and whenever the user changes it. */
fun followReducedMotion(lifecycle: LifecycleBus) {
    val query = window.matchMedia("(prefers-reduced-motion: reduce)")
    fun report() = lifecycle.send(LifecycleEvent.ReducedMotionChanged(reduced = query.matches))
    query.addEventListener("change", { report() })
    report()
}
