package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.data.Policy
import com.midinero.control.data.PolicyPrice
import com.midinero.control.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HouseholdTest {
    private val fuelCat = Category(id = 1, name = "Gasolina", emoji = "⛽", colorHex = "#000000", kind = "FUEL")
    private val lightCat = Category(id = 2, name = "Luz", emoji = "💡", colorHex = "#000000", kind = "ELECTRICITY")
    private val carCat = Category(id = 3, name = "Taller", emoji = "🔧", colorHex = "#000000", kind = "CAR")
    private val other = Category(id = 4, name = "Ocio", emoji = "🎉", colorHex = "#000000")
    private val categories = listOf(fuelCat, lightCat, carCat, other)

    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun expense(id: Long, cents: Long, cat: Long, date: LocalDate, d: ExpenseDetails = ExpenseDetails.EMPTY) =
        Expense(id = id, amountCents = cents, merchant = "x", categoryId = cat, timestamp = at(date), details = d.encode())

    @Test
    fun detallesSeGuardanYSeLeen() {
        val d = ExpenseDetails(liters = 40.5, tripKm = 620.0, odometerKm = 123456, vehicleId = 2, services = setOf(ServiceType.TYRES, ServiceType.OIL))
        val text = d.encode()
        assertEquals("l=40.5;km=620;odo=123456;car=2;svc=OIL,TYRES", text)
        assertEquals(d, ExpenseDetails.parse(text))
        assertNull(ExpenseDetails.EMPTY.encode())
        assertEquals(ExpenseDetails.EMPTY, ExpenseDetails.parse(null))
        // Lo que no se entiende se ignora sin romper nada
        assertEquals(ExpenseDetails(m3 = 12.0), ExpenseDetails.parse("m3=12;raro;l=abc;svc=NADA"))
    }

    @Test
    fun cantidadesEscritasAMano() {
        assertEquals(40.5, parseQuantity("40,5")!!, 0.0001)
        assertEquals(40.5, parseQuantity("40.5")!!, 0.0001)
        assertEquals(1234.0, parseQuantity("1.234")!!, 0.0001)
        assertEquals(1234.5, parseQuantity("1.234,5")!!, 0.0001)
        assertNull(parseQuantity(""))
        assertNull(parseQuantity("-3"))
        assertNull(parseQuantity("abc"))
    }

    @Test
    fun consumoDeCombustible() {
        val d0 = LocalDate.of(2025, 1, 1)
        val expenses = listOf(
            expense(1, 6000, 1, d0, ExpenseDetails(liters = 40.0, tripKm = 600.0)),
            expense(2, 3000, 1, d0.plusDays(10), ExpenseDetails(liters = 20.0)),
            expense(3, 1500, 4, d0.plusDays(11)),
        )
        val entries = Meters.entries(TrackerKind.FUEL, categories, expenses)
        assertEquals(2, entries.size)
        val s = Meters.fuel(entries)
        assertEquals(9000L, s.totalCents)
        assertEquals(60.0, s.liters, 0.001)
        assertEquals(150.0, s.pricePerLiter!!, 0.001) // 1,50 €/l
        assertEquals(6.667, s.litersPer100!!, 0.001) // solo el que tiene km
        assertEquals(1000.0, s.costPer100!!, 0.001) // 10 € cada 100 km
        assertEquals(6.667, s.fills.first().litersPer100!!, 0.001)
        assertNull(s.fills.last().litersPer100)
    }

    @Test
    fun kmSacadosDelCuentakilometros() {
        val d0 = LocalDate.of(2025, 1, 1)
        val entries = Meters.entries(
            TrackerKind.FUEL, categories,
            listOf(
                expense(1, 6000, 1, d0, ExpenseDetails(liters = 40.0, odometerKm = 10000)),
                expense(2, 6000, 1, d0.plusDays(9), ExpenseDetails(liters = 40.0, odometerKm = 10650)),
            ),
        )
        val fills = Meters.fuel(entries).fills
        assertNull(fills[0].km)
        assertEquals(650.0, fills[1].km!!, 0.001)
    }

    @Test
    fun avisoDeConsumoAlto() {
        fun fill(l100: Double) = Meters.fill(
            MeterEntry(Expense(amountCents = 5000, merchant = "", timestamp = 0), LocalDate.now(), ExpenseDetails(liters = l100 * 5, tripKm = 500.0))
        )
        val normal = listOf(6.0, 6.2, 5.9, 6.1).map(::fill)
        assertNull(Meters.consumptionAlert(normal))
        val high = listOf(6.0, 6.2, 5.9, 7.3).map(::fill)
        assertNotNull(Meters.consumptionAlert(high))
        assertNull(Meters.consumptionAlert(listOf(6.0, 9.0).map(::fill)))
    }

    @Test
    fun luzPorMesesYResumen() {
        val expenses = listOf(
            expense(1, 8000, 2, LocalDate.of(2025, 1, 15), ExpenseDetails(kwhPeak = 100.0, kwhFlat = 120.0, kwhValley = 180.0, kwhExported = 50.0)),
            expense(2, 6000, 2, LocalDate.of(2025, 2, 15), ExpenseDetails(kwhPeak = 80.0, kwhFlat = 100.0, kwhValley = 120.0)),
        )
        val entries = Meters.entries(TrackerKind.ELECTRICITY, categories, expenses)
        val months = Meters.monthly(entries)
        assertEquals(listOf(YearMonth.of(2025, 1), YearMonth.of(2025, 2)), months.map { it.month })
        assertEquals(400.0, months[0].kwhConsumed, 0.001)
        assertEquals(50.0, months[0].kwhExported, 0.001)
        assertEquals(20.0, Meters.unitPrice(TrackerKind.ELECTRICITY, entries)!!, 0.001) // 14000 cts / 700 kWh
        val text = Meters.electricitySummary(months)
        assertTrue(text, text.contains("punta 180 kWh"))
        assertTrue(text, text.contains("excedentes 50 kWh"))
    }

    @Test
    fun compararTramos() {
        val expenses = listOf(
            expense(1, 1000, 2, LocalDate.of(2024, 3, 1)),
            expense(2, 1500, 2, LocalDate.of(2025, 3, 1)),
            expense(3, 500, 4, LocalDate.of(2025, 6, 1)),
            Expense(id = 4, amountCents = 99999, merchant = "Nómina", isIncome = true, timestamp = at(LocalDate.of(2025, 6, 1))),
        )
        val a = DateRange.year(2025)
        val b = a.previousYear()
        assertEquals(DateRange.year(2024), b)
        val rows = Compare.byCategory(expenses, a, b)
        assertEquals(listOf(2L, 4L), rows.map { it.categoryId })
        assertEquals(1500L, rows[0].aCents)
        assertEquals(1000L, rows[0].bCents)
        assertEquals(0.5, rows[0].change!!, 0.0001)
        assertNull(rows[1].change)
        assertEquals(2000L, Compare.total(expenses, a))
        assertEquals(1500L, Compare.total(expenses, a, setOf(2L)))
        val monthly = Compare.monthly(expenses, a, setOf(2L))
        assertEquals(12, monthly.size)
        assertEquals(1500L, monthly[2].second)
        assertEquals(DateRange(LocalDate.of(2024, 12, 22), LocalDate.of(2024, 12, 31)), DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10)).previousSpan())
    }

    @Test
    fun avisosDelCocheYSeguros() {
        val car = Vehicle(id = 1, name = "León", oilReminderMonths = 11, itvDueEpochDay = LocalDate.of(2025, 9, 30).toEpochDay(), itvNoticeDays = 30)
        val carEntries = Meters.entries(
            TrackerKind.CAR, categories,
            listOf(
                expense(1, 9000, 3, LocalDate.of(2024, 1, 10), ExpenseDetails(vehicleId = 1, odometerKm = 100000, services = setOf(ServiceType.OIL))),
                expense(2, 12000, 3, LocalDate.of(2025, 1, 20), ExpenseDetails(vehicleId = 1, odometerKm = 115000, services = setOf(ServiceType.OIL, ServiceType.TYRES))),
            ),
        )
        val policy = Policy(id = 5, name = "Seguro León", vehicleId = 1, renewalEpochDay = LocalDate.of(2025, 11, 15).toEpochDay())
        val reminders = Garage.reminders(listOf(car), carEntries, listOf(policy))
        assertEquals(listOf(ReminderKind.ITV, ReminderKind.POLICY, ReminderKind.OIL), reminders.map { it.kind })
        val oil = reminders.first { it.kind == ReminderKind.OIL }
        assertEquals(LocalDate.of(2025, 12, 20), oil.dueDate) // 11 meses después del último cambio
        val itv = reminders.first { it.kind == ReminderKind.ITV }
        assertEquals(LocalDate.of(2025, 8, 31), itv.notifyFrom)
        val pol = reminders.first { it.kind == ReminderKind.POLICY }
        assertEquals(LocalDate.of(2025, 10, 1), pol.notifyFrom) // 45 días antes
        assertEquals(LocalDate.of(2025, 10, 16), pol.cancelBy) // 30 días antes
        assertTrue(pol.isActive(LocalDate.of(2025, 10, 2)))
        assertTrue(pol.message(LocalDate.of(2025, 10, 2)).contains("negocia"))

        val prices = listOf(
            PolicyPrice(id = 1, policyId = 5, startEpochDay = LocalDate.of(2024, 11, 15).toEpochDay(), amountCents = 40000),
            PolicyPrice(id = 2, policyId = 5, startEpochDay = LocalDate.of(2025, 11, 15).toEpochDay(), amountCents = 46000),
        )
        assertEquals(0.15, Garage.lastChange(Garage.priceHistory(5, prices))!!, 0.0001)
        val s = Garage.summary(car, emptyList(), carEntries, listOf(policy), prices, DateRange.year(2025))
        assertEquals(12000L, s.servicesCents)
        assertEquals(46000L, s.insuranceCents)
        assertEquals(115000L, s.lastOdometer)
    }
}
