package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExportTest {

    @Test
    fun `cabecera, filas y formato espanol`() {
        val ts = LocalDate.of(2025, 3, 7).atTime(9, 5).atZone(zone).toInstant().toEpochMilli()
        val csv = CsvExport.build(
            listOf(
                Expense(id = 1, amountCents = 123456, merchant = "Bar; \"El Sol\"", note = "cena\ncon amigos", categoryId = 1, timestamp = ts, source = ExpenseSource.GOOGLE_PAY),
            ),
            listOf(Category(id = 1, name = "Restaurantes", emoji = "🍽️", colorHex = "#000000")),
        )
        assertTrue(csv.startsWith(CsvExport.BOM))
        val lines = csv.removePrefix(CsvExport.BOM).trimEnd().split("\r\n")
        assertEquals("Fecha;Hora;Tipo;Importe;Comercio;Categoría;Nota;Origen;Moneda original;Importe original;Viaje", lines[0])
        assertEquals("07/03/2025;09:05;Gasto;1234,56;\"Bar; \"\"El Sol\"\"\";Restaurantes;cena con amigos;Detectado;;;", lines[1])
    }

    @Test
    fun `decimales`() {
        assertEquals("0,05", CsvExport.decimal(5))
        assertEquals("12,00", CsvExport.decimal(1200))
        assertEquals("-3,50", CsvExport.decimal(-350))
    }
}
