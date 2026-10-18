package com.cdodi.features.background

import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How many of the background's pixels are computed: the rest come from a linear upscale. */
enum class BackgroundQuality(val scale: Float, val label: String) {
    Quarter(0.25f, "¼"),
    Half(0.5f, "½"),
    Full(1f, "full"),
}

/** The background quality this viewer picked, remembered in this browser (and kept in memory where it can't be). */
class BackgroundSettings {
    private val state = MutableStateFlow(load() ?: BackgroundQuality.Half)

    val quality: StateFlow<BackgroundQuality> = state.asStateFlow()

    fun setQuality(quality: BackgroundQuality) {
        state.value = quality
        try {
            window.localStorage.setItem(KEY, quality.name)
        } catch (_: Throwable) {
            // Storage blocked (a private window, cleared site data): the choice lasts until the page closes.
        }
    }

    private fun load(): BackgroundQuality? = try {
        window.localStorage.getItem(KEY)?.let { name -> BackgroundQuality.entries.firstOrNull { it.name == name } }
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val KEY = "background-quality"
    }
}
