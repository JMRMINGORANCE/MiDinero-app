package com.midinero.control.domain

import com.midinero.control.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetsTest {

    private fun exp(date: LocalDate, cents: Long, cat: Long? = 1, income: Boolean = false) = Expense(
        amountCents = cents, merchant = "X", categoryId = cat, isIncome = income,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun `ritmo y lo que queda por dia`() {
        val sept = YearMonth.of(2025, 9)
        val p = Budgets.pace(sept, spentCents = 20000, limitCents = 30000, today = LocalDate.of(2025, 9, 10))
        // 10 de 30 días = 33 %, gastado 67 %: vas por encima del ritmo
        assertTrue(p.aheadOfPace)
        assertEquals(21, p.daysLeft)
        assertEquals(10000L / 21, p.dailyAllowanceCents)

        val calm = Budgets.pace(sept, 5000, 30000, LocalDate.of(2025, 9, 20))
        assertFalse(calm.aheadOfPace)

        val over = Budgets.pace(sept, 31000, 30000, LocalDate.of(2025, 9, 20))
        assertNull(over.dailyAllowanceCents)
        assertFalse(over.aheadOfPace) // ya pasado: no es cuestión de ritmo

        val closed = Budgets.pace(YearMonth.of(2025, 8), 1000, 30000, LocalDate.of(2025, 9, 20))
        assertEquals(1f, closed.monthFraction)
        assertNull(closed.dailyAllowanceCents)
    }

    @Test
    fun `media de tres meses por categoria`() {
        val list = listOf(
            exp(LocalDate.of(2025, 6, 5), 9000),
            exp(LocalDate.of(2025, 7, 5), 3000),
            exp(LocalDate.of(2025, 8, 5), 3000, cat = 2),
            exp(LocalDate.of(2025, 9, 5), 99999), // mes actual: no cuenta
            exp(LocalDate.of(2025, 5, 5), 99999), // demasiado antiguo
            exp(LocalDate.of(2025, 7, 5), 50000, cat = 1, income = true),
        )
        val avg = Budgets.monthlyAverageByCategory(list, YearMonth.of(2025, 9))
        assertEquals(4000L, avg[1])
        assertEquals(1000L, avg[2])
    }

    @Test
    fun `media con un solo mes de datos`() {
        val list = listOf(exp(LocalDate.of(2025, 8, 5), 30000))
        assertEquals(30000L, Budgets.monthlyAverageByCategory(list, YearMonth.of(2025, 9))[1])
    }

    @Test
    fun `meses cumplidos`() {
        val list = listOf(
            exp(LocalDate.of(2025, 7, 5), 9000),
            exp(LocalDate.of(2025, 8, 5), 3000),
            exp(LocalDate.of(2025, 8, 6), 100, cat = 2),
        )
        val c = Budgets.compliance(list, mapOf(1L to 5000L, 2L to 50L), YearMonth.of(2025, 9))
        assertEquals(1 to 2, c[1]) // julio se pasó, agosto no
        assertEquals(1 to 2, c[2]) // julio sin gasto = cumplido; agosto se pasó
    }

    @Test
    fun `sugerencia redonda`() {
        assertEquals(4500L, Budgets.roundSuggestion(4123))
        assertEquals(13000L, Budgets.roundSuggestion(12001))
        assertEquals(500L, Budgets.roundSuggestion(1))
    }
}
