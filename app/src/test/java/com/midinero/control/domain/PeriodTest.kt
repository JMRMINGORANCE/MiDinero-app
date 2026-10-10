package com.midinero.control.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodTest {

    @After
    fun reset() { Period.startDay = 1 }

    private val sept = YearMonth.of(2025, 9)

    @Test
    fun `mes natural por defecto`() {
        assertEquals(LocalDate.of(2025, 9, 1), sept.startDate())
        assertEquals(LocalDate.of(2025, 10, 1), sept.endDateExclusive())
        assertEquals(30, sept.lengthInDays())
        assertEquals(sept, periodOf(LocalDate.of(2025, 9, 30)))
        assertNull(sept.rangeLabel())
    }

    @Test
    fun `inicio el 25 toma el nombre del mes siguiente`() {
        Period.startDay = 25
        assertEquals(LocalDate.of(2025, 8, 25), sept.startDate())
        assertEquals(LocalDate.of(2025, 9, 25), sept.endDateExclusive())
        assertEquals(31, sept.lengthInDays())
        assertEquals(sept, periodOf(LocalDate.of(2025, 8, 25)))
        assertEquals(sept, periodOf(LocalDate.of(2025, 9, 24)))
        assertEquals(YearMonth.of(2025, 10), periodOf(LocalDate.of(2025, 9, 25)))
        assertEquals(1, sept.dayIndex(LocalDate.of(2025, 8, 25)))
        // Enero empieza el 25 de diciembre del año anterior
        assertEquals(LocalDate.of(2024, 12, 25), YearMonth.of(2025, 1).startDate())
        assertEquals(YearMonth.of(2025, 1), periodOf(LocalDate.of(2024, 12, 31)))
    }

    @Test
    fun `inicio el 5 toma el nombre del mes en que empieza`() {
        Period.startDay = 5
        assertEquals(LocalDate.of(2025, 9, 5), sept.startDate())
        assertEquals(sept, periodOf(LocalDate.of(2025, 10, 4)))
        assertEquals(YearMonth.of(2025, 8), periodOf(LocalDate.of(2025, 9, 4)))
    }

    @Test
    fun `los milisegundos respetan el periodo`() {
        Period.startDay = 25
        val ts = LocalDate.of(2025, 8, 26).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(sept, monthOf(ts))
        assert(ts >= sept.startMillis() && ts < sept.endMillis())
    }

    @Test
    fun `ritmo con periodo personalizado`() {
        Period.startDay = 25
        val p = Budgets.pace(sept, 1000, 31000, today = LocalDate.of(2025, 8, 25))
        assertEquals(31, p.daysLeft)
    }
}
