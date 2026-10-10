package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Formatos habituales de los avisos de bancos españoles (título, texto) → importe y comercio. */
class BankFormatsTest {

    private fun check(title: String?, text: String, cents: Long, merchant: String) {
        val p = PaymentParser.parse(title, text)
        requireNotNull(p) { "No se ha entendido: $title / $text" }
        assertEquals("$title / $text", cents, p.amountCents)
        assertEquals("$title / $text", merchant, p.merchant)
    }

    @Test fun bbva() = check("Compra con tarjeta", "Has realizado una compra de 12,50 € en MERCADONA con tu tarjeta ••1234", 1250, "MERCADONA")
    @Test fun santander() = check("Santander", "Compra con tu tarjeta *1234 por 23,40 EUR en LIDL MADRID", 2340, "LIDL MADRID")
    @Test fun caixabank() = check("CaixaBank", "Compra de 8,90€ en STARBUCKS GRAN VIA con tu tarjeta acabada en 5678", 890, "STARBUCKS GRAN VIA")
    @Test fun ing() = check("ING", "Pago con tarjeta: 45,00 € en REPSOL E.S. 1234", 4500, "REPSOL E.S. 1234")
    @Test fun openbank() = check("Openbank", "Compra por importe de 19,99 EUR en AMAZON.ES", 1999, "AMAZON.ES")
    @Test fun revolut() = check("💳 Glovo", "Has pagado 15,30 €", 1530, "💳 Glovo")
    @Test fun sabadell() = check("Banco Sabadell", "Operación con tarjeta: 32,00 € en ZARA", 3200, "ZARA")
    @Test fun bankinter() = check("Bankinter", "Se ha realizado un pago de 6,75 € en BURGER KING con tu tarjeta", 675, "BURGER KING")
    @Test fun miles() = check("BBVA", "Has realizado una compra de 1.234,56 € en EL CORTE INGLES", 123456, "EL CORTE INGLES")
    @Test fun recibo() = check("Recibo cargado", "Se ha cargado un recibo de 45,20 € de IBERDROLA CLIENTES", 4520, "IBERDROLA CLIENTES")
    @Test fun paypal() = check("PayPal", "Has enviado un pago de 9,99 € a Spotify AB", 999, "Spotify AB")

    @Test fun puntoFinal() = check("BBVA", "Compra de 5,00 € en MERCADONA. Tarjeta ••1234", 500, "MERCADONA")
    @Test fun ingresoNoEsGasto() = assertNull(PaymentParser.parse("BBVA", "Has recibido una transferencia de 300,00 €"))
    @Test fun devolucionResta() {
        val p = PaymentParser.parse("Santander", "Devolución de 12,00 EUR en ZARA")!!
        org.junit.Assert.assertTrue(p.refund)
        org.junit.Assert.assertEquals(1200L, p.amountCents)
    }
}
