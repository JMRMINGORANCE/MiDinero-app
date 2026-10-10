package com.midinero.control.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Qué columna del CSV es cada dato (índices; -1 = no hay). */
data class CsvMapping(val date: Int, val description: Int, val amount: Int)

/** Movimiento leído de un CSV del banco. [amountCents] negativo = gasto, positivo = ingreso. */
data class ImportedRow(val date: LocalDate, val description: String, val amountCents: Long)

data class CsvPreview(
    val header: List<String>,
    val rows: List<List<String>>,
    val mapping: CsvMapping,
    val delimiter: Char,
)

/**
 * Lee los CSV que exportan los bancos (BBVA, Santander, CaixaBank, ING, Revolut...). Cada banco usa
 * su formato, así que se detecta el separador, la fila de cabecera y qué columna es cada cosa.
 */
object CsvImport {

    private val dateFormats = listOf("dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd.MM.yyyy", "dd/MM/yy", "d/M/yy")
        .map { DateTimeFormatter.ofPattern(it) }

    fun parseDate(raw: String): LocalDate? {
        // Solo la fecha: «1/9/2025 10:22» o «2025-09-01T10:22» → «1/9/2025», «2025-09-01»
        val s = raw.trim().split(' ', 'T').first().trim()
        for (f in dateFormats) runCatching { return LocalDate.parse(s, f) }
        // "2025-09-01 10:22:33" o "2025-09-01T10:22"
        return runCatching { LocalDate.parse(raw.trim().take(10)) }.getOrNull()
    }

    /** "-12,50", "−12,50 €", "1.234,56", "(12.50)", "12,50-" -> céntimos con signo. */
    fun parseAmount(raw: String): Long? {
        var s = raw.trim().replace("€", "").replace("EUR", "", ignoreCase = true).replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.drop(1).dropLast(1) }
        if (s.startsWith("-") || s.startsWith("−")) { negative = true; s = s.drop(1) }
        if (s.endsWith("-")) { negative = true; s = s.dropLast(1) }
        if (s.startsWith("+")) s = s.drop(1)
        if (s.none { it.isDigit() } || s.any { !it.isDigit() && it != ',' && it != '.' }) return null
        val cents = parseAmountToCents(s) ?: return null
        return if (negative) -cents else cents
    }

    /** Separa una línea respetando comillas ("a;b" es un solo campo). */
    fun splitLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    fun detectDelimiter(lines: List<String>): Char =
        listOf(';', ',', '\t', '|').maxBy { d -> lines.take(20).sumOf { l -> splitLine(l, d).size } }

    /** Prepara la vista previa: cabecera, filas y la mejor suposición de columnas. */
    fun preview(text: String): CsvPreview? {
        val lines = text.removePrefix("\uFEFF").lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val delimiter = detectDelimiter(lines)
        val table = lines.map { splitLine(it, delimiter) }
        // Primera fila de datos: la primera con una fecha y un importe
        val firstData = table.indexOfFirst { row -> row.any { parseDate(it) != null } && row.any { cell -> parseAmount(cell) != null && cell.any { it == ',' || it == '.' || it == '-' } } }
        if (firstData < 0) return null
        val header = if (firstData > 0) table[firstData - 1] else List(table[firstData].size) { "Columna ${it + 1}" }
        val rows = table.drop(firstData).filter { it.size >= 2 }
        return CsvPreview(header, rows, guessMapping(header, rows), delimiter)
    }

    fun guessMapping(header: List<String>, rows: List<List<String>>): CsvMapping {
        val h = header.map { Categorizer.normalize(it) }
        val cols = rows.maxOfOrNull { it.size } ?: 0
        fun byName(vararg names: String) = h.indexOfFirst { col -> names.any { col.contains(it) } }
        fun share(col: Int, test: (String) -> Boolean) = rows.take(30).count { r -> r.getOrNull(col)?.let(test) == true }

        val dateCol = byName("fecha valor", "fecha operacion", "fecha", "completed date", "started date", "transaction date", "posting date", "date").takeIf { it >= 0 }
            ?: (0 until cols).maxByOrNull { share(it) { s -> parseDate(s) != null } } ?: 0
        val amountCol = byName("importe", "amount", "cantidad", "cargo", "value", "debit").takeIf { it >= 0 && it != dateCol }
            ?: (0 until cols).filter { it != dateCol }.maxByOrNull { share(it) { s -> parseAmount(s) != null && s.any { c -> c == ',' || c == '.' } } } ?: 1
        val descCol = byName("concepto", "descripcion", "description", "movimiento", "detalle", "comercio", "merchant", "payee", "details", "reference").takeIf { it >= 0 && it != dateCol && it != amountCol }
            ?: (0 until cols).filter { it != dateCol && it != amountCol }
                .maxByOrNull { col -> rows.take(30).sumOf { r -> r.getOrNull(col)?.count { it.isLetter() } ?: 0 } } ?: -1
        return CsvMapping(dateCol, descCol, amountCol)
    }

    fun rows(preview: CsvPreview, mapping: CsvMapping = preview.mapping): List<ImportedRow> =
        preview.rows.mapNotNull { r ->
            val date = r.getOrNull(mapping.date)?.let(::parseDate) ?: return@mapNotNull null
            val amount = r.getOrNull(mapping.amount)?.let(::parseAmount) ?: return@mapNotNull null
            if (amount == 0L) return@mapNotNull null
            val desc = r.getOrNull(mapping.description)?.replace(Regex("""\s+"""), " ")?.trim().orEmpty()
            ImportedRow(date, desc.ifBlank { tr("Movimiento importado", "Imported transaction") }.take(80), amount)
        }
}
