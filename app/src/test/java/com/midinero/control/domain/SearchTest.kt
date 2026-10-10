package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val super_ = Category(id = 1, name = "Supermercado", emoji = "🛒", colorHex = "#000")
    private fun e(cents: Long, merchant: String, note: String = "") = Expense(amountCents = cents, merchant = merchant, note = note, timestamp = 0)

    @Test
    fun `texto sin tildes ni mayusculas y en nota o categoria`() {
        val q = SearchQuery.parse("Cafeteria")
        assertTrue(q.matches(e(250, "Cafetería Luna"), null))
        assertTrue(SearchQuery.parse("cumple").matches(e(250, "Bar", "cumpleaños de Ana"), null))
        assertTrue(SearchQuery.parse("super").matches(e(250, "Mercadona"), super_))
        assertFalse(SearchQuery.parse("super").matches(e(250, "Mercadona"), null))
    }

    @Test
    fun `importes exactos y rangos`() {
        assertEquals(SearchQuery(emptyList(), 1250, 1250), SearchQuery.parse("12,50"))
        assertEquals(SearchQuery(emptyList(), 5000, null), SearchQuery.parse("> 50"))
        assertEquals(SearchQuery(emptyList(), null, 1000), SearchQuery.parse("<10"))
        assertEquals(SearchQuery(emptyList(), 2000, 4000), SearchQuery.parse("40-20"))
        val q = SearchQuery.parse("amazon >30")
        assertTrue(q.matches(e(3500, "AMAZON ES"), null))
        assertFalse(q.matches(e(2500, "AMAZON ES"), null))
        assertFalse(q.matches(e(3500, "Zara"), null))
    }

    @Test
    fun `consulta vacia`() {
        assertTrue(SearchQuery.parse("   ").isEmpty)
    }

    @Test
    fun `resumen del mes para compartir`() {
        val text = MonthReport.text("marzo 2025", 90_000, 150_000, 100_000, listOf("Supermercado" to 30_000L), 120_000)
        assertTrue(text.startsWith("Mis gastos de marzo 2025"))
        assertTrue(text.contains("de presupuesto"))
        assertTrue(text.contains("Ahorro"))
        assertTrue(text.contains("Un 10 % menos que el mes anterior"))
        assertTrue(text.contains("• Supermercado"))
    }

    private fun on(date: java.time.LocalDate, cents: Long = 1000, merchant: String = "Bar") =
        Expense(amountCents = cents, merchant = merchant, timestamp = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())

    @Test
    fun `fechas hoy ayer meses y dias`() {
        val today = java.time.LocalDate.of(2026, 10, 4)
        fun q(t: String) = SearchQuery.parse(t, today)
        assertTrue(q("hoy").matches(on(today), null))
        assertFalse(q("hoy").matches(on(today.minusDays(1)), null))
        assertTrue(q("ayer").matches(on(today.minusDays(1)), null))
        // Mes sin año: el último que ha llegado (diciembre es del año pasado)
        assertTrue(q("septiembre").matches(on(java.time.LocalDate.of(2026, 9, 15)), null))
        assertTrue(q("diciembre").matches(on(java.time.LocalDate.of(2025, 12, 1)), null))
        assertFalse(q("diciembre").matches(on(java.time.LocalDate.of(2026, 12, 1)), null))
        assertTrue(q("sept 2025").matches(on(java.time.LocalDate.of(2025, 9, 3)), null))
        assertTrue(q("12/09").matches(on(java.time.LocalDate.of(2026, 9, 12)), null))
        assertTrue(q("12/09/2024").matches(on(java.time.LocalDate.of(2024, 9, 12)), null))
        assertTrue(q("mes pasado").matches(on(java.time.LocalDate.of(2026, 9, 30)), null))
        assertFalse(q("mes pasado").matches(on(today), null))
        assertTrue(q("esta semana").matches(on(java.time.LocalDate.of(2026, 9, 28)), null))
    }

    @Test
    fun `fechas combinadas con texto e importe`() {
        val today = java.time.LocalDate.of(2026, 10, 4)
        val q = SearchQuery.parse("amazon >30 septiembre", today)
        assertTrue(q.matches(on(java.time.LocalDate.of(2026, 9, 2), 4500, "Amazon"), null))
        assertFalse(q.matches(on(java.time.LocalDate.of(2026, 9, 2), 2000, "Amazon"), null))
        assertFalse(q.matches(on(java.time.LocalDate.of(2026, 8, 2), 4500, "Amazon"), null))
        assertFalse(q.matches(on(java.time.LocalDate.of(2026, 9, 2), 4500, "Zara"), null))
        // «Café de Indias» sigue encontrándose aunque «de» se ignore
        assertTrue(SearchQuery.parse("cafe de indias", today).matches(on(today, merchant = "Café de Indias"), null))
    }
}
