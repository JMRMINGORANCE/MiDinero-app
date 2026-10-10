package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    private fun parse(title: String?, text: String?) = PaymentParser.parse(title, text)

    @Test
    fun `titulo comercio y texto con importe`() {
        val p = parse("Mercadona", "12,50 € con Visa ••1234")!!
        assertEquals(1250L, p.amountCents)
        assertEquals("Mercadona", p.merchant)
    }

    @Test
    fun `frase has pagado en comercio`() {
        val p = parse("Google Pay", "Has pagado 8,90 € en GLOVO con tu tarjeta")!!
        assertEquals(890L, p.amountCents)
        assertEquals("GLOVO", p.merchant)
    }

    @Test
    fun `importe con miles`() {
        assertEquals(123456L, parse("IKEA", "1.234,56 €")!!.amountCents)
        assertEquals(123456L, parse("IKEA", "1 234,56 €")!!.amountCents)
    }

    @Test
    fun `importe en formato ingles y EUR delante`() {
        assertEquals(1250L, parse("Shell", "EUR 12.50")!!.amountCents)
        assertEquals(700L, parse("Starbucks", "7 EUR")!!.amountCents)
    }

    @Test
    fun `importe negativo se guarda en positivo`() {
        assertEquals(399L, parse("Spotify", "-3,99 €")!!.amountCents)
    }

    @Test
    fun `ignora pagos rechazados y lo recibido`() {
        assertNull(parse("Google Pay", "Pago rechazado: 5,00 € en Zara"))
        assertNull(parse("Bizum", "Has recibido 10 €"))
    }

    @Test
    fun `sin importe no es pago`() {
        assertNull(parse("Google Wallet", "Tu tarjeta está lista"))
        assertNull(parse(null, null))
    }

    @Test
    fun `titulo generico usa frase del texto`() {
        val p = parse("Google Wallet", "Pago de 3,20 € en Cafetería Luna")!!
        assertEquals("Cafetería Luna", p.merchant)
    }

    @Test
    fun `hora no se toma como comercio`() {
        val p = parse("Google Pay", "4,00 € a las 13:05")!!
        assertEquals(400L, p.amountCents)
        assertEquals("Pago con Google Pay", p.merchant)
    }

    @Test
    fun `quita la tarjeta del nombre`() {
        val p = parse("Repsol", "Pagado 45,00 € en REPSOL con Mastercard ••9876")!!
        assertEquals("REPSOL", p.merchant)
    }
}

class PaymentParserBankTest {
    @Test
    fun `avisos de banco que no son gastos`() {
        assertNull(PaymentParser.parse("BBVA", "Tu saldo disponible es 1.234,56 €"))
        assertNull(PaymentParser.parse("Revolut", "¡Gana 20 € invitando a tus amigos!"))
        assertNull(PaymentParser.parse("CaixaBank", "Transferencia recibida de 300,00 €"))
    }

    @Test
    fun `pago con tarjeta de banco`() {
        val p = PaymentParser.parse("Revolut", "Has pagado 23,40 € en Zara")!!
        assertEquals(2340L, p.amountCents)
        assertEquals("Zara", p.merchant)
    }

    @Test
    fun `detecta textos con dinero`() {
        org.junit.Assert.assertTrue(PaymentApps.looksLikeMoney("Pago 12,50 €"))
        org.junit.Assert.assertTrue(PaymentApps.looksLikeMoney("EUR 3"))
        org.junit.Assert.assertFalse(PaymentApps.looksLikeMoney("Tu tarjeta está lista"))
    }
}

class PaymentParserFormatsTest {
    private fun parse(t: String?, b: String?) = PaymentParser.parse(t, b)

    @Test
    fun `formato en ingles`() {
        val p = parse("Google Wallet", "Contactless payment €4.50 at Starbucks")!!
        assertEquals(450L, p.amountCents)
        assertEquals("Starbucks", p.merchant)
        val q = parse("Google Pay", "Payment of EUR 9.99 to Spotify")!!
        assertEquals(999L, q.amountCents)
        assertEquals("Spotify", q.merchant)
    }

    @Test
    fun `comercio antes del importe`() {
        val p = parse("Google Wallet", "Pagado en MERCADONA\n45,20 €")!!
        assertEquals(4520L, p.amountCents)
        assertEquals("MERCADONA", p.merchant)
        val q = parse("Google Wallet", "Compra en Zara Gran Vía\n29,95 € con Mastercard ••1234")!!
        assertEquals("Zara Gran Vía", q.merchant)
    }

    @Test
    fun `titulo con importe y comercio en el texto`() {
        val p = parse("12,30 €", "IKEA Alfafar")!!
        assertEquals(1230L, p.amountCents)
        assertEquals("IKEA Alfafar", p.merchant)
    }
}

