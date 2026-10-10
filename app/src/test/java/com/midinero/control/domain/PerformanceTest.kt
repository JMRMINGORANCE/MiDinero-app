package com.midinero.control.domain

import com.midinero.control.data.Expense
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/** Los cálculos de pantalla deben ser rápidos incluso con años de datos (5.000 movimientos). */
class PerformanceTest {

    private val merchants = listOf("Mercadona", "Glovo", "Repsol", "Netflix", "Bar Pepe", "Zara", "Farmacia", "Lidl", "Uber", "Cine")

    private val data: List<Expense> = run {
        val rnd = Random(42)
        val start = LocalDate.of(2023, 1, 1)
        (0 until 5000).map { i ->
            Expense(
                id = i.toLong(),
                amountCents = rnd.nextLong(100, 20000),
                merchant = merchants[rnd.nextInt(merchants.size)] + " " + rnd.nextInt(100),
                categoryId = rnd.nextLong(1, 11),
                timestamp = start.plusDays(rnd.nextLong(0, 1000)).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            )
        }
    }

    private fun timed(block: () -> Unit): Long {
        block() // calentamiento
        val t = System.nanoTime()
        repeat(5) { block() }
        return (System.nanoTime() - t) / 5 / 1_000_000
    }

    @Test
    fun `calculos rapidos con 5000 movimientos`() {
        val month = YearMonth.of(2025, 6)
        val stats = timed { Stats.compute(month, data, LocalDate.of(2025, 6, 15)) }
        val year = timed { SpendingCalendar.year(2025, data) }
        val subs = timed { SubscriptionDetector.detect(data, emptyList(), LocalDate.of(2025, 9, 1)) }
        val insight = timed { Insights.merchant("mercadona", data, month) }
        println("Stats $stats ms · Año $year ms · Suscripciones $subs ms · Comercio $insight ms")
        assertTrue("Stats demasiado lento: $stats ms", stats < 400)
        assertTrue("Resumen anual demasiado lento: $year ms", year < 400)
        assertTrue("Detector demasiado lento: $subs ms", subs < 800)
        assertTrue("Historial de comercio demasiado lento: $insight ms", insight < 400)
    }
}
