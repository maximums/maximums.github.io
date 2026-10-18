package com.cdodi.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdodi.shell.LocalMenuLayout

private val Fog = Color(0xFF8C9499)
private val Hairline = Color(0xFFEDE6D6).copy(alpha = 0.12f)

/** DESIGN.md's fog pane, a little thicker: controls over a moving scene on a small screen must stay readable. */
private val PanelFill = Color(0xD80D1013)

/**
 * A scene page's controls. On a wide screen they're simply shown. On a phone they'd cover most of the scene, so they
 * fold behind a "Controls" button, and open over a fog fill.
 */
@Composable
fun CompactPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (!LocalMenuLayout.current.compact) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth().then(if (open) Modifier.background(PanelFill).padding(8.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (open) "CLOSE" else "CONTROLS",
            color = Fog,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = 44.dp) // DESIGN.md: touch targets of at least 44 px
                .wrapContentHeight(Alignment.CenterVertically)
                .border(1.dp, Hairline)
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        if (open) content()
    }
}
