package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import java.time.LocalDate

/**
 * Búsqueda en todo el historial. Admite texto ("mercadona"), importes exactos ("12,50"),
 * rangos (">50", "<10", "20-40") y fechas ("hoy", "ayer", "septiembre", "sept 2025", "12/09",
 * "12/09/2025", "esta semana", "mes pasado"). Se pueden combinar: "amazon >30 septiembre".
 */
data class SearchQuery(
    val words: List<String>,
    val minCents: Long? = null,
    val maxCents: Long? = null,
    /** Primer y último día incluidos, si se ha buscado una fecha. */
    val fromDay: LocalDate? = null,
    val toDay: LocalDate? = null,
) {
    val isEmpty: Boolean get() = words.isEmpty() && minCents == null && maxCents == null && fromDay == null

    fun matches(e: Expense, category: Category?): Boolean {
        if (minCents != null && e.amountCents < minCents) return false
        if (maxCents != null && e.amountCents > maxCents) return false
        if (fromDay != null || toDay != null) {
            val d = dateOf(e.timestamp)
            if (fromDay != null && d.isBefore(fromDay)) return false
            if (toDay != null && d.isAfter(toDay)) return false
        }
        if (words.isEmpty()) return true
        val haystack = Categorizer.normalize(listOfNotNull(e.merchant, e.note, category?.name).joinToString(" "))
        return words.all { haystack.contains(it) }
    }

    companion object {
        private val range = Regex("""^(\d+(?:[.,]\d{1,2})?)\s*-\s*(\d+(?:[.,]\d{1,2})?)$""")
        private val compare = Regex("""^([<>])\s*(\d+(?:[.,]\d{1,2})?)$""")
        private val exact = Regex("""^\d+(?:[.,]\d{1,2})?$""")
        private val slashDate = Regex("""^(\d{1,2})/(\d{1,2})(?:/(\d{2}|\d{4}))?$""")
        private val year = Regex("""^(19|20)\d{2}$""")

        /** Meses en español e inglés, enteros y abreviados. */
        private val months: Map<String, Int> = buildMap {
            val es = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
            val en = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
            es.forEachIndexed { i, m -> put(m, i + 1); put(m.take(3), i + 1) }
            en.forEachIndexed { i, m -> put(m, i + 1); put(m.take(3), i + 1) }
            put("setiembre", 9); put("sept", 9); put("sep", 9); put("set", 9)
        }

        /** Expresiones de varias palabras: se cambian por una sola antes de separar. */
        private val phrases = listOf(
            "esta semana" to "#thisweek", "this week" to "#thisweek",
            "semana pasada" to "#lastweek", "la semana pasada" to "#lastweek", "last week" to "#lastweek",
            "este mes" to "#thismonth", "this month" to "#thismonth",
            "mes pasado" to "#lastmonth", "el mes pasado" to "#lastmonth", "last month" to "#lastmonth",
            "este año" to "#thisyear", "this year" to "#thisyear",
        )

        fun parse(text: String, today: LocalDate = LocalDate.now()): SearchQuery {
            var min: Long? = null
            var max: Long? = null
            var from: LocalDate? = null
            var to: LocalDate? = null
            fun days(a: LocalDate, b: LocalDate) { from = a; to = b }
            val words = mutableListOf<String>()
            var lower = " " + text.trim().lowercase(SPANISH) + " "
            phrases.sortedByDescending { it.first.length }.forEach { (p, tag) -> lower = lower.replace(" $p ", " $tag ") }
            // "> 50" se junta en un solo trozo antes de separar por espacios
            val tokens = lower.trim().replace(Regex("""([<>])\s+"""), "$1").split(Regex("""\s+""")).filter { it.isNotBlank() }
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i].trim(',', '.')
                val r = range.find(t)
                val c = compare.find(t)
                val sd = slashDate.find(t)
                val month = months[t.trimEnd('.')]
                i++
                when {
                    t == "hoy" || t == "today" -> days(today, today)
                    t == "ayer" || t == "yesterday" -> days(today.minusDays(1), today.minusDays(1))
                    t == "anteayer" -> days(today.minusDays(2), today.minusDays(2))
                    t == "#thisweek" -> days(today.with(java.time.DayOfWeek.MONDAY), today)
                    t == "#lastweek" -> today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1).let { days(it, it.plusDays(6)) }
                    // «Este mes» es el periodo de gastos (puede empezar el día de cobro), como en el resto de la app
                    t == "#thismonth" -> days(periodOf(today).startDate(), today)
                    t == "#lastmonth" -> periodOf(today).minusMonths(1).let { days(it.startDate(), it.endDateExclusive().minusDays(1)) }
                    t == "#thisyear" -> days(today.withDayOfYear(1), today)
                    sd != null -> {
                        val d = sd.groupValues[1].toInt()
                        val m = sd.groupValues[2].toInt()
                        val yText = sd.groupValues[3]
                        val y = when {
                            yText.length == 4 -> yText.toInt()
                            yText.length == 2 -> 2000 + yText.toInt()
                            // Sin año: el último 12/09 que ya ha pasado
                            else -> if (runCatching { LocalDate.of(today.year, m, d) }.getOrNull()?.isAfter(today) == true) today.year - 1 else today.year
                        }
                        val date = runCatching { LocalDate.of(y, m, d) }.getOrNull()
                        if (date != null) days(date, date)
                    }
                    month != null && (t.length > 3 || t.trimEnd('.') in setOf("ene", "feb", "mar", "abr", "jun", "jul", "ago", "sep", "oct", "nov", "dic", "jan", "apr", "aug", "dec", "sept")) -> {
                        // «septiembre 2025»; sin año, el último septiembre (este año si ya ha llegado)
                        val next = tokens.getOrNull(i)?.trim(',', '.')
                        val y = if (next != null && year.matches(next)) { i++; next.toInt() }
                        else if (month > today.monthValue) today.year - 1 else today.year
                        val ym = java.time.YearMonth.of(y, month)
                        days(ym.atDay(1), ym.atEndOfMonth())
                    }
                    t == "de" || t == "del" || t == "en" || t == "of" || t == "in" -> Unit
                    else -> when {
                    r != null -> {
                        min = parseUserAmount(r.groupValues[1])
                        max = parseUserAmount(r.groupValues[2])
                    }
                    c != null -> {
                        val cents = parseUserAmount(c.groupValues[2])
                        if (c.groupValues[1] == ">") min = cents else max = cents
                    }
                    exact.matches(t) -> {
                        val cents = parseUserAmount(t)
                        min = cents
                        max = cents
                    }
                    else -> Categorizer.normalize(t).takeIf { it.isNotEmpty() }?.let { words += it }
                    }
                }
            }
            if (min != null && max != null && min > max) {
                val tmp = min
                min = max
                max = tmp
            }
            return SearchQuery(words, min, max, from, to)
        }
    }
}

