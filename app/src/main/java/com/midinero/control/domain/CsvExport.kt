package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import com.midinero.control.data.Trip
import java.time.format.DateTimeFormatter

/**
 * CSV pensado para abrirse con Excel o Google Sheets en español:
 * separador ";", coma decimal y BOM UTF-8 para que se vean bien las tildes.
 */
object CsvExport {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** En español, «;» y coma decimal (Excel en España); en inglés, «,» y punto. */
    private val separator: String get() = if (Lang.english) "," else ";"
    const val BOM = "\uFEFF"

    /** Columnas de datos extra (litros, kWh…), con los mismos nombres que lee la importación. */
    private val extraFields = listOf(
        CsvField.METHOD, CsvField.LITERS, CsvField.TRIP_KM, CsvField.ODOMETER, CsvField.VEHICLE,
        CsvField.KWH_PEAK, CsvField.KWH_FLAT, CsvField.KWH_VALLEY, CsvField.KWH_EXPORTED, CsvField.KWH_GAS, CsvField.M3, CsvField.SERVICES,
    )

    fun build(
        expenses: List<Expense>,
        categories: List<Category>,
        trips: List<Trip> = emptyList(),
        vehicles: List<com.midinero.control.data.Vehicle> = emptyList(),
        methods: List<com.midinero.control.data.Account> = emptyList(),
    ): String {
        val byId = categories.associateBy { it.id }
        val tripById = trips.associateBy { it.id }
        val vehicleById = vehicles.associateBy { it.id }
        val methodById = methods.associateBy { it.id }
        val sb = StringBuilder(BOM)
        val header = if (Lang.english) {
            listOf("Date", "Time", "Type", "Amount", "Merchant", "Category", "Note", "Source", "Original currency", "Original amount", "Trip")
        } else {
            listOf("Fecha", "Hora", "Tipo", "Importe", "Comercio", "Categoría", "Nota", "Origen", "Moneda original", "Importe original", "Viaje")
        } + extraFields.map { it.header }
        sb.append(header.joinToString(separator)).append("\r\n")
        expenses.sortedBy { it.timestamp }.forEach { e ->
            val row = listOf(
                dateOf(e.timestamp).format(dateFormat),
                timeLabel(e.timestamp),
                when {
                    e.isIncome -> tr("Ingreso", "Income")
                    e.isRefund -> tr("Devolución", "Refund")
                    else -> tr("Gasto", "Expense")
                },
                decimal(e.amountCents),
                e.merchant,
                e.categoryId?.let { byId[it]?.name } ?: "",
                e.note,
                when (e.source) {
                    ExpenseSource.MANUAL -> tr("Manual", "Manual")
                    ExpenseSource.GOOGLE_PAY -> tr("Detectado", "Detected")
                    ExpenseSource.RECURRING -> tr("Recurrente", "Recurring")
                    ExpenseSource.IMPORTED -> tr("Importado", "Imported")
                },
                e.originalCurrency ?: "",
                e.originalAmountCents?.takeIf { e.originalCurrency != null }?.let { decimal(it) } ?: "",
                e.tripId?.let { tripById[it]?.name } ?: "",
            ) + extras(ExpenseDetails.parse(e.details), e.accountId?.let { methodById[it]?.name }, vehicleById)
            sb.append(row.joinToString(separator) { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun extras(d: ExpenseDetails, method: String?, vehicles: Map<Long, com.midinero.control.data.Vehicle>): List<String> {
        fun q(v: Double?) = v?.let { quantityText(it) } ?: ""
        return listOf(
            method.orEmpty(), q(d.liters), q(d.tripKm), d.odometerKm?.toString().orEmpty(),
            d.vehicleId?.let { vehicles[it]?.name }.orEmpty(),
            q(d.kwhPeak), q(d.kwhFlat), q(d.kwhValley), q(d.kwhExported), q(d.kwh), q(d.m3),
            d.services.sortedBy { it.ordinal }.joinToString(", ") { it.label },
        )
    }

    /** 40.5 -> "40,5" (o "40.5" en inglés), sin separador de miles. */
    private fun quantityText(v: Double): String {
        val r = Math.round(v * 1000) / 1000.0
        val s = if (r == Math.floor(r)) r.toLong().toString() else r.toString()
        return if (Lang.english) s else s.replace('.', ',')
    }

    /** 123456 -> "1234,56" (sin separador de miles, para que la hoja lo lea como número). */
    fun decimal(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return (if (Lang.english) "%s%d.%02d" else "%s%d,%02d").format(sign, abs / 100, abs % 100)
    }

    fun escape(value: String): String {
        val v = value.replace("\r", " ").replace("\n", " ")
        return if (v.any { it == ';' || it == ',' && Lang.english || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}
