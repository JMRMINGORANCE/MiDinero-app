package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {

    private fun exp(
        date: LocalDate,
        cents: Long,
        merchant: String = "X",
        cat: Long? = 1,
        src: ExpenseSource = ExpenseSource.MANUAL,
        income: Boolean = false,
    ) = Expense(
        amountCents = cents,
        merchant = merchant,
        categoryId = cat,
        timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        source = src,
        isIncome = income,
    )

    private val sept = YearMonth.of(2025, 9)

    @Test
    fun `totales, comparativa y tendencia`() {
        val list = listOf(
            exp(LocalDate.of(2025, 9, 1), 1000, cat = 1),
            exp(LocalDate.of(2025, 9, 2), 3000, cat = 2),
            exp(LocalDate.of(2025, 8, 15), 2000),
            exp(LocalDate.of(2025, 4, 15), 500),
        )
        val s = Stats.compute(sept, list, today = LocalDate.of(2025, 10, 3))
        assertEquals(4000L, s.totalCents)
        assertEquals(2000L, s.previousTotalCents)
        assertEquals(100, s.changeVsPrevious)
        assertEquals(listOf(2L to 3000L, 1L to 1000L), s.byCategory)
        assertEquals(6, s.trend.size)
        assertEquals(YearMonth.of(2025, 4) to 500L, s.trend.first())
        assertEquals(sept to 4000L, s.trend.last())
        // Mes cerrado: media sobre 30 días y sin previsión
        assertEquals(4000L / 30, s.dailyAverageCents)
        assertNull(s.projectionCents)
    }

    @Test
    fun `prevision suma los cargos fijos que faltan por llegar`() {
        val list = listOf(exp(LocalDate.of(2025, 9, 5), 3000))
        val gym = com.midinero.control.data.RecurringExpense(
            name = "Gimnasio", amountCents = 4000, dayOfMonth = 20,
            nextDueEpochDay = LocalDate.of(2025, 9, 20).toEpochDay(),
        )
        val nextMonth = gym.copy(name = "Seguro", nextDueEpochDay = LocalDate.of(2025, 10, 2).toEpochDay())
        val paused = gym.copy(name = "Pausado", active = false)
        val salary = gym.copy(name = "Nómina", isIncome = true)
        val s = Stats.compute(sept, list, today = LocalDate.of(2025, 9, 10), recurringCharges = listOf(gym, nextMonth, paused, salary))
        assertEquals(4000L, s.pendingRecurringCents)
        // 30 € en 10 días -> 90 € en 30 días, más los 40 € del gimnasio del día 20
        assertEquals(9000L + 4000L, s.projectionCents)
        // Un mes ya pasado no tiene nada pendiente
        assertEquals(0L, Stats.compute(sept, list, today = LocalDate.of(2025, 10, 5), recurringCharges = listOf(gym)).pendingRecurringCents)
    }

    @Test
    fun `prevision cuenta lo fijo una sola vez`() {
        val list = listOf(
            exp(LocalDate.of(2025, 9, 1), 50000, src = ExpenseSource.RECURRING),
            exp(LocalDate.of(2025, 9, 5), 3000),
        )
        val s = Stats.compute(sept, list, today = LocalDate.of(2025, 9, 10))
        // 500 € fijos + 30 € en 10 días -> 90 € en 30 días
        assertEquals(50000L + 9000L, s.projectionCents)
        assertEquals(53000L / 10, s.dailyAverageCents)
    }

    @Test
    fun `comercios agrupados y dia de la semana`() {
        val list = listOf(
            exp(LocalDate.of(2025, 9, 1), 1000, "MERCADONA 1234"), // lunes
            exp(LocalDate.of(2025, 9, 8), 1500, "Mercadona 999"), // lunes
            exp(LocalDate.of(2025, 9, 3), 700, "Glovo"), // miércoles
        )
        val s = Stats.compute(sept, list, today = LocalDate.of(2025, 9, 30))
        assertEquals(2500L, s.topMerchants.first().second)
        assertEquals(2500L, s.byWeekday[0])
        assertEquals(700L, s.byWeekday[2])
        assertEquals(1500L, s.biggest!!.amountCents)
        assertNull(Stats.compute(sept, emptyList(), LocalDate.of(2025, 9, 30)).changeVsPrevious)
    }

    @Test
    fun `ingresos aparte y tasa de ahorro`() {
        val list = listOf(
            exp(LocalDate.of(2025, 9, 1), 150000, "Nómina", cat = 99, income = true),
            exp(LocalDate.of(2025, 9, 3), 60000),
            exp(LocalDate.of(2025, 8, 1), 140000, "Nómina", cat = 99, income = true),
        )
        val s = Stats.compute(sept, list, today = LocalDate.of(2025, 9, 30))
        assertEquals(60000L, s.totalCents)
        assertEquals(1, s.count)
        assertEquals(150000L, s.incomeCents)
        assertEquals(90000L, s.balanceCents)
        assertEquals(60, s.savingsRate)
        assertEquals(YearMonth.of(2025, 8) to 140000L, s.incomeTrend[4])
        assertEquals(listOf(1L to 60000L), s.byCategory)
        assertNull(Stats.compute(sept, list.drop(1).take(1), LocalDate.of(2025, 9, 30)).savingsRate)
    }
}
