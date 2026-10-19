package com.cdodi.features.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.gpu.PassTiming
import com.cdodi.ui.components.HintOption
import com.cdodi.ui.components.formatDecimal
import com.cdodi.ui.theme.Autumn
import com.cdodi.ui.theme.AutumnColors
import kotlinx.coroutines.flow.StateFlow

/**
 * The background's quality as a hint in a corner (DESIGN.md §8): spaced-out mono, the choice under a lamp thread, and
 * what the background costs on the GPU right now, when the browser can time it.
 */
@Composable
fun QualityToggle(settings: BackgroundSettings, gpuTimings: StateFlow<List<PassTiming>?>, modifier: Modifier = Modifier) {
    val quality by settings.quality.collectEachFrame()
    val timings by gpuTimings.collectEachFrame()
    val cost = timings?.firstOrNull { it.name == "background" }?.milliseconds

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("BACKGROUND", style = Autumn.type.hint, color = AutumnColors.Fog, modifier = Modifier.padding(end = 6.dp))
        for (option in BackgroundQuality.entries) {
            HintOption(option.label, option == quality, "Background quality ${option.label}", onClick = { settings.setQuality(option) })
        }
        if (cost != null) Text("${formatDecimal(cost, 2)} MS", style = Autumn.type.hint, color = AutumnColors.Fog, modifier = Modifier.padding(start = 6.dp))
    }
}
