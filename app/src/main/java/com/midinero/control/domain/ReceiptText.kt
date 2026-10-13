package com.midinero.control.domain

import java.time.LocalDate

/** Lo que se ha podido leer de la foto de un ticket. */
data class ReceiptReading(val amountCents: Long?, val merchant: String?, val date: LocalDate?) {
    val isEmpty: Boolean get() = amountCents == null && merchant == null && date == null
}

/**
 * Interpreta el texto reconocido en la foto de un ticket (una fila por línea, como se ve en el papel):
 * el total, el comercio y la fecha.
 */
object ReceiptText {
    private val amount = Regex("""(?<![\d.,])(\d{1,3}(?:[.\s]\d{3})*|\d+)[,.](\d{2})(?![\d])""")
    private val totalWords = Regex("""(?i)\b(total(\s+a\s+pagar|\s+eur|\s+€|\s+compra|\s+ticket)?|importe(\s+total)?|a\s+pagar|tarjeta|amount\s+due|grand\s+total|sum[ae]?)\b""")
    private val notTotal = Regex("""(?i)\b(sub\s*-?total|base(\s+imponible)?|i\.?v\.?a|iva|impuestos?|tax|cambio|entregado|efectivo\s+entregado|descuento|ahorro|dto|propina|tip|cuota)\b""")
    private val dateRe = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})\b""")
    private val notMerchant = Regex("""(?i)(factura|ticket|simplificad|c\.?i\.?f|n\.?i\.?f|tel[eé]?f?|tlf|www\.|http|@|gracias|bienvenid|fecha|hora|caja|mesa|cajer|operador|\bc/|\bcalle\b|\bavda\b|\bav\.|\bpl\.|\d{5})""")

    fun parse(rows: List<String>, today: LocalDate = LocalDate.now()): ReceiptReading {
        val clean = rows.map { it.replace('€', ' ').replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        return ReceiptReading(total(clean), merchant(clean), date(clean, today))
    }

    private fun amountsIn(line: String): List<Long> = amount.findAll(line).mapNotNull { m ->
        val whole = m.groupValues[1].replace(".", "").replace(" ", "").toLongOrNull() ?: return@mapNotNull null
        whole * 100 + m.groupValues[2].toLong()
    }.toList()

    private fun total(rows: List<String>): Long? {
        // La última línea con «TOTAL» (o similar) que no sea subtotal/IVA; su importe o el de la línea siguiente
        val candidates = rows.indices.filter { totalWords.containsMatchIn(rows[it]) && !notTotal.containsMatchIn(rows[it]) }
        for (i in candidates.sortedDescending()) {
            amountsIn(rows[i]).lastOrNull()?.let { if (it > 0) return it }
            rows.getOrNull(i + 1)?.let { next -> if (!notTotal.containsMatchIn(next)) amountsIn(next).firstOrNull()?.let { if (it > 0) return it } }
        }
        // Sin palabra «total»: el importe más alto que no sea de cambio ni de efectivo entregado
        return rows.filter { !notTotal.containsMatchIn(it) }.flatMap { amountsIn(it) }.filter { it in 1..10_000_000 }.maxOrNull()
    }

    private fun merchant(rows: List<String>): String? {
        val top = rows.take(8)
        // Un comercio conocido en la cabecera del ticket
        top.firstNotNullOfOrNull { MerchantCatalog.find(it)?.name }?.let { return it }
        val line = top.firstOrNull { r ->
            val letters = r.count { it.isLetter() }
            letters >= 3 && letters >= r.length / 2 && !notMerchant.containsMatchIn(r) && amountsIn(r).isEmpty()
        } ?: return null
        val text = line.trim('*', '-', '=', '.', ' ')
        // «MERCADONA S.A.» → «Mercadona»; las mayúsculas del ticket, con mayúscula inicial
        val noSuffix = text.replace(Regex("""(?i)[,\s]+(s\.?\s?l\.?u?|s\.?\s?a\.?|s\.?\s?c\.?|c\.?\s?b\.?)\.?$"""), "").trim()
        return CustomMerchants.suggestName(noSuffix).take(40).ifBlank { null }
    }

    private fun date(rows: List<String>, today: LocalDate): LocalDate? = rows.firstNotNullOfOrNull { r ->
        dateRe.findAll(r).firstNotNullOfOrNull { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            runCatching { LocalDate.of(y, mo, d) }.getOrNull()?.takeIf { !it.isAfter(today) && it.isAfter(today.minusYears(2)) }
        }
    }
}
