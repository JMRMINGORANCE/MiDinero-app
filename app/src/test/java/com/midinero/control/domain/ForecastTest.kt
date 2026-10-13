package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.Frequency
import com.midinero.control.data.RecurringExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ForecastTest {
    private val today = LocalDate.of(2026, 10, 10)
    private val month = YearMonth.of(2026, 10)
    private fun at(day: Int) = LocalDate.of(2026, 10, day).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun avisaDeLoQueSobraYDelPresupuesto() {
        val expenses = listOf(
            Expense(amountCents = 31_000, merchant = "Super", timestamp = at(5)),
            Expense(amountCents = 50_000, merchant = "Nómina", timestamp = at(1), isIncome = true),
        )
        val salary = RecurringExpense(name = "Extra", amountCents = 100_000, dayOfMonth = 25, nextDueEpochDay = LocalDate.of(2026, 10, 25).toEpochDay(), isIncome = true, frequency = Frequency.MONTHLY)
        val stats = Stats.compute(month, expenses, today, recurringCharges = listOf(salary))
        val f = Forecast.of(stats, 80_000, listOf(salary), today)!!
        // 310 € en 10 días → unos 961 € en 31 días
        assertEquals(stats.projectionCents, f.projectedSpendCents)
        assertEquals(f.projectedSpendCents - 80_000, f.vsBudgetCents)
        assertEquals(100_000L, f.pendingIncomeCents)
        assertEquals(150_000L - f.projectedSpendCents, f.balanceCents)
        assertEquals(22, f.daysLeft)
    }

    @Test
    fun sinGastosOSinIngresos() {
        assertNull(Forecast.of(Stats.compute(month, emptyList(), today), 80_000, emptyList(), today))
        val stats = Stats.compute(month, listOf(Expense(amountCents = 1_000, merchant = "Bar", timestamp = at(2))), today)
        val f = Forecast.of(stats, 0, emptyList(), today)!!
        assertNull(f.budgetCents)
        assertNull(f.balanceCents)
    }
}
