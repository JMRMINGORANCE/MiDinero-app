package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickEntryTest {
    private val today = LocalDate.of(2025, 9, 25) // jueves

    @Test
    fun `importe y concepto en cualquier orden`() {
        assertEquals(QuickEntry(1250, "Café", today, false), QuickEntryParser.parse("12,50 café", today))
        assertEquals(QuickEntry(4520, "Mercadona", today, false), QuickEntryParser.parse("mercadona 45,20", today))
        assertEquals(QuickEntry(700, "Parking", today, false), QuickEntryParser.parse("parking 7€", today))
        assertEquals(123456L, QuickEntryParser.parse("1.234,56 sofá", today)!!.amountCents)
        val en = QuickEntryParser.parse("1,234.56 sofa", today)!!
        assertEquals(123456L, en.amountCents)
        assertEquals("Sofa", en.merchant)
    }

    @Test
    fun `fechas relativas`() {
        assertEquals(today.minusDays(1), QuickEntryParser.parse("taxi 18 ayer", today)!!.date)
        assertEquals("Taxi", QuickEntryParser.parse("taxi 18 ayer", today)!!.merchant)
        assertEquals(today.minusDays(2), QuickEntryParser.parse("anteayer cine 9", today)!!.date)
        assertEquals(LocalDate.of(2025, 9, 19), QuickEntryParser.parse("cena 60 el viernes", today)!!.date)
        assertEquals(today, QuickEntryParser.parse("pan 1,20 el jueves", today)!!.date)
    }

    @Test
    fun `ingresos`() {
        val a = QuickEntryParser.parse("+1200 nómina", today)!!
        assertTrue(a.isIncome)
        assertEquals(120000L, a.amountCents)
        assertTrue(QuickEntryParser.parse("cobro alquiler 500", today)!!.isIncome)
    }

    @Test
    fun `sin importe no vale`() {
        assertNull(QuickEntryParser.parse("café", today))
        assertNull(QuickEntryParser.parse("", today))
        assertEquals("Gasto", QuickEntryParser.parse("15", today)!!.merchant)
    }
}
