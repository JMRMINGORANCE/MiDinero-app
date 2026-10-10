package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.Frequency
import com.midinero.control.data.RecurringExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ExploreTest {
    private fun e(date: LocalDate, cents: Long, merchant: String, income: Boolean = false) =
        Expense(amountCents = cents, merchant = merchant, timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), isIncome = income)

    private val d = LocalDate.of(2026, 9, 1)

    @Test
    fun `ranking de comercios agrupa variantes y ordena por gasto`() {
        val list = listOf(
            e(d, 2000, "MERCADONA 1234"), e(d.plusDays(3), 3000, "Mercadona"), e(d.plusDays(5), 4500, "Zara"),
            e(d.plusDays(6), 9999, "Nómina", income = true), e(d.minusDays(5), 10000, "Zara"),
        )
        val r = MerchantRanking.rank(list, d, d.plusDays(29))
        assertEquals(listOf(5000L, 4500L), r.map { it.totalCents })
        assertEquals("Mercadona", r[0].name)
        assertEquals(2, r[0].count)
        assertEquals(2500L, r[0].averageCents)
    }

    @Test
    fun `patron semanal`() {
        // Cuatro semanas: los sábados se gastan 60 €, el resto de días nada
        val from = LocalDate.of(2026, 8, 31) // lunes
        val list = (0 until 28).map { from.plusDays(it.toLong()) }.filter { it.dayOfWeek == DayOfWeek.SATURDAY }.map { e(it, 6000, "Bar") }
        val p = Weekdays.pattern(list, from, from.plusDays(27))!!
        assertEquals(DayOfWeek.SATURDAY, p.busiest)
        assertEquals(6000L, p.averages[DayOfWeek.SATURDAY])
        assertEquals(0L, p.averages[DayOfWeek.MONDAY])
        assertNull(Weekdays.pattern(list, from, from.plusDays(5)))
    }

    @Test
    fun `comparacion anual a estas alturas`() {
        val today = LocalDate.of(2026, 3, 10)
        val list = listOf(e(LocalDate.of(2025, 2, 1), 1000, "A"), e(LocalDate.of(2025, 6, 1), 5000, "B"), e(LocalDate.of(2026, 1, 5), 3000, "C"))
        assertEquals(1000L, YearCompare.previousCents(2026, list, today))
        assertEquals(6000L, YearCompare.currentCents(2025, list, today))
        assertEquals(3000L, YearCompare.currentCents(2026, list, today))
    }

    @Test
    fun `renovaciones anuales`() {
        val today = LocalDate.of(2026, 10, 4)
        fun r(id: Long, f: Frequency, due: LocalDate) = RecurringExpense(id = id, name = "R$id", amountCents = 5000, frequency = f, dayOfMonth = due.dayOfMonth, nextDueEpochDay = due.toEpochDay())
        val all = listOf(r(1, Frequency.YEARLY, today.plusDays(7)), r(2, Frequency.MONTHLY, today.plusDays(7)), r(3, Frequency.YEARLY, today.plusDays(90)), r(4, Frequency.QUARTERLY, today.plusDays(20)))
        assertEquals(listOf(1L, 4L), Renewals.upcoming(all, today).map { it.id })
        assertEquals(listOf(1L), Renewals.dueIn(all, 7, today).map { it.id })
    }

    @Test
    fun `porcentaje con importes ocultos`() {
        assertEquals("64 %", PrivateShare.label(64000, 100000))
        assertNull(PrivateShare.label(100, 0))
    }
}
