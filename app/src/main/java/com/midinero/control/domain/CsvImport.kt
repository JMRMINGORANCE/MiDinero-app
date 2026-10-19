package com.midinero.control.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Columnas de más que se leen si el CSV las tiene con el mismo nombre que las que exporta MiDinero
 * (en español o en inglés): así un CSV exportado sirve de plantilla para importar.
 */
enum class CsvField(val es: String, val en: String, val aliases: List<String> = emptyList()) {
    TYPE("Tipo", "Type"),
    TIME("Hora", "Time"),
    CATEGORY("Categoría", "Category"),
    NOTE("Nota", "Note"),
    METHOD("Método de pago", "Payment method"),
    LITERS("Litros", "Litres"),
    TRIP_KM("Km del repostaje", "Refuel km"),
    ODOMETER("Km del coche", "Odometer km"),
    VEHICLE("Coche o vivienda", "Car or home", listOf("Coche", "Car", "Vivienda", "Home", "Casa")),
    KWH_PEAK("kWh punta", "Peak kWh"),
    KWH_FLAT("kWh llano", "Flat kWh"),
    KWH_VALLEY("kWh valle", "Off-peak kWh"),
    KWH_EXPORTED("kWh excedentes", "Exported kWh"),
    KWH_GAS("kWh gas", "Gas kWh"),
    M3("m³", "m³"),
    SERVICES("Mantenimiento", "Servicing");

    val header: String get() = tr(es, en)

    companion object {
        /** «Categoría» = «categoria», «m³» = «m3». */
        fun norm(s: String): String = java.text.Normalizer.normalize(s.trim().lowercase().replace("³", "3"), java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "").replace(Regex("""\s+"""), " ")

        fun detect(header: List<String>): Map<CsvField, Int> {
            val h = header.map(::norm)
            return entries.mapNotNull { f ->
                val names = (listOf(f.es, f.en) + f.aliases).map(::norm) + if (f == LITERS) listOf("liters") else emptyList()
                val i = h.indexOfFirst { it in names }
                if (i >= 0) f to i else null
            }.toMap()
        }
    }
}

/** Qué columna del CSV es cada dato (índices; -1 = no hay). [extras]: columnas de más; [midinero]: es un CSV de MiDinero. */
data class CsvMapping(
    val date: Int,
    val description: Int,
    val amount: Int,
    val extras: Map<CsvField, Int> = emptyMap(),
    val midinero: Boolean = false,
)

/** Movimiento leído de un CSV. [amountCents] negativo = gasto, positivo = ingreso (o devolución si [refund]). */
data class ImportedRow(
    val date: LocalDate,
    val description: String,
    val amountCents: Long,
    val refund: Boolean = false,
    val time: java.time.LocalTime? = null,
    /** Nombre de la categoría; null = se clasifica sola. */
    val category: String? = null,
    /** Si no existe la categoría, se crea (solo en CSV de MiDinero; en los del banco se clasifica sola). */
    val createCategory: Boolean = false,
    val note: String = "",
    val details: ExpenseDetails = ExpenseDetails.EMPTY,
    /** Nombre del coche y del método de pago (se crean si no existen). */
    val vehicle: String? = null,
    val method: String? = null,
)

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
        var s = raw.trim().replace("€", "").replace("$", "").replace("EUR", "", ignoreCase = true).replace("USD", "", ignoreCase = true).replace("MXN", "", ignoreCase = true).replace(" ", "").replace(" ", "")
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
        val extras = CsvField.detect(header)
        // Un CSV de MiDinero (o hecho con su plantilla): trae tipo, categoría y origen
        val origin = h.indexOfFirst { it == "origen" || it == "source" }
        val midinero = CsvField.TYPE in extras && CsvField.CATEGORY in extras && origin >= 0
        return CsvMapping(dateCol, descCol, amountCol, extras.filterValues { it != dateCol && it != amountCol && it != descCol }, midinero)
    }

    private fun services(raw: String): Set<ServiceType> =
        raw.split(',', '+', '/', ';').mapNotNull { part ->
            val p = CsvField.norm(part)
            if (p.isEmpty()) null else ServiceType.entries.firstOrNull { t ->
                p == t.name.lowercase() || p == CsvField.norm(tr(t.labelEs, t.labelEn)) || p == CsvField.norm(t.labelEs) || p == CsvField.norm(t.labelEn)
            }
        }.toSet()

    /** Gasto, ingreso o devolución según la columna «Tipo» (null si no la hay o no se entiende). */
    private fun type(raw: String?): Int? = when (raw?.let(CsvField::norm)) {
        "gasto", "expense" -> -1
        "ingreso", "income" -> 1
        "devolucion", "refund" -> 0
        else -> null
    }

    fun rows(preview: CsvPreview, mapping: CsvMapping = preview.mapping): List<ImportedRow> =
        preview.rows.mapNotNull { r ->
            val date = r.getOrNull(mapping.date)?.let(::parseDate) ?: return@mapNotNull null
            val amount = r.getOrNull(mapping.amount)?.let(::parseAmount) ?: return@mapNotNull null
            if (amount == 0L) return@mapNotNull null
            val desc = r.getOrNull(mapping.description)?.replace(Regex("""\s+"""), " ")?.trim().orEmpty()
            fun cell(f: CsvField) = mapping.extras[f]?.let { r.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            fun num(f: CsvField) = cell(f)?.let(::parseQuantity)
            // Con la columna «Tipo» (las de MiDinero), el importe va en positivo y el tipo dice qué es
            val type = type(cell(CsvField.TYPE))
            val abs = kotlin.math.abs(amount)
            val details = ExpenseDetails(
                liters = num(CsvField.LITERS), tripKm = num(CsvField.TRIP_KM), odometerKm = num(CsvField.ODOMETER)?.toLong(),
                kwhPeak = num(CsvField.KWH_PEAK), kwhFlat = num(CsvField.KWH_FLAT), kwhValley = num(CsvField.KWH_VALLEY),
                kwhExported = num(CsvField.KWH_EXPORTED), kwh = num(CsvField.KWH_GAS), m3 = num(CsvField.M3),
                services = cell(CsvField.SERVICES)?.let(::services).orEmpty(),
            )
            ImportedRow(
                date = date,
                description = desc.ifBlank { tr("Movimiento importado", "Imported transaction") }.take(80),
                amountCents = when (type) { -1 -> -abs; 1, 0 -> abs; else -> amount },
                refund = type == 0,
                time = cell(CsvField.TIME)?.let { t -> runCatching { java.time.LocalTime.parse(t.padStart(5, '0').take(5)) }.getOrNull() },
                category = cell(CsvField.CATEGORY),
                createCategory = mapping.midinero,
                note = cell(CsvField.NOTE).orEmpty().take(300),
                details = details,
                vehicle = cell(CsvField.VEHICLE),
                method = cell(CsvField.METHOD),
            )
        }
}
