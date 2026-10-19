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
        assertEquals(
            "Fecha;Hora;Tipo;Importe;Comercio;Categoría;Nota;Origen;Moneda original;Importe original;Viaje;" +
                "Método de pago;Litros;Km del repostaje;Km del coche;Coche o vivienda;kWh punta;kWh llano;kWh valle;kWh excedentes;kWh gas;m³;Mantenimiento",
            lines[0],
        )
        assertEquals("07/03/2025;09:05;Gasto;1234,56;\"Bar; \"\"El Sol\"\"\";Restaurantes;cena con amigos;Detectado;;;" + ";".repeat(12), lines[1])
    }

    /** Un CSV exportado sirve de plantilla: al importarlo vuelven el tipo, la categoría, la hora y los datos extra. */
    @Test
    fun `exportar e importar con datos extra`() {
        val ts = LocalDate.of(2025, 3, 7).atTime(9, 5).atZone(zone).toInstant().toEpochMilli()
        val car = com.midinero.control.data.Vehicle(id = 4, name = "León")
        val card = com.midinero.control.data.Account(id = 2, name = "Crédito BBVA")
        val csv = CsvExport.build(
            listOf(
                Expense(id = 1, amountCents = 6000, merchant = "Repsol", categoryId = 1, timestamp = ts, accountId = 2,
                    details = ExpenseDetails(liters = 40.5, tripKm = 610.0, odometerKm = 120345, vehicleId = 4).encode()),
                Expense(id = 2, amountCents = 4210, merchant = "Canal", categoryId = 2, timestamp = ts, details = ExpenseDetails(m3 = 12.5).encode()),
                Expense(id = 3, amountCents = 9000, merchant = "Taller", categoryId = 3, timestamp = ts,
                    details = ExpenseDetails(services = setOf(ServiceType.OIL, ServiceType.TYRES)).encode()),
                Expense(id = 4, amountCents = -1500, merchant = "Zara", categoryId = 3, timestamp = ts),
                Expense(id = 5, amountCents = 200000, merchant = "Nómina", isIncome = true, timestamp = ts),
            ),
            listOf(
                Category(id = 1, name = "Gasolina", emoji = "⛽", colorHex = "#000000"),
                Category(id = 2, name = "Agua", emoji = "🚿", colorHex = "#000000"),
                Category(id = 3, name = "Taller", emoji = "🔧", colorHex = "#000000"),
            ),
            vehicles = listOf(car), methods = listOf(card),
        )
        val preview = CsvImport.preview(csv)!!
        assertTrue(preview.mapping.midinero)
        val rows = CsvImport.rows(preview)
        assertEquals(5, rows.size)
        val fuel = rows[0]
        assertEquals(-6000L, fuel.amountCents)
        assertEquals("Gasolina", fuel.category)
        assertEquals(java.time.LocalTime.of(9, 5), fuel.time)
        assertEquals(40.5, fuel.details.liters!!, 0.001)
        assertEquals(610.0, fuel.details.tripKm!!, 0.001)
        assertEquals(120345L, fuel.details.odometerKm)
        assertEquals("León", fuel.vehicle)
        assertEquals("Crédito BBVA", fuel.method)
        assertEquals(12.5, rows[1].details.m3!!, 0.001)
        assertEquals(TrackerKind.WATER, rows[1].details.impliedKind)
        assertEquals(setOf(ServiceType.OIL, ServiceType.TYRES), rows[2].details.services)
        assertTrue(rows[3].refund)
        assertEquals(1500L, rows[3].amountCents)
        assertEquals(200000L, rows[4].amountCents)
        assertTrue(!rows[4].refund)
    }

    @Test
    fun `decimales`() {
        assertEquals("0,05", CsvExport.decimal(5))
        assertEquals("12,00", CsvExport.decimal(1200))
        assertEquals("-3,50", CsvExport.decimal(-350))
    }
}
