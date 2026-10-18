package com.cdodi.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cdodi.shell.LocalMenuLayout
import com.cdodi.ui.components.QuietButton
import com.cdodi.ui.components.glass
import com.cdodi.ui.theme.AutumnColors

/**
 * A scene page's controls on a fog pane (DESIGN.md §5), [width] wide. On a phone they'd cover most of the scene, so
 * they fold behind a "Controls" button and open full width, scrolling when they're taller than the screen.
 */
@Composable
fun CompactPanel(width: Dp, modifier: Modifier = Modifier, spacing: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    if (!LocalMenuLayout.current.compact) {
        Column(modifier.width(width).glass().padding(28.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuietButton(if (open) "Close" else "Controls", { open = !open }, Modifier.align(Alignment.End))
        if (open) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .glass(fill = { AutumnColors.Night.copy(alpha = 0.82f) })
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        }
    }
}
