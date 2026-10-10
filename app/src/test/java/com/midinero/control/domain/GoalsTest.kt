package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsTest {
    private val today = LocalDate.of(2025, 9, 1)

    @Test
    fun `lo que hay que apartar al mes`() {
        val p = Goals.progress(120000, LocalDate.of(2026, 3, 1), listOf(today.minusDays(10) to 20000L), today)
        assertEquals(20000L, p.savedCents)
        assertEquals(100000L, p.remainingCents)
        assertEquals(6, p.monthsLeft)
        assertEquals(16667L, p.monthlyNeededCents)
    }

    @Test
    fun `estimacion al ritmo actual`() {
        val contributions = listOf(
            today.minusDays(80) to 10000L, today.minusDays(50) to 10000L, today.minusDays(20) to 10000L,
        )
        val p = Goals.progress(60000, LocalDate.of(2025, 11, 1), contributions, today)
        // 100 € al mes y faltan 300 €: unos 3 meses -> diciembre, tarde para noviembre
        assertEquals(2025, p.estimatedDate!!.year)
        assertEquals(12, p.estimatedDate!!.monthValue)
        assertEquals(false, Goals.onTrack(p, LocalDate.of(2025, 11, 1)))
        assertEquals(true, Goals.onTrack(p, LocalDate.of(2026, 1, 15)))
    }

    @Test
    fun `meta cumplida y sin ritmo`() {
        val done = Goals.progress(1000, null, listOf(today to 1500L), today)
        assertTrue(done.completed)
        assertNull(done.monthlyNeededCents)
        assertNull(done.estimatedDate)
        val stalled = Goals.progress(1000, null, emptyList(), today)
        assertFalse(stalled.completed)
        assertNull(stalled.estimatedDate)
        assertEquals(0f, stalled.ratio)
        val late = Goals.progress(1000, today.minusDays(1), listOf(today to 200L), today)
        assertEquals(800L, late.monthlyNeededCents)
    }
}
