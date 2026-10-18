package com.cdodi.features.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.adapters.compose.collectEachFrame
import com.cdodi.adapters.gpu.PassTiming
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private val Fog = Color(0xFF8C9499)
private val Parchment = Color(0xFFEDE6D6)

/**
 * The background's quality, as a quiet line of spaced-out mono in a corner (DESIGN.md §8's corner hints): pick ¼, ½ or
 * full, and see what the background costs on the GPU right now, when the browser can time it.
 */
@Composable
fun QualityToggle(settings: BackgroundSettings, gpuTimings: StateFlow<List<PassTiming>?>, modifier: Modifier = Modifier) {
    val quality by settings.quality.collectEachFrame()
    val timings by gpuTimings.collectEachFrame()
    val cost = timings?.firstOrNull { it.name == "background" }?.milliseconds

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("BACKGROUND", color = Fog, fontSize = 10.sp, letterSpacing = 2.5.sp, fontFamily = FontFamily.Monospace)
        for (option in BackgroundQuality.entries) {
            Text(
                text = option.label,
                color = if (option == quality) Parchment else Fog.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { settings.setQuality(option) }.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (cost != null) {
            val hundredths = (cost * 100).roundToInt()
            Text("${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} ms", color = Fog, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
