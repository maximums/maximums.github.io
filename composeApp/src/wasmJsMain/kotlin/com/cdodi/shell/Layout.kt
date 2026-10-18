package com.cdodi.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.cdodi.LocalViewportSize

/** Narrower than this, the site uses its phone layout. */
val CompactWidth = 600.dp

/**
 * The sizes of the menu for this window. On Home, the menu is a square window of four panes around the Welcome
 * rhombus; on every other page, it's a row of panes along the top, above the page. On a phone ([compact]) the window
 * fills the width, the row is shorter, and its labels are short.
 */
@Immutable
data class MenuLayout(
    val compact: Boolean,
    /** Space around everything, from the edges of the window. */
    val margin: Dp,
    /** The side of Home's square of panes. */
    val window: Dp,
    /** One pane in the top row. */
    val bar: DpSize,
    val barGap: Dp,
    val label: TextUnit,
    val welcome: TextUnit,
) {
    /** One triangle pane's box: a quarter of the window. */
    val pane: Dp get() = window / 2

    /** The Welcome rhombus. */
    val rhombus: Dp get() = window * 2 / 3

    /** Where a page starts, below the top row. */
    val pageTop: Dp get() = bar.height + barGap
}

/**
 * The layout for a window [width] × [height] (the whole viewport) and a top row of [panes].
 */
fun menuLayout(width: Dp, height: Dp, panes: Int = 5): MenuLayout {
    val compact = width < CompactWidth
    val margin = if (compact) 12.dp else 24.dp
    val innerWidth = width - margin * 2
    val innerHeight = height - margin * 2
    val gap = if (compact) 6.dp else 16.dp
    val paneWidth = min(if (compact) 120.dp else 220.dp, (innerWidth - gap * (panes - 1)) / panes)
    return MenuLayout(
        compact = compact,
        margin = margin,
        window = if (compact) min(innerWidth, innerHeight * 0.7f) else min(innerWidth * 0.31f, innerHeight * 0.8f),
        bar = DpSize(paneWidth, if (compact) 44.dp else 64.dp),
        barGap = gap,
        label = if (compact) 15.sp else 22.sp,
        welcome = if (compact) 24.sp else 30.sp,
    )
}

val LocalMenuLayout = compositionLocalWithComputedDefaultOf {
    val viewport = LocalViewportSize.currentValue
    menuLayout(viewport.width, viewport.height)
}
