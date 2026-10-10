package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CsvImportTest {

    @Test
    fun `importes y fechas`() {
        assertEquals(-1250L, CsvImport.parseAmount("-12,50"))
        assertEquals(-1250L, CsvImport.parseAmount("−12,50 €"))
        assertEquals(123456L, CsvImport.parseAmount("1.234,56"))
        assertEquals(-1250L, CsvImport.parseAmount("(12.50)"))
        assertEquals(-1250L, CsvImport.parseAmount("12,50-"))
        assertNull(CsvImport.parseAmount("Mercadona"))
        assertEquals(LocalDate.of(2025, 9, 3), CsvImport.parseDate("03/09/2025"))
        assertEquals(LocalDate.of(2025, 9, 3), CsvImport.parseDate("2025-09-03 10:11:12"))
        assertEquals(LocalDate.of(2025, 9, 3), CsvImport.parseDate("03.09.2025"))
        assertEquals(LocalDate.of(2025, 9, 1), CsvImport.parseDate("1/9/2025 10:22"))
        assertEquals(LocalDate.of(2025, 9, 3), CsvImport.parseDate("3/9/25"))
        assertEquals(LocalDate.of(2025, 9, 3), CsvImport.parseDate("2025-09-03T10:11"))
    }

    @Test
    fun `csv de banco espanol con cabecera y lineas previas`() {
        val csv = "﻿Movimientos de la cuenta ES12 3456\n" +
            "Titular;Juan\n" +
            "\n" +
            "Fecha;Fecha valor;Concepto;Importe;Saldo\n" +
            "03/09/2025;03/09/2025;COMPRA TARJ. MERCADONA VALENCIA;-45,20;1.200,00\n" +
            "01/09/2025;01/09/2025;NOMINA EMPRESA SL;1.850,00;1.245,20\n" +
            "31/08/2025;31/08/2025;\"BAR PEPE; TERRAZA\";-3,50;-604,80\n"
        val p = CsvImport.preview(csv)!!
        assertEquals(';', p.delimiter)
        assertEquals(0, p.mapping.date)
        assertEquals(2, p.mapping.description)
        assertEquals(3, p.mapping.amount)
        val rows = CsvImport.rows(p)
        assertEquals(3, rows.size)
        assertEquals(ImportedRow(LocalDate.of(2025, 9, 3), "COMPRA TARJ. MERCADONA VALENCIA", -4520), rows[0])
        assertEquals(185000L, rows[1].amountCents)
        assertEquals("BAR PEPE; TERRAZA", rows[2].description)
    }

    @Test
    fun `csv ingles con comas`() {
        val csv = "Type,Started Date,Completed Date,Description,Amount,Currency\n" +
            "CARD_PAYMENT,2025-09-02 10:00:00,2025-09-03 11:00:00,Starbucks,-4.50,EUR\n" +
            "TOPUP,2025-09-01 09:00:00,2025-09-01 09:00:00,Top-up,100.00,EUR\n"
        val p = CsvImport.preview(csv)!!
        assertEquals(',', p.delimiter)
        assertEquals(3, p.mapping.description)
        assertEquals(4, p.mapping.amount)
        val rows = CsvImport.rows(p)
        assertEquals(-450L, rows[0].amountCents)
        assertEquals("Starbucks", rows[0].description)
    }

    @Test
    fun `fichero sin datos`() {
        assertNull(CsvImport.preview(""))
        assertNull(CsvImport.preview("hola;adios\nnada;que ver"))
    }
}
