package com.midinero.control.domain

import com.midinero.control.data.Expense

/**
 * Etiquetas: palabras con # en la nota de un movimiento («cena con Ana #cumpleaños #amigos»).
 * Sirven para juntar gastos de categorías distintas. Se comparan sin mayúsculas ni acentos.
 */
object Tags {
    private val tag = Regex("""#([\p{L}\p{N}_-]{2,30})""")

    /** Etiquetas de un texto, ya normalizadas («#Cumpleaños» → «cumpleanos»). */
    fun of(text: String): Set<String> = tag.findAll(text).map { key(it.groupValues[1]) }.filter { it.isNotEmpty() }.toSet()

    /** Las etiquetas tal y como se escribieron la primera vez, para mostrarlas («Cumpleaños»). */
    fun labels(expenses: List<Expense>): Map<String, String> = buildMap {
        expenses.sortedBy { it.timestamp }.forEach { e -> tag.findAll(e.note).forEach { m -> putIfAbsent(key(m.groupValues[1]), m.groupValues[1]) } }
    }

    fun key(raw: String): String = Categorizer.normalize(raw.removePrefix("#")).replace(" ", "")

    data class TagTotal(val key: String, val label: String, val totalCents: Long, val count: Int, val lastTimestamp: Long)

    /** Gasto por etiqueta (sin ingresos), de más a menos. */
    fun totals(expenses: List<Expense>): List<TagTotal> {
        val labels = labels(expenses)
        val spending = expenses.filter { !it.isIncome }
        return spending.flatMap { e -> of(e.note).map { it to e } }
            .groupBy({ it.first }, { it.second })
            .map { (k, list) -> TagTotal(k, labels[k] ?: k, list.sumOf { it.amountCents }, list.size, list.maxOf { it.timestamp }) }
            .sortedByDescending { it.totalCents }
    }

    /** Las más usadas últimamente, para ofrecerlas al apuntar. */
    fun suggestions(expenses: List<Expense>, limit: Int = 8): List<String> {
        val labels = labels(expenses)
        return expenses.sortedByDescending { it.timestamp }.take(400)
            .flatMap { of(it.note) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(limit).map { labels[it.key] ?: it.key }
    }

    /** Añade #etiqueta al final de la nota si no la tiene ya. */
    fun append(note: String, label: String): String {
        if (key(label) in of(note)) return note
        val clean = label.removePrefix("#").replace(Regex("""\s+"""), "")
        return (note.trimEnd() + " #" + clean).trim()
    }
}
