package com.cdodi.features.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.gpu.PassTiming
import com.cdodi.ui.components.GlassShape
import com.cdodi.ui.components.drawThread
import com.cdodi.ui.components.focusRing
import com.cdodi.ui.components.formatDecimal
import com.cdodi.ui.components.warmth
import com.cdodi.ui.theme.Autumn
import com.cdodi.ui.theme.AutumnColors
import com.cdodi.ui.theme.Motion
import com.cdodi.ui.theme.settling
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
        for (option in BackgroundQuality.entries) Option(option, option == quality) { settings.setQuality(option) }
        if (cost != null) Text("${formatDecimal(cost, 2)} MS", style = Autumn.type.hint, color = AutumnColors.Fog, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Option(option: BackgroundQuality, chosen: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val warm by warmth(interaction)
    val thread by settling(chosen, Motion.SHORT_SECONDS)
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .focusRing(interaction, GlassShape)
            .clickable(interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Background quality ${option.label}"; selected = chosen }
            .padding(horizontal = 6.dp)
            .drawBehind {
                val y = size.height / 2 + 10.dp.toPx()
                drawThread(Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y), alpha = thread)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            option.label.uppercase(),
            style = Autumn.type.hint,
            color = if (chosen) AutumnColors.Parchment else lerp(AutumnColors.Fog, AutumnColors.Mist, warm),
        )
    }
}
