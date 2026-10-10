package com.midinero.control.domain

import com.midinero.control.data.CustomRule
import com.midinero.control.data.RuleMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun `tipos de coincidencia`() {
        assertTrue(RuleMatcher.matches(CustomRule(pattern = "amzn", categoryId = 1), "AMZN Mktp ES*2K4"))
        assertTrue(RuleMatcher.matches(CustomRule(pattern = "Café", categoryId = 1), "CAFE DE LA ESQUINA"))
        assertFalse(RuleMatcher.matches(CustomRule(pattern = "bar", categoryId = 1), "Barcelona Tours"))
        assertTrue(RuleMatcher.matches(CustomRule(pattern = "super", match = RuleMatch.STARTS_WITH), "Supermercados Paco"))
        assertFalse(RuleMatcher.matches(CustomRule(pattern = "paco", match = RuleMatch.STARTS_WITH), "Supermercados Paco"))
        assertTrue(RuleMatcher.matches(CustomRule(pattern = "Gym Sur", match = RuleMatch.EXACT), "GYM SUR 123"))
        assertFalse(RuleMatcher.matches(CustomRule(pattern = "Gym", match = RuleMatch.EXACT), "GYM SUR"))
        assertFalse(RuleMatcher.matches(CustomRule(pattern = "  "), "lo que sea"))
    }

    @Test
    fun `gana la prioridad y luego la mas especifica`() {
        val rules = listOf(
            CustomRule(id = 1, pattern = "amazon", categoryId = 10),
            CustomRule(id = 2, pattern = "amazon prime", categoryId = 20),
            CustomRule(id = 3, pattern = "amazon", categoryId = 30, priority = 5),
        )
        assertEquals(3L, RuleMatcher.find(rules, "Amazon Prime Video")!!.id)
        assertEquals(2L, RuleMatcher.find(rules.dropLast(1), "Amazon Prime Video")!!.id)
        assertNull(RuleMatcher.find(rules, "Mercadona"))
    }

    @Test
    fun `alias`() {
        val rules = listOf(CustomRule(pattern = "amzn", renameTo = "Amazon"))
        assertEquals("Amazon", RuleMatcher.displayName(rules, "AMZN Mktp ES"))
        assertEquals("Lidl", RuleMatcher.displayName(rules, "Lidl"))
    }
}
