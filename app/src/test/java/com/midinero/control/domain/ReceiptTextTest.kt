package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptTextTest {
    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun ticketDeSupermercado() {
        val rows = """
            MERCADONA, S.A.
            C/ MAYOR 12 28013 MADRID
            TELEFONO: 912345678
            12/10/2026 18:42  OP: 123456
            FACTURA SIMPLIFICADA: 2345-019-123456
            1 LECHE ENTERA            0,95
            2 PAN BARRA               1,80
            1 ACEITE OLIVA            8,45
            TOTAL (€)                11,20
            TARJETA BANCARIA         11,20
            IVA  BASE  CUOTA
            10%  10,18  1,02
        """.trimIndent().lines()
        val r = ReceiptText.parse(rows, today)
        assertEquals(1120L, r.amountCents)
        assertEquals("Mercadona", r.merchant)
        assertEquals(LocalDate.of(2026, 10, 12), r.date)
    }

    @Test
    fun barSinTotalClaro() {
        val rows = listOf("BAR EL RINCON DE PACO", "CIF B12345678", "Mesa 4", "2 CAÑAS 3,00", "1 RACION 9,50", "IMPORTE 12,50", "ENTREGADO 20,00", "CAMBIO 7,50", "13-10-26")
        val r = ReceiptText.parse(rows, today)
        assertEquals(1250L, r.amountCents)
        assertEquals("Bar el Rincon de Paco", r.merchant)
        assertEquals(LocalDate.of(2026, 10, 13), r.date)
    }

    @Test
    fun totalEnLaLineaSiguienteYMiles() {
        val r = ReceiptText.parse(listOf("ELECTRO HOGAR SL", "SUBTOTAL 1.000,00", "IVA 210,00", "TOTAL A PAGAR", "1.210,00"), today)
        assertEquals(121000L, r.amountCents)
        assertEquals("Electro Hogar", r.merchant)
        assertNull(r.date)
    }
}
