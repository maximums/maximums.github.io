package com.cdodi.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cdodi.design.theme.Viewport
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuLayoutTest {

    /** The screens the site is checked on, as their browsers' viewports: phones, phones on their side, tablets, computers. */
    private val screens = listOf(
        320 to 568, 360 to 640, 390 to 844, 412 to 915,
        568 to 320, 667 to 375, 844 to 390, 932 to 430,
        600 to 900, 768 to 1024, 820 to 1180, 1024 to 768,
        1024 to 640, 1024 to 480, 1280 to 600, 1280 to 800, 1366 to 768, 1440 to 900, 1920 to 1080, 2560 to 1440, 3840 to 2160,
    ).map { (w, h) -> Viewport(w.dp, h.dp) }

    /** A 4-inch phone on its side: too short for any design at three quarters, so the type holds while the window shrinks. */
    private val tooShort = Viewport(568.dp, 320.dp)

    private fun layout(viewport: Viewport) = menuLayout(viewport)

    @Test
    fun theHomeMockupIsDrawnAtItsOwnSize() {
        val layout = layout(Viewport(1440.dp, 900.dp))
        assertEquals(HomeDesign.Large, layout.home)
        assertEquals(558.dp, layout.window)
        assertEquals(1f, layout.windowScale)
    }

    @Test
    fun homesWindowIsScaledNoFurtherThanItsDesignReads() {
        for (viewport in screens - tooShort) {
            val layout = layout(viewport)
            assertTrue(layout.windowScale in MIN_SCALE..MAX_SCALE, "$viewport: ${layout.home.side} at ${layout.windowScale}")
            assertEquals(layout.windowScale, layout.textScale, "$viewport")
        }
        val short = layout(tooShort)
        assertTrue(short.windowScale < MIN_SCALE)
        assertEquals(MIN_SCALE, short.textScale)
    }

    @Test
    fun theLargeDesignIsUsedWhereverItReads() {
        for (viewport in screens) {
            val layout = layout(viewport)
            val large = layout.window >= HomeDesign.Large.side * MIN_SCALE
            assertEquals(if (large) HomeDesign.Large else HomeDesign.Small, layout.home, "$viewport at ${layout.window}")
        }
    }

    @Test
    fun homesWindowAndItsMarksFitTheScreen() {
        for (viewport in screens) {
            val layout = layout(viewport)
            val withMarks = layout.window + layout.home.marksReach * 2 * layout.windowScale
            assertTrue(withMarks <= viewport.width - layout.margin + 0.01.dp, "$viewport: $withMarks across")
            val top = viewport.height / 2 + layout.windowOffset - withMarks / 2
            assertTrue(top >= 0.dp && top + withMarks <= viewport.height, "$viewport: $withMarks tall, from $top")
            layout.hintDrop?.let { assertTrue(viewport.height / 2 + it + 12.dp <= viewport.height, "$viewport: the hint at $it") }
        }
    }

    @Test
    fun homesWindowFollowsTheWidthWithoutJumps() {
        for (height in listOf(640, 900, 1200)) {
            var last: Dp? = null
            for (width in 300..2600) {
                val window = layout(Viewport(width.dp, height.dp)).window
                last?.let { assertTrue(abs((window - it).value) <= 1f, "${width}x$height: from $it to $window") }
                last = window
            }
        }
    }

    @Test
    fun phonesHaveThePhoneLayoutAndOnlyRoomyBarsHaveTheName() {
        for (viewport in screens) {
            val layout = layout(viewport)
            assertEquals(viewport.width < 600.dp, layout.compact, "$viewport")
            assertEquals(viewport.width >= 840.dp, layout.brandName, "$viewport")
            assertEquals(viewport.height < 480.dp, layout.short, "$viewport")
            assertEquals(layout.short, layout.hintDrop == null, "$viewport")
        }
    }

    @Test
    fun theGuttersAndGapsAreTheMockupsNarrowingWithTheWindow() {
        val mockup = layout(Viewport(1440.dp, 900.dp))
        assertEquals(48.dp, mockup.margin)
        assertEquals(44.dp, mockup.navGap)
        assertEquals(48.dp, layout(Viewport(2560.dp, 1440.dp)).margin)
        assertEquals(24.dp, layout(Viewport(600.dp, 900.dp)).margin)
        assertEquals(16.dp, layout(Viewport(390.dp, 844.dp)).margin)
    }
}
