package com.cdodi.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.cdodi.ui.theme.SizeClass
import com.cdodi.ui.theme.Viewport

/** The least a design is scaled to: below three quarters of its size, its type gets too small to read. */
const val MIN_SCALE = 0.75f

/** The most a design is scaled to: on a larger screen Home's window stops growing, and the street gets the room. */
const val MAX_SCALE = 1.5f

/**
 * On a short screen (a phone on its side), the room above Home's window, for its corner marks (the sound toggle is off
 * to the side), and below it, for the marks and the quality toggle under them.
 */
private val ShortRoomAbove = 24.dp
private val ShortRoomBelow = 64.dp

/**
 * Home's window as designed (DESIGN.md §8, the Home mockup): every size in it, for a window of [side]. The window is
 * a composition, like a slide, drawn at any size by scaling all of it, density and all, so its words, rules and gaps
 * keep their places on the panes. There are two designs: the mockup's ([Large]), and a 390 dp phone's ([Small]),
 * whose words are larger for its side.
 */
@Immutable
data class HomeDesign(
    /** The window's side. */
    val side: Dp,
    /** A menu word on its pane. */
    val label: TextUnit,
    /** "Welcome". */
    val welcome: TextUnit,
    /** The line of verse under the name, in the rhombus. */
    val verse: TextUnit,
    /** Space between the rhombus's lines. */
    val welcomeGap: Dp,
    /** A pane's words from its outer corner, across. */
    val insetX: Dp,
    /** A pane's words from its outer corner, up or down. */
    val insetY: Dp,
    /** The lamp rule under a pane's word, grown on hover. It must end short of the pane's diagonal. */
    val rule: Dp,
    /** How far the panes stand off the window's diagonals. */
    val paneGap: Dp = 10.dp,
    /** The Welcome rhombus, inside the window's edges. */
    val rhombusInset: Dp = 14.dp,
    /** How far the corner marks reach outside the window. */
    val marksReach: Dp = 20.dp,
) {
    companion object {
        /** The mockup's, at 1,440 × 900. */
        val Large = HomeDesign(
            side = 558.dp, label = 34.sp, welcome = 64.sp, verse = 19.sp, welcomeGap = 18.dp,
            insetX = 36.dp, insetY = 38.dp, rule = 110.dp,
        )

        /** A 390 dp phone's. Its rule is shorter: at 72 dp it touched the diagonal. */
        val Small = HomeDesign(
            side = 358.dp, label = 24.sp, welcome = 40.sp, verse = 16.sp, welcomeGap = 12.dp,
            insetX = 18.dp, insetY = 18.dp, rule = 64.dp,
        )
    }
}

/**
 * The sizes of the site's frame for a [Viewport] (DESIGN.md §8, the mockups at 1440 × 900). On Home, the menu is a
 * square window of four panes around the Welcome rhombus: [home] drawn at [window]. On every other page, it's a slim
 * top bar with the menu's words. A phone ([compact]) has its own bar, and a window across its width; a short screen
 * ([short], a phone on its side) has a window as tall as it can be, with Home's toggles above and below it. The large
 * design is drawn from three quarters of its size up; a window smaller than that gets the small one.
 */
@Immutable
data class MenuLayout(
    val compact: Boolean,
    val short: Boolean,
    /** Space from the screen's sides to the bar's contents and Home's toggles. */
    val margin: Dp,
    /** Space from the top of the screen to Home's sound toggle. */
    val toggleTop: Dp,
    /** Space from the bottom of the screen to Home's quality toggle and colophon. */
    val toggleBottom: Dp,
    /** The side of Home's window, on screen. */
    val window: Dp,
    /** How far below the screen's centre Home's window sits (above it, when negative). */
    val windowOffset: Dp,
    /** Home's window as designed: on screen, it's scaled by [windowScale]. */
    val home: HomeDesign,
    /** How far below the window's centre Home's hint sits; null where there's no room for it. */
    val hintDrop: Dp?,
    /** The top bar's height; pages start below it. */
    val barHeight: Dp,
    /** Space between the words in the top bar. */
    val navGap: Dp,
    /** A word in the top bar. */
    val navLabel: TextUnit,
    /** Whether the top bar has room for the name beside the brand's diamond. */
    val brandName: Boolean,
) {
    /** How far Home's window is scaled from its design: inside it, a dp is this many of the page's. */
    val windowScale: Float get() = window / home.side

    /** How far the type in Home's window is scaled: as the window, but never under [MIN_SCALE]. */
    val textScale: Float get() = maxOf(windowScale, MIN_SCALE)

    /** The Welcome rhombus, on screen. */
    val rhombus: Dp get() = window - home.rhombusInset * 2 * windowScale

    /** Where a page starts. */
    val pageTop: Dp get() = barHeight
}

/** The layout for a [viewport]. */
fun menuLayout(viewport: Viewport): MenuLayout {
    val compact = viewport.compact
    val short = viewport.heightClass == SizeClass.Compact
    val margin = viewport.gutter
    // The mockup's share of the screen (39 vw, 62 vh), but no smaller than the large design reads at; on a short
    // screen, all the height the toggles leave. Up to the largest scale.
    val wanted = (if (short) viewport.height - ShortRoomAbove - ShortRoomBelow else min(viewport.vh(62f), max(viewport.vw(39f), HomeDesign.Large.side * MIN_SCALE)))
        .coerceAtMost(HomeDesign.Large.side * MAX_SCALE)
    // As much of it as fits across with its corner marks, which may reach into the gutters, but only halfway. Whichever
    // design it gets, so the window grows smoothly with the screen.
    val marks = 1 + 2 * maxOf(HomeDesign.Large.marksReach / HomeDesign.Large.side, HomeDesign.Small.marksReach / HomeDesign.Small.side)
    val window = min(wanted, (viewport.width - margin) / marks)
    val home = if (window >= HomeDesign.Large.side * MIN_SCALE) HomeDesign.Large else HomeDesign.Small
    val tight = compact || short
    return MenuLayout(
        compact = compact,
        short = short,
        margin = margin,
        toggleTop = if (tight) 8.dp else 24.dp,
        toggleBottom = if (tight) 12.dp else 36.dp,
        window = window,
        windowOffset = if (short) (ShortRoomAbove - ShortRoomBelow) / 2 else 0.dp,
        home = home,
        hintDrop = if (short) null else window / 2 + 60.dp,
        barHeight = if (compact) 56.dp else 72.dp,
        navGap = if (compact) 16.dp else viewport.fluid(44.dp, min = 24.dp),
        navLabel = if (compact) 17.sp else 23.sp,
        brandName = viewport.widthClass == SizeClass.Expanded,
    )
}

/** The frame's layout, provided once at the root of the UI (AppRoot), from the viewport. */
val LocalMenuLayout = staticCompositionLocalOf<MenuLayout> { error("No menu layout: it's provided at the root of the UI") }
