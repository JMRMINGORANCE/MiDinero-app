package com.midinero.control.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Resultado de interpretar "12,50 café ayer". */
data class QuickEntry(val amountCents: Long, val merchant: String, val date: LocalDate, val isIncome: Boolean)

/**
 * Apuntar escribiendo una frase corta: "12,50 café", "mercadona 45,20", "+1200 nómina",
 * "taxi 18 ayer", "cena 60 el viernes".
 */
object QuickEntryParser {

    private val amountRegex = Regex("""(?<![\w.,])([+-]?)(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s?(?:€|\$|eur|euros|usd|mxn|d[oó]lares|pesos)?(?![\w])""", RegexOption.IGNORE_CASE)

    private val weekdays = mapOf(
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY,
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
    )

    fun parse(input: String, today: LocalDate = LocalDate.now()): QuickEntry? {
        var text = input.trim()
        if (text.isEmpty()) return null
        val match = amountRegex.find(text) ?: return null
        val cents = parseAmountToCents(match.groupValues[2]) ?: return null
        if (cents <= 0 || cents > 100_000_000) return null
        val income = match.groupValues[1] == "+" || Regex("""(?i)\b(ingreso|cobro|cobrado|nomina|nómina|salary|income|paycheck)\b""").containsMatchIn(text)
        text = text.removeRange(match.range)

        var date = today
        val words = Categorizer.normalize(text)
        when {
            Regex("""\b(anteayer|day before yesterday)\b""").containsMatchIn(words) -> date = today.minusDays(2)
            Regex("""\b(ayer|yesterday)\b""").containsMatchIn(words) -> date = today.minusDays(1)
            else -> weekdays.entries.firstOrNull { Regex("""\b${it.key}\b""").containsMatchIn(words) }?.let { (_, dow) ->
                date = today.with(TemporalAdjusters.previousOrSame(dow))
            }
        }
        // Quita las palabras de fecha y relleno del concepto
        val merchant = text
            .replace(Regex("""(?i)\b(anteayer|ayer|hoy|el|la|en|de|del|pasado|pasada|lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b"""), " ")
            .replace(Regex("""(?i)\b(the day before yesterday|yesterday|today|last|on|at|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-', ':')
            .replaceFirstChar { it.titlecase(Lang.locale) }
        return QuickEntry(cents, merchant.ifBlank { if (income) tr("Ingreso", "Income") else tr("Gasto", "Expense") }.take(80), date, income)
    }
}
