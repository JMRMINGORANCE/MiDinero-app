package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.DefaultCategories
import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import com.midinero.control.data.RecurringExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DetectorsTest {
    private fun exp(date: LocalDate, cents: Long, merchant: String, cat: Long? = 1, src: ExpenseSource = ExpenseSource.GOOGLE_PAY) = Expense(
        amountCents = cents, merchant = merchant, categoryId = cat, source = src,
        timestamp = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    private val today = LocalDate.of(2025, 9, 20)

    @Test
    fun `detecta pagos mensuales parecidos`() {
        val list = listOf(
            exp(LocalDate.of(2025, 6, 5), 1299, "NETFLIX.COM"),
            exp(LocalDate.of(2025, 7, 5), 1299, "NETFLIX.COM"),
            exp(LocalDate.of(2025, 8, 5), 1299, "Netflix.com"),
            exp(LocalDate.of(2025, 9, 5), 1299, "NETFLIX.COM"),
            // Supermercado: importes muy distintos
            exp(LocalDate.of(2025, 7, 1), 4000, "Mercadona"), exp(LocalDate.of(2025, 8, 1), 9000, "Mercadona"),
            exp(LocalDate.of(2025, 9, 1), 2500, "Mercadona"),
            // Semanal: no es mensual
            exp(LocalDate.of(2025, 9, 1), 500, "Bar"), exp(LocalDate.of(2025, 9, 8), 500, "Bar"), exp(LocalDate.of(2025, 9, 15), 500, "Bar"),
        )
        val found = SubscriptionDetector.detect(list, emptyList(), today)
        assertEquals(1, found.size)
        assertEquals(1299L, found.first().amountCents)
        assertEquals(5, found.first().dayOfMonth)
        assertEquals(LocalDate.of(2025, 10, 5), found.first().nextDate)
    }

    @Test
    fun `ignora lo que ya es fijo o se ha cancelado`() {
        val list = listOf(5, 6, 7).map { exp(LocalDate.of(2025, it, 5), 999, "Spotify") }
        val r = RecurringExpense(name = "Spotify", amountCents = 999, dayOfMonth = 5, nextDueEpochDay = 0)
        assertTrue(SubscriptionDetector.detect(list, listOf(r), today).isEmpty())
        // último pago hace más de 45 días
        assertTrue(SubscriptionDetector.detect(list, emptyList(), today).isEmpty())
    }

    @Test
    fun `consejos de categoria y rachas`() {
        val cats = mapOf(1L to Category(id = 1, name = DefaultCategories.RESTAURANTES, emoji = "", colorHex = "#000"))
        val list = listOf(
            exp(LocalDate.of(2025, 6, 10), 10000, "A"), exp(LocalDate.of(2025, 7, 10), 10000, "A"),
            exp(LocalDate.of(2025, 8, 10), 10000, "A"), exp(LocalDate.of(2025, 9, 10), 20000, "A"),
        )
        val tips = Tips.generate(YearMonth.of(2025, 9), list, cats, emptyList(), today = LocalDate.of(2025, 9, 30))
        assertTrue(tips.any { it.text.contains("100 % más") && it.categoryId == 1L })
        assertTrue(tips.any { it.text.contains("días seguidos sin gastar") })
    }
}
