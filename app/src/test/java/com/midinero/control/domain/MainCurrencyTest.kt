package com.midinero.control.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La moneda principal (euros por defecto, dólares o pesos mexicanos). */
class MainCurrencyTest {
    @After
    fun reset() {
        Currencies.base = Currencies.EUR
    }

    @Test
    fun pesosMexicanos() {
        Currencies.base = Currencies.MXN
        // «$» son pesos: se apunta tal cual, sin convertir
        val p = PaymentParser.parse("Google Wallet", "Pagaste $250.00 en OXXO")!!
        assertEquals(25000L, p.amountCents)
        assertEquals(Currencies.MXN, p.currency)
        // Un pago en dólares se reconoce como moneda extranjera
        val usd = PaymentParser.parse("Google Wallet", "US$12.50 at Starbucks")!!
        assertEquals(Currencies.USD, usd.currency)
        assertEquals(1250L, usd.amountCents)
        assertTrue(Currencies.approxRate(Currencies.USD) > 10)
        assertTrue(formatCents(25000).contains("250"))
    }

    @Test
    fun dolares() {
        Currencies.base = Currencies.USD
        val p = PaymentParser.parse("Starbucks", "$4.75 with Visa ••1234")!!
        assertEquals(475L, p.amountCents)
        assertEquals(Currencies.USD, p.currency)
        // Un pago en euros es extranjero
        val eur = PaymentParser.parse("Google Wallet", "Has pagado 10,00 € en Zara")!!
        assertEquals(Currencies.EUR, eur.currency)
        assertEquals(Currencies.approxRate(Currencies.EUR), 1 / 0.92, 0.001)
    }

    @Test
    fun eurosPorDefecto() {
        val p = PaymentParser.parse("Mercadona", "12,50 €")!!
        assertEquals(Currencies.EUR, p.currency)
        assertEquals(1250L, p.amountCents)
        assertEquals(Currencies.USD, PaymentParser.parse("Google Wallet", "$12.50 at Starbucks")!!.currency)
        assertTrue(Currencies.all.none { it.code == Currencies.EUR })
    }
}
