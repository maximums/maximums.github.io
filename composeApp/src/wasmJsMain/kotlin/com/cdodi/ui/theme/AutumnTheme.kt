package com.cdodi.ui.theme

import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/** The site's look (DESIGN.md): its type, and body text in Mist as the default for every `Text`. */
@Composable
fun AutumnTheme(fonts: AutumnFonts, content: @Composable () -> Unit) {
    val type = remember(fonts) { AutumnType(fonts) }
    CompositionLocalProvider(
        LocalAutumnType provides type,
        LocalTextStyle provides type.body,
        LocalContentColor provides AutumnColors.Mist,
        content = content,
    )
}

/** The theme's type roles: `Text("Boids", style = Autumn.type.title)`. */
object Autumn {
    val type: AutumnType
        @Composable @ReadOnlyComposable get() = LocalAutumnType.current
}
