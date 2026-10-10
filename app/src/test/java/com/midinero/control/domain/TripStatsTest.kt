package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TripStatsTest {
    private val start = LocalDate.of(2025, 8, 10)
    private val trip = Trip(id = 1, name = "Roma", startDay = start.toEpochDay(), endDay = start.plusDays(9).toEpochDay(), budgetCents = 100_000)

    private fun exp(cents: Long, cat: Long?, income: Boolean = false) =
        Expense(amountCents = cents, merchant = "x", categoryId = cat, timestamp = 0, isIncome = income, tripId = 1)

    @Test
    fun `estado segun las fechas`() {
        assertEquals(TripStatus.UPCOMING, TripStats.status(trip, start.minusDays(1)))
        assertEquals(TripStatus.ACTIVE, TripStats.status(trip, start))
        assertEquals(TripStatus.ACTIVE, TripStats.status(trip, start.plusDays(9)))
        assertEquals(TripStatus.FINISHED, TripStats.status(trip, start.plusDays(10)))
        assertEquals(TripStatus.ACTIVE, TripStats.status(trip.copy(endDay = null), start.plusYears(1)))
    }

    @Test
    fun `resumen en curso con proyeccion`() {
        val s = TripStats.summarize(trip, listOf(exp(20_000, 1), exp(10_000, 2), exp(5_000, 1), exp(99_999, null, income = true)), start.plusDays(4))
        assertEquals(35_000L, s.totalCents)
        assertEquals(3, s.count)
        assertEquals(5, s.days)
        assertEquals(7_000L, s.perDayCents)
        assertEquals(65_000L, s.remainingCents)
        assertEquals(70_000L, s.projectedCents)
        assertEquals(listOf<Pair<Long?, Long>>(1L to 25_000L, 2L to 10_000L), s.byCategory)
    }

    @Test
    fun `terminado no proyecta y cuenta todos los dias`() {
        val s = TripStats.summarize(trip, listOf(exp(50_000, 1)), start.plusDays(30))
        assertEquals(10, s.days)
        assertNull(s.projectedCents)
        assertEquals(5_000L, s.perDayCents)
    }

    @Test
    fun `niveles de aviso`() {
        assertNull(TripStats.alertLevel(79_999, 100_000))
        assertEquals(80, TripStats.alertLevel(80_000, 100_000))
        assertEquals(100, TripStats.alertLevel(100_000, 100_000))
        assertNull(TripStats.alertLevel(10, 0))
    }
}
