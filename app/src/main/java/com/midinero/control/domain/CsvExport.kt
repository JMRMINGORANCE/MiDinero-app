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

    fun build(expenses: List<Expense>, categories: List<Category>, trips: List<Trip> = emptyList()): String {
        val byId = categories.associateBy { it.id }
        val tripById = trips.associateBy { it.id }
        val sb = StringBuilder(BOM)
        val header = if (Lang.english) {
            listOf("Date", "Time", "Type", "Amount", "Merchant", "Category", "Note", "Source", "Original currency", "Original amount", "Trip")
        } else {
            listOf("Fecha", "Hora", "Tipo", "Importe", "Comercio", "Categoría", "Nota", "Origen", "Moneda original", "Importe original", "Viaje")
        }
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
            )
            sb.append(row.joinToString(separator) { escape(it) }).append("\r\n")
        }
        return sb.toString()
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
