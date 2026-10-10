package com.midinero.control.domain

import com.midinero.control.data.Expense
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LangTest {
    @Before fun english() { Lang.english = true }
    @After fun spanish() { Lang.english = false }

    @Test
    fun `elige idioma segun ajuste y sistema`() {
        assertFalse(Lang.resolve("system", "es"))
        assertFalse(Lang.resolve("system", "ca"))
        assertTrue(Lang.resolve("system", "en"))
        assertTrue(Lang.resolve("system", "de"))
        assertFalse(Lang.resolve("es", "en"))
        assertTrue(Lang.resolve("en", "es"))
    }

    @Test
    fun `importes y fechas en ingles`() {
        assertEquals("€12.50", formatCents(1250))
        assertEquals("12.50", centsToInput(1250))
        assertEquals("September 2026", YearMonth.of(2026, 9).label())
        assertEquals("Friday 25 September", LocalDate.of(2026, 9, 25).dayLabel())
        Lang.english = false
        assertEquals("12,50 €", formatCents(1250).replace(' ', ' '))
        assertEquals("Septiembre 2026", YearMonth.of(2026, 9).label())
    }

    @Test
    fun `frase rapida en ingles`() {
        val today = LocalDate.of(2026, 9, 25)
        val e = QuickEntryParser.parse("12.50 coffee yesterday", today)!!
        assertEquals(1250L, e.amountCents)
        assertEquals("Coffee", e.merchant)
        assertEquals(today.minusDays(1), e.date)
        assertTrue(QuickEntryParser.parse("+1500 salary", today)!!.isIncome)
    }

    @Test
    fun `resumen y csv en ingles`() {
        val text = MonthReport.text("September 2026", 90_000, 150_000, 100_000, listOf("Groceries" to 30_000L), 120_000)
        assertTrue(text.startsWith("My spending for September 2026"))
        assertTrue(text.contains("10 % less than the previous month"))
        val csv = CsvExport.build(listOf(Expense(amountCents = 123456, merchant = "Shop, Ltd", timestamp = 0)), emptyList())
        val lines = csv.removePrefix(CsvExport.BOM).trimEnd().split("\r\n")
        assertTrue(lines[0].startsWith("Date,Time,Type,Amount,Merchant"))
        assertTrue(lines[1].contains("Expense,1234.56,\"Shop, Ltd\""))
    }

    @Test
    fun `categorias por defecto en los dos idiomas`() {
        assertEquals("Groceries", CategoryNames.localized("Supermercado"))
        assertTrue(CategoryNames.matches("Groceries", "Supermercado"))
        assertTrue(CategoryNames.matches("Supermercado", "Supermercado"))
        assertFalse(CategoryNames.matches("Leisure", "Supermercado"))
        assertEquals("Save", tr("Guardar", "Save"))
        assertEquals("3 expenses", plural(3, "gasto", "gastos", "expense", "expenses"))
    }
}
