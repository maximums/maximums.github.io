package com.cdodi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import com.cdodi.ui.theme.Motion
import com.cdodi.ui.theme.SiteTheme
import com.cdodi.ui.theme.settling

/**
 * One choice in a corner hint (DESIGN.md §8): a spaced-out mono word, bright over a thread of the accent when
 * [chosen], muted otherwise, warming on hover. A 44 dp touch target, described to screen readers by [description].
 */
@Composable
fun HintOption(label: String, chosen: Boolean, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val warm by warmth(interaction)
    val thread by settling(chosen, Motion.SHORT_SECONDS)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .focusRing(interaction, GlassShape)
            .clickable(interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description; selected = chosen }
            .padding(horizontal = 6.dp)
            .drawBehind {
                val y = size.height / 2 + 10.dp.toPx()
                drawThread(colors.accent, Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y), alpha = thread)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = SiteTheme.type.hint,
            color = if (chosen) colors.bright else lerp(colors.muted, colors.text, warm),
        )
    }
}
