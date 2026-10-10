package com.midinero.control.domain

import com.midinero.control.data.Frequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    @Test
    fun `mensual respeta el dia 31 en meses cortos`() {
        val jan31 = LocalDate.of(2025, 1, 31)
        val feb = Recurrence.next(jan31, Frequency.MONTHLY, 31)
        assertEquals(LocalDate.of(2025, 2, 28), feb)
        assertEquals(LocalDate.of(2025, 3, 31), Recurrence.next(feb, Frequency.MONTHLY, 31))
    }

    @Test
    fun `anual en bisiesto`() {
        val d = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), Recurrence.next(d, Frequency.YEARLY, 29))
    }

    @Test
    fun `primer cargo desde hoy al reactivar`() {
        val start = LocalDate.of(2025, 1, 5)
        val today = LocalDate.of(2025, 4, 10)
        assertEquals(LocalDate.of(2025, 5, 5), Recurrence.firstOnOrAfter(start, today, Frequency.MONTHLY, 5))
        assertEquals(LocalDate.of(2025, 4, 10), Recurrence.firstOnOrAfter(LocalDate.of(2025, 3, 10), today, Frequency.MONTHLY, 10))
    }

    @Test
    fun `semanal y trimestral`() {
        val d = LocalDate.of(2025, 1, 30)
        assertEquals(LocalDate.of(2025, 2, 6), Recurrence.next(d, Frequency.WEEKLY, 30))
        assertEquals(LocalDate.of(2025, 4, 30), Recurrence.next(d, Frequency.QUARTERLY, 30))
        assertEquals(LocalDate.of(2025, 5, 31), Recurrence.next(LocalDate.of(2025, 2, 28), Frequency.QUARTERLY, 31))
    }

    @Test
    fun `equivalente mensual y anual`() {
        assertEquals(1000L * 52 / 12, Recurrence.monthlyEquivalentCents(1000, Frequency.WEEKLY))
        assertEquals(1000L, Recurrence.monthlyEquivalentCents(3000, Frequency.QUARTERLY))
        assertEquals(100L, Recurrence.monthlyEquivalentCents(1200, Frequency.YEARLY))
    }
}