/** Texto para compartir el resumen de un mes (por WhatsApp, correo...). */
object MonthReport {
    fun text(
        periodLabel: String,
        spentCents: Long,
        incomeCents: Long,
        previousSpentCents: Long?,
        topCategories: List<Pair<String, Long>>,
        budgetCents: Long?,
    ): String = buildString {
        append(tr("Mis gastos de $periodLabel (MiDinero)\n", "My spending for $periodLabel (MiDinero)\n"))
        append(tr("Gastado: ${formatCents(spentCents)}", "Spent: ${formatCents(spentCents)}"))
        if (budgetCents != null && budgetCents > 0) append(tr(" de ${formatCents(budgetCents)} de presupuesto", " of a ${formatCents(budgetCents)} budget"))
        append("\n")
        if (incomeCents > 0) {
            append(tr("Ingresos: ${formatCents(incomeCents)}\n", "Income: ${formatCents(incomeCents)}\n"))
            val balance = incomeCents - spentCents
            append(if (balance >= 0) tr("Ahorro: ${formatCents(balance)}\n", "Savings: ${formatCents(balance)}\n") else tr("Déficit: ${formatCents(-balance)}\n", "Deficit: ${formatCents(-balance)}\n"))
        }
        if (previousSpentCents != null && previousSpentCents > 0) {
            val pct = ((spentCents - previousSpentCents) * 100 / previousSpentCents).toInt()
            append(
                when {
                    pct > 0 -> tr("Un $pct % más que el mes anterior\n", "$pct % more than the previous month\n")
                    pct < 0 -> tr("Un ${-pct} % menos que el mes anterior\n", "${-pct} % less than the previous month\n")
                    else -> tr("Igual que el mes anterior\n", "Same as the previous month\n")
                }
            )
        }
        if (topCategories.isNotEmpty()) {
            append(tr("\nEn qué:\n", "\nWhere:\n"))
            topCategories.take(5).forEach { (name, cents) -> append("• $name: ${formatCents(cents)}\n") }
        }
    }.trimEnd()
}
