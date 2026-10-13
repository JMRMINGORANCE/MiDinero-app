package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Avisos de las tarjetas cripto (Bleap, Bybit, Bybit EU), en español y en inglés. */
class CryptoCardParserTest {
    private fun check(title: String, body: String, cents: Long, merchant: String) {
        val p = PaymentParser.parse(title, body)
        assertNotNull("$title | $body", p)
        p!!
        assertEquals("$title | $body", cents, p.amountCents)
        assertEquals("$title | $body", merchant, p.merchant)
    }

    @Test
    fun bybit() {
        check("Bybit Card", "Has pagado 25,40 EUR en MERCADONA", 2540, "MERCADONA")
        check("Bybit Card", "You spent 25.40 EUR at AMAZON", 2540, "AMAZON")
        check("Card transaction", "A payment of 12.30 EUR at Lidl was successful", 1230, "Lidl")
    }

    @Test
    fun bleap() {
        check("Bleap", "You paid €12.30 at Mercadona", 1230, "Mercadona")
        check("Bleap", "Pago de 8,90 € en Starbucks", 890, "Starbucks")
        check("Bleap", "Tu pago de 15,00 € en Zara ha sido aprobado", 1500, "Zara")
    }
}
