package com.cdodi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cdodi.ui.theme.SiteTheme

private val Pill = RoundedCornerShape(24.dp)

/** DESIGN.md §6, Lamp button: the one action that matters. A pill with a lamp border, a faint lamp fill and a glow. */
@Composable
fun LampButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val colors = SiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val warm by warmth(interaction)
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 48.dp)
            .glow(colors.accent.copy(alpha = 0.6f), radius = 24.dp, shape = Pill, spread = (-4).dp) { 0.6f + 0.4f * warm }
            .focusRing(interaction, Pill)
            .clip(Pill)
            .drawBehind { drawRect(colors.accent.copy(alpha = 0.08f + 0.08f * warm)) }
            .border(1.dp, colors.accent.copy(alpha = 0.5f), Pill)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.bright, modifier = Modifier.size(16.dp))
        Text(text, style = SiteTheme.type.small.copy(fontWeight = FontWeight.Medium), color = colors.bright)
    }
}

/**
 * DESIGN.md §6, Quiet button: everything else. A pill with a hairline border; hover strengthens the border and lights
 * the text. With an [icon] and no [text] it's a 48 px round icon button, described by [description].
 */
@Composable
fun QuietButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val colors = SiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val warm by warmth(interaction, alsoWhen = selected)
    val shape: Shape = if (text == null) CircleShape else Pill
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .focusRing(interaction, shape)
            .clip(shape)
            .drawBehind { if (selected) drawRect(colors.bright.copy(alpha = 0.06f)) }
            .border(1.dp, lerp(colors.bright.copy(alpha = 0.14f), colors.bright.copy(alpha = 0.35f), warm), shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = if (text == null) 0.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val colour = lerp(colors.text, colors.bright, warm)
        if (icon != null) Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(16.dp))
        if (text != null) Text(text, style = SiteTheme.type.small, color = colour)
    }
}

/** DESIGN.md §6, Round button: a 48 px lamp circle with a stroke icon (play, pause). */
@Composable
fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val warm by warmth(interaction)
    Box(
        modifier = modifier
            .size(48.dp)
            .glow(colors.accent.copy(alpha = 0.6f), radius = 22.dp, shape = CircleShape, spread = (-2).dp) { 0.65f + 0.35f * warm }
            .focusRing(interaction, CircleShape)
            .clip(CircleShape)
            .drawBehind { drawRect(colors.accent.copy(alpha = 0.08f + 0.08f * warm)) }
            .border(1.dp, colors.accent.copy(alpha = 0.5f), CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.bright, modifier = Modifier.size(16.dp))
    }
}

/** DESIGN.md §6, Label: a small upper-case sans heading over a group. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = SiteTheme.colors.muted) {
    Text(text.uppercase(), style = SiteTheme.type.label, color = color, maxLines = 1, modifier = modifier)
}

/** A hint in a corner: spaced-out mono capitals (DESIGN.md §8). Where it wraps, its lines keep to [textAlign]. */
@Composable
fun CornerHint(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text.uppercase(), style = SiteTheme.type.hint, color = SiteTheme.colors.muted, textAlign = textAlign, modifier = modifier)
}
