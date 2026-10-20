package com.cdodi.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How much room a window has along one side, from a phone's to a computer's. */
enum class SizeClass { Compact, Medium, Expanded }

/** The mockups' width (DESIGN.md §8): a fluid size is the mockups' at this width. */
private val MockupWidth = 1440.dp

/**
 * The browser window the site fills, in dp (a dp is a CSS pixel). Sizes that belong to the whole screen, like Home's
 * window or the gutters, are fractions of it ([vw], [vh], [fluid]); text, touch targets and hairlines never are, so
 * browser zoom still enlarges them. The size classes choose between layouts, at Material's window size classes: 600
 * and 840 dp wide, 480 and 900 dp tall.
 */
@Immutable
data class Viewport(val width: Dp, val height: Dp) {
    val widthClass: SizeClass get() = when {
        width < 600.dp -> SizeClass.Compact
        width < 840.dp -> SizeClass.Medium
        else -> SizeClass.Expanded
    }

    val heightClass: SizeClass get() = when {
        height < 480.dp -> SizeClass.Compact
        height < 900.dp -> SizeClass.Medium
        else -> SizeClass.Expanded
    }

    /** A phone's width: the site's phone layout. */
    val compact: Boolean get() = widthClass == SizeClass.Compact

    /** Space from the window's edges to what's laid over the scene: a phone's 16 dp, elsewhere the mockups' 48. */
    val gutter: Dp get() = if (compact) 16.dp else fluid(48.dp, min = 24.dp)

    /** [percent] of the width, as CSS's `vw`. */
    fun vw(percent: Float): Dp = width * (percent / 100f)

    /** [percent] of the height, as CSS's `vh`. */
    fun vh(percent: Float): Dp = height * (percent / 100f)

    /** [size] as the mockups have it, narrowing with the window below their width, but never under [min]. */
    fun fluid(size: Dp, min: Dp): Dp = (size * (width / MockupWidth)).coerceIn(min, size)
}

/** The viewport, provided once at the root of the UI ([windowViewport]). */
val LocalViewport = staticCompositionLocalOf<Viewport> { error("No viewport: it's provided at the root of the UI") }

/** The viewport of the window this composition fills, following its size. */
@Composable
fun windowViewport(): Viewport {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return remember(size, density) { with(density) { Viewport(size.width.toDp(), size.height.toDp()) } }
}
