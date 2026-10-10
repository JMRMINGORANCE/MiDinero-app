package com.midinero.control.domain

import com.midinero.control.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InsightsTest {

    private fun exp(date: LocalDate, cents: Long, merchant: String, cat: Long? = 1, income: Boolean = false) = Expense(
        amountCents = cents, merchant = merchant, categoryId = cat, isIncome = income,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    private val sept = YearMonth.of(2025, 9)

    @Test
    fun `resumen de categoria`() {
        val list = listOf(
            exp(LocalDate.of(2025, 9, 2), 3000, "Glovo"),
            exp(LocalDate.of(2025, 9, 5), 1000, "GLOVO 22"),
            exp(LocalDate.of(2025, 9, 6), 500, "Just Eat"),
            exp(LocalDate.of(2025, 8, 6), 6000, "Glovo"),
            exp(LocalDate.of(2025, 6, 6), 2000, "Glovo"),
            exp(LocalDate.of(2025, 9, 6), 9999, "Mercadona", cat = 2),
        )
        val c = Insights.category(1, sept, list)
        assertEquals(4500L, c.totalCents)
        assertEquals(6000L, c.previousCents)
        assertEquals(4000L, c.averageCents) // (6000 + 2000) / 2 meses con gasto
        assertEquals("Glovo" to 4000L, c.topMerchants.first())
        assertEquals(3, c.monthExpenses.size)
        assertEquals(6, c.trend.size)
    }

    @Test
    fun `historial de comercio`() {
        val list = listOf(
            exp(LocalDate.of(2025, 9, 2), 3000, "MERCADONA 1"),
            exp(LocalDate.of(2025, 7, 5), 1000, "Mercadona 2", cat = 3),
            exp(LocalDate.of(2025, 5, 5), 2000, "mercadona", cat = 3),
            exp(LocalDate.of(2025, 9, 3), 500, "Lidl"),
        )
        val m = Insights.merchant("mercadona", list, sept)
        assertEquals(3, m.count)
        assertEquals(6000L, m.totalCents)
        assertEquals(2000L, m.averageTicketCents)
        assertEquals(LocalDate.of(2025, 5, 5), m.first)
        assertEquals(LocalDate.of(2025, 9, 2), m.last)
        assertEquals(3L, m.categoryId)
        assertEquals("MERCADONA 1", m.displayName)
    }
}
