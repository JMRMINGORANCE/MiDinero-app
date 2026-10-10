package com.midinero.control.domain

import com.midinero.control.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarTest {
    private fun exp(date: LocalDate, cents: Long, income: Boolean = false, cat: Long? = 1) = Expense(
        amountCents = cents, merchant = "X", categoryId = cat, isIncome = income,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun `totales por dia y niveles por cuartiles`() {
        val sept = YearMonth.of(2025, 9)
        val list = listOf(
            exp(LocalDate.of(2025, 9, 1), 100), exp(LocalDate.of(2025, 9, 1), 100),
            exp(LocalDate.of(2025, 9, 2), 500), exp(LocalDate.of(2025, 9, 3), 1000),
            exp(LocalDate.of(2025, 9, 4), 100000), exp(LocalDate.of(2025, 9, 5), 999, income = true),
        )
        val totals = SpendingCalendar.dailyTotals(sept, list)
        assertEquals(200L, totals[LocalDate.of(2025, 9, 1)])
        assertEquals(null, totals[LocalDate.of(2025, 9, 5)])
        val levels = SpendingCalendar.levels(totals)
        assertEquals(1, levels[LocalDate.of(2025, 9, 1)])
        assertEquals(4, levels[LocalDate.of(2025, 9, 4)])
    }

    @Test
    fun `resumen anual`() {
        val list = listOf(
            exp(LocalDate.of(2025, 1, 10), 1000), exp(LocalDate.of(2025, 3, 10), 3000, cat = 2),
            exp(LocalDate.of(2025, 3, 11), 200000, income = true), exp(LocalDate.of(2024, 12, 10), 999),
        )
        val y = SpendingCalendar.year(2025, list)
        assertEquals(4000L, y.totalCents)
        assertEquals(200000L, y.incomeCents)
        assertEquals(2000L, y.monthlyAverageCents)
        assertEquals(YearMonth.of(2025, 3), y.maxMonth)
        assertEquals(YearMonth.of(2025, 1), y.minMonth)
        assertEquals(12, y.byMonth.size)
        assertEquals(2L to 3000L, y.byCategory.first())
    }
}
