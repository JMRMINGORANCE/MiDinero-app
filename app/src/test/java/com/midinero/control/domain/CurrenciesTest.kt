package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrenciesTest {

    @Test
    fun `encuentra importes en otras monedas`() {
        assertEquals("USD" to 1250L, Currencies.findForeignAmount("Contactless payment \$12.50 at Starbucks"))
        assertEquals("USD" to 1250L, Currencies.findForeignAmount("Pagado 12,50 USD en Walmart"))
        assertEquals("GBP" to 300L, Currencies.findForeignAmount("£3 at Pret"))
        assertEquals("CHF" to 450000L, Currencies.findForeignAmount("CHF 4.500,00"))
        assertNull(Currencies.findForeignAmount("12,50 € en Mercadona"))
    }

    @Test
    fun `conversion y formato`() {
        assertEquals(1150L, Currencies.toEurCents(1250, 0.92))
        val s = Currencies.format(1250, "USD")
        assertTrue(s, s.contains("12,50"))
        val yen = Currencies.format(150000, "JPY")
        assertTrue(yen, yen.contains("1500") || yen.contains("1.500"))
    }
}
