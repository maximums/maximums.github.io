package com.cdodi.shell

import androidx.compose.runtime.mutableStateOf
import com.cdodi.adapters.compose.effects.UiEffectIds
import com.cdodi.core.navigation.BackStack
import com.cdodi.core.navigation.NavState
import com.cdodi.core.navigation.effect.AnimatedEffect
import com.cdodi.core.navigation.effect.EffectState
import com.cdodi.core.navigation.graph.Destination
import com.cdodi.features.about.AboutDestination
import com.cdodi.features.boids.BoidsDestination
import com.cdodi.features.home.HomeDestination
import com.cdodi.features.life.LifeDestination
import com.cdodi.features.life.engine.LifeRule
import com.cdodi.features.notes.NotesDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the screen reads off the navigation state: how much of Home is shown, the bar, the caption, the panes. */
class NavProjectionsTest {

    private val history = BackStack(listOf(HomeDestination))

    private fun idle(at: Destination) = NavState.Idle(at, history)

    /** A transition with each effect at its own progress, played backwards when [reversed] (a transition turned back). */
    private fun going(from: Destination, to: Destination, vararg effects: Pair<String, Float>, reversed: Boolean = false) =
        NavState.Transitioning(from, to, effects.map { (id, progress) -> EffectState(AnimatedEffect(id), progress, 0.0, reversed) }, null, history)

    @Test
    fun homeDissolvesAsTheMenuMorphsAwayAndBack() {
        assertEquals(1f, idle(HomeDestination).homeness())
        assertEquals(0f, idle(AboutDestination).homeness())
        assertEquals(1f, going(HomeDestination, AboutDestination).homeness(), "the morph hasn't started")
        assertEquals(0.75f, going(HomeDestination, AboutDestination, UiEffectIds.MENU_MORPH to 0.25f).homeness())
        assertEquals(0.25f, going(AboutDestination, HomeDestination, UiEffectIds.MENU_MORPH to 0.25f).homeness())
        assertEquals(0f, going(AboutDestination, BoidsDestination).homeness(), "one page to another")
    }

    @Test
    fun theLayoutChangesSidesWhenTheMorphStarts() {
        assertEquals(HomeDestination, going(HomeDestination, AboutDestination).layoutAt())
        assertEquals(AboutDestination, going(HomeDestination, AboutDestination, UiEffectIds.MENU_MORPH to 0f).layoutAt())
        assertEquals(AboutDestination, going(HomeDestination, AboutDestination).target(), "the bar's thread moves at the click")
    }

    @Test
    fun theBarCondensesLeavingHomeAndStaysBetweenPages() {
        assertEquals(1f, idle(AboutDestination).barSolidity())
        assertEquals(0f, going(HomeDestination, AboutDestination).barSolidity(), "before it condenses")
        assertEquals(0.4f, going(HomeDestination, AboutDestination, UiEffectIds.CONDENSE to 0.4f).barSolidity())
        assertEquals(1f, going(AboutDestination, NotesDestination()).barSolidity())
    }

    @Test
    fun onlyTheScenesHaveACaptionAndItFadesAsThePageArrives() {
        assertEquals("the flock is gathering", captionFor(going(HomeDestination, BoidsDestination)))
        assertEquals("the embers are catching", captionFor(going(LifeDestination(LifeRule.HighLife), HomeDestination)))
        assertNull(captionFor(going(HomeDestination, AboutDestination)))
        assertNull(captionFor(going(AboutDestination, BoidsDestination)), "not from one page to another")
        assertNull(captionFor(idle(BoidsDestination)))

        val holding = going(HomeDestination, BoidsDestination, UiEffectIds.CAPTION to 1f, UiEffectIds.FADE to 0.5f)
        assertEquals(0.5f, holding.captionAlpha())
        assertEquals(0f, going(HomeDestination, BoidsDestination).captionAlpha())
    }

    @Test
    fun theChosenPaneIsWipedAndTheOthersFogOver() {
        val nav = mutableStateOf<NavState>(going(HomeDestination, LifeDestination(), UiEffectIds.FOG_WIPE to 0.6f, UiEffectIds.FOG_THICKEN to 0.3f, UiEffectIds.LABEL_GLOW to 0.9f))
        val effects = nav.paneEffects()
        assertEquals(0.6f, effects.wipe(MenuItem.Life))
        assertEquals(0.9f, effects.glow(MenuItem.Life))
        assertEquals(0f, effects.thicken(MenuItem.Life))
        assertEquals(0.3f, effects.thicken(MenuItem.Boids))
        assertEquals(0f, effects.wipe(MenuItem.Boids))

        nav.value = idle(HomeDestination) // read while drawing: the same effects follow the state
        assertEquals(0f, effects.wipe(MenuItem.Life))
    }

    @Test
    fun eachMenuItemKnowsItsPageWhateverItCarries() {
        assertTrue(MenuItem.Life.isAt(LifeDestination(LifeRule.Seeds)))
        assertTrue(MenuItem.Notes.isAt(NotesDestination("compose-on-the-web")))
        assertFalse(MenuItem.Boids.isAt(LifeDestination()))
        assertTrue(going(HomeDestination, NotesDestination()).involves(HomeDestination))
    }

    @Test
    fun pagesMeltInTheMenusOrder() {
        assertEquals(listOf(HomeDestination) + MenuItem.entries.map { it.destination.route }, menuOrder)
        // Later in the menu melts left, earlier melts right: Life comes before Boids.
        assertEquals(-1f, meltDirection(LifeDestination(), BoidsDestination))
        assertEquals(1f, meltDirection(BoidsDestination, LifeDestination()))
        assertEquals(-1f, meltDirection(AboutDestination, NotesDestination()))
    }
}