class PaymentParserForeignTest {
    @Test
    fun `pagos en otra moneda`() {
        val p = PaymentParser.parse("Google Wallet", "Contactless payment \$12.50 at Starbucks")!!
        assertEquals("USD", p.currency)
        assertEquals(1250L, p.amountCents)
        assertEquals("Starbucks", p.merchant)
        val q = PaymentParser.parse("Pret A Manger", "£4.20 with Visa ••1234")!!
        assertEquals("GBP", q.currency)
        assertEquals("Pret A Manger", q.merchant)
        assertEquals(Currencies.EUR, PaymentParser.parse("Mercadona", "12,50 €")!!.currency)
    }

    @Test
    fun `recargas de Revolut no son gastos`() {
        assertNull(PaymentParser.parse("Revolut", "50,00 € con Visa ••1234"))
        assertNull(PaymentParser.parse("REVOLUT**4821*", "20,00 €"))
        assertNull(PaymentParser.parse("Google Wallet", "Has pagado 100,00 € en Revolut Ltd con tu tarjeta"))
        assertNull(PaymentParser.parse("Google Pay", "Recarga Revolut\n30 €"))
        // Un pago normal sigue funcionando
        assertEquals("Mercadona", PaymentParser.parse("Mercadona", "12,50 €")!!.merchant)
    }

    @Test
    fun `importe ingles con miles`() {
        assertEquals(123456L, PaymentParser.parse("Starbucks", "€1,234.56")!!.amountCents)
        assertEquals(123456L, PaymentParser.parse("Google Pay", "€1,234.56 at Apple Store")!!.amountCents)
        assertEquals(123456L, PaymentParser.parse("IKEA", "1,234.56 EUR")!!.amountCents)
    }

    @Test
    fun `importe en euros escrito`() {
        val p = PaymentParser.parse("CaixaBank", "Compra de 12,50 euros en MERCADONA")!!
        assertEquals(1250L, p.amountCents)
        assertEquals("MERCADONA", p.merchant)
    }

    @Test
    fun `pago con saldo al final sigue siendo un gasto`() {
        val p = PaymentParser.parse("BBVA", "Compra de 19,99 EUR en AMAZON. Tu saldo es 1.234,00 EUR")!!
        assertEquals(1999L, p.amountCents)
        assertEquals("AMAZON", p.merchant)
    }

    @Test
    fun `comercios que contienen palabras de aviso`() {
        assertEquals(450L, PaymentParser.parse("Google Pay", "Has pagado 4,50 € en Café Saldoni")!!.amountCents)
        assertEquals(1250L, PaymentParser.parse("Google Pay", "12,50 € en Gasolinera Abonos")!!.amountCents)
    }

    @Test
    fun `avisos que no son gastos siguen ignorandose`() {
        assertNull(PaymentParser.parse("BBVA", "Te quedan 50,00 € de saldo"))
        assertNull(PaymentParser.parse("Banco", "Has recibido 20,00 € de Ana"))
        assertNull(PaymentParser.parse("Banco", "Has superado el límite: 300,00 €"))
    }

    @Test
    fun `devoluciones se leen como devolucion`() {
        val amazon = PaymentParser.parse("Amazon", "Reembolso de 20,00 €")!!
        assertTrue(amazon.refund)
        assertEquals(2000L, amazon.amountCents)
        assertEquals("Amazon", amazon.merchant)

        val zara = PaymentParser.parse("Banco", "Devolución de 12,00 € en ZARA")!!
        assertTrue(zara.refund)
        assertEquals(1200L, zara.amountCents)
        assertEquals("ZARA", zara.merchant)

        val recibido = PaymentParser.parse("Santander", "Has recibido una devolución de 35,90 € de DECATHLON")!!
        assertTrue(recibido.refund)
        assertEquals("DECATHLON", recibido.merchant)

        val ingles = PaymentParser.parse("Google Wallet", "Refund of €7.50 from Uber")!!
        assertTrue(ingles.refund)
        assertEquals(750L, ingles.amountCents)

        // Sin comercio reconocible sigue siendo una devolución
        assertTrue(PaymentParser.parse("Reembolso", "15,00 €")!!.refund)
        // Un pago normal no lo es
        assertFalse(PaymentParser.parse("Mercadona", "12,50 €")!!.refund)
    }

    @Test
    fun `importe propuesto de un aviso no reconocido`() {
        assertEquals(4590L, PaymentParser.guessAmount("Cargo en cuenta\nOperación por 45,90 € realizada"))
        assertEquals(1250L, PaymentParser.guessAmount("EUR 12.50 pending"))
        assertNull(PaymentParser.guessAmount("Tu tarjeta está lista"))
    }

    @Test
    fun `devoluciones fallidas o solo solicitadas no se apuntan`() {
        assertNull(PaymentParser.parse("Banco", "Devolución rechazada: 12,00 € en ZARA"))
        assertNull(PaymentParser.parse("Amazon", "Has solicitado un reembolso de 20,00 €"))
        assertNull(PaymentParser.parse("Amazon", "Reembolso pendiente: 20,00 €"))
        assertNull(PaymentParser.parse("Revolut", "Reembolso de 30,00 € de Revolut"))
    }
}
