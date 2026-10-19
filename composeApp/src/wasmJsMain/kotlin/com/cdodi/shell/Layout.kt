package com.cdodi.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.cdodi.LocalViewportSize

/** Narrower than this, the site uses its phone layout. */
val CompactWidth = 600.dp

/**
 * The sizes of the site's frame for this window (DESIGN.md §8, the mockups at 1440 × 900). On Home, the menu is a
 * square window of four panes around the Welcome rhombus; on every other page, a slim top bar with the menu's words.
 * On a phone ([compact]) the window fills the width and the type is smaller.
 *
 * Home's window is a composition, like a slide: its words, rules and gaps are set for [designWindow] and the whole of
 * it is scaled to [window], density and all ([windowScale]), so they keep their places on the panes at any size.
 */
@Immutable
data class MenuLayout(
    val compact: Boolean,
    /** Space from the window's edges to the bar's contents and the corner hints. */
    val margin: Dp,
    /** The side of Home's square of panes, on screen. */
    val window: Dp,
    /** The side Home's window is designed at: the mockup's on a computer, a 390 dp phone's on a phone. */
    val designWindow: Dp,
    /** The top bar's height; pages start below it. */
    val barHeight: Dp,
    /** Space between the words in the top bar. */
    val navGap: Dp,
    /** A word in the top bar. */
    val navLabel: TextUnit,
    /** The same word on its Home pane, at [designWindow]. */
    val homeLabel: TextUnit,
    /** "Welcome", at [designWindow]. */
    val welcome: TextUnit,
) {
    /** How far Home's window is scaled from its design: inside it, a dp is this many of the page's. */
    val windowScale: Float get() = window / designWindow

    /** One pane's box: a quarter of the window. */
    val pane: Dp get() = window / 2

    /** The Welcome rhombus, 14 design dp inside the window. */
    val rhombus: Dp get() = window - 28.dp * windowScale

    /** Where a page starts. */
    val pageTop: Dp get() = barHeight
}

/** The layout for a window [width] × [height] (the whole viewport). */
fun menuLayout(width: Dp, height: Dp): MenuLayout {
    val compact = width < CompactWidth
    val window = if (compact) min(width - 32.dp, height * 0.55f) else min(width * 0.39f, height * 0.62f)
    return MenuLayout(
        compact = compact,
        margin = if (compact) 16.dp else 48.dp,
        window = window,
        designWindow = if (compact) 358.dp else 558.dp,
        barHeight = if (compact) 56.dp else 72.dp,
        navGap = if (compact) 16.dp else 44.dp,
        navLabel = if (compact) 17.sp else 23.sp,
        homeLabel = if (compact) 24.sp else 34.sp,
        welcome = if (compact) 40.sp else 64.sp,
    )
}

val LocalMenuLayout = compositionLocalWithComputedDefaultOf {
    val viewport = LocalViewportSize.currentValue
    menuLayout(viewport.width, viewport.height)
}
