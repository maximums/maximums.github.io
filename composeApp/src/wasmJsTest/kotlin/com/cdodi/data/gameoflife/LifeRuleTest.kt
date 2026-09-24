package com.cdodi.data.gameoflife

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifeRuleTest {

    @Test
    fun conwayMasks() {
        assertEquals(0b1000, LifeRule.Conway.birth)
        assertEquals(0b1100, LifeRule.Conway.survive)
    }

    @Test
    fun notationRoundTrips() {
        listOf("B3/S23", "B36/S23", "B2/S", "B/S012345678").forEach { notation ->
            assertEquals(notation, LifeRule.parse(notation).toString())
        }
    }

    @Test
    fun parsingIsLenientAboutCaseAndWhitespace() {
        assertEquals(LifeRule.Conway, LifeRule.parse("  b3/s23 "))
    }

    @Test
    fun invalidNotationIsRejected() {
        listOf("", "B9/S23", "S23/B3", "B3S23", "Conway").forEach { notation ->
            assertFailsWith<IllegalArgumentException>(notation) { LifeRule.parse(notation) }
        }
    }

    @Test
    fun conwayDecisions() {
        val rule = LifeRule.Conway

        assertTrue(rule.isAliveNext(isAlive = false, neighbours = 3))
        assertFalse(rule.isAliveNext(isAlive = false, neighbours = 2))
        assertTrue(rule.isAliveNext(isAlive = true, neighbours = 2))
        assertTrue(rule.isAliveNext(isAlive = true, neighbours = 3))
        assertFalse(rule.isAliveNext(isAlive = true, neighbours = 1))
        assertFalse(rule.isAliveNext(isAlive = true, neighbours = 4))
    }
}
