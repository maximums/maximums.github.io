package com.cdodi.features.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdodi.adapters.audio.AmbientSound
import com.cdodi.design.components.HintOption
import com.cdodi.design.theme.SiteTheme

/**
 * The rain's sound as a corner hint (DESIGN.md §8): "RAIN", then on and off. [compact] (a phone's top bar) is the word
 * alone, lit when the rain is on. Turning it on is the click that lets the browser start the sound.
 */
@Composable
fun SoundToggle(sound: AmbientSound, modifier: Modifier = Modifier, compact: Boolean = false) {
    val on by sound.enabled.collectAsState()
    if (compact) {
        HintOption("Rain", chosen = on, description = if (on) "Turn the rain off" else "Turn the rain on", onClick = { sound.setEnabled(!on) }, modifier = modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("RAIN", style = SiteTheme.type.hint, color = SiteTheme.colors.muted, modifier = Modifier.padding(end = 6.dp))
        HintOption("On", chosen = on, description = "Rain sound on", onClick = { sound.setEnabled(true) })
        HintOption("Off", chosen = !on, description = "Rain sound off", onClick = { sound.setEnabled(false) })
    }
}
