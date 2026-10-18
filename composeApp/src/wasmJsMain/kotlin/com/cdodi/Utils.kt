package com.cdodi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@Immutable
data class ViewportSize(val width: Dp, val height: Dp)

inline val Number.vw: Dp
    @Composable get() = LocalViewportSize.current.width * (toFloat() / 100f)

inline val Number.vh: Dp
    @Composable get() = LocalViewportSize.current.height * (toFloat() / 100f)

val LocalViewportSize = compositionLocalWithComputedDefaultOf {
    val windowInfo = LocalWindowInfo.currentValue
    val density = LocalDensity.currentValue

    with(density) {
        ViewportSize(
            width = windowInfo.containerSize.width.toDp(),
            height = windowInfo.containerSize.height.toDp()
        )
    }
}
