package com.midinero.control.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

val SPANISH: Locale = Locale("es", "ES")

private val currencyFormats = ThreadLocal.withInitial { HashMap<Pair<Locale, String>, NumberFormat>() }

/** Formato de la moneda principal ([Currencies.base]) en el idioma de la app: «12,50 €», «12,50 US$», «12,50 MX$». */
private fun currencyFormat(): NumberFormat = currencyFormats.get()!!.getOrPut(Lang.locale to Currencies.base) {
    NumberFormat.getCurrencyInstance(Lang.locale).apply {
        currency = java.util.Currency.getInstance(Currencies.base)
        // Los pesos y los dólares también se apuntan con céntimos
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
}

/** Si devuelve true, los importes se muestran ocultos ("•••• €"); lo controla la opción de privacidad. */
@Volatile
var amountsHidden: () -> Boolean = { false }

val HIDDEN_AMOUNT: String get() = "•••• " + Currencies.symbol()

/** 1250 -> "12,50 €" (o "•••• €" si el usuario ha ocultado los importes). */
fun formatCents(cents: Long): String =
    if (amountsHidden()) HIDDEN_AMOUNT else currencyFormat().format(cents / 100.0)

/** Importe con signo para listas: «−12,50 €» un gasto, «+12,50 €» un ingreso o una devolución. */
fun signedAmount(e: com.midinero.control.data.Expense): String =
    if (e.isIncome || e.isRefund) "+" + formatCents(kotlin.math.abs(e.amountCents)) else "−" + formatCents(e.amountCents)

/** Convierte lo que escribe el usuario ("12,5", "12.50", "1.234,56") a céntimos. */
fun parseUserAmount(input: String): Long? {
    val cleaned = input.trim().replace("€", "").replace("$", "").replace(" ", "")
    if (cleaned.isEmpty()) return null
    return parseAmountToCents(cleaned)?.takeIf { it > 0 }
}

/**
 * Convierte un importe con separadores españoles o ingleses a céntimos.
 * El último separador seguido de 1-2 cifras se toma como decimal.
 */
fun parseAmountToCents(raw: String): Long? {
    val s = raw.replace(" ", "").replace(" ", "").replace(" ", "")
    if (s.isEmpty() || s.none { it.isDigit() }) return null
    val lastSep = maxOf(s.lastIndexOf(','), s.lastIndexOf('.'))
    val normalized = if (lastSep >= 0 && s.length - lastSep - 1 in 1..2) {
        val intPart = s.substring(0, lastSep).replace(".", "").replace(",", "")
        val decPart = s.substring(lastSep + 1)
        "$intPart.$decPart"
    } else {
        s.replace(".", "").replace(",", "")
    }
    val value = normalized.toDoubleOrNull() ?: return null
    return (abs(value) * 100).roundToLong()
}

fun centsToInput(cents: Long): String {
    val euros = cents / 100
    val rest = cents % 100
    return if (rest == 0L) euros.toString() else (if (Lang.english) "%d.%02d" else "%d,%02d").format(euros, rest)
}

val zone: ZoneId get() = ZoneId.systemDefault()

/** Inicio y fin (sin incluir) del periodo de gastos; ver [Period]. */
fun YearMonth.startMillis(): Long = startDate().atStartOfDay(zone).toInstant().toEpochMilli()
fun YearMonth.endMillis(): Long = endDateExclusive().atStartOfDay(zone).toInstant().toEpochMilli()

/** Periodo de gastos al que pertenece un instante. */
fun monthOf(millis: Long): YearMonth = periodOf(dateOf(millis))
fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private val monthFormatter get() = Lang.dateFormat("LLLL yyyy")
private val dayFormatter get() = Lang.dateFormat("EEEE d 'de' MMMM", "EEEE d MMMM")
private val shortDateFormatter get() = Lang.dateFormat("d MMM yyyy")
private val timeFormatter get() = Lang.dateFormat("HH:mm")

fun YearMonth.label(): String = format(monthFormatter).replaceFirstChar { it.titlecase(Lang.locale) }
fun LocalDate.dayLabel(): String = format(dayFormatter).replaceFirstChar { it.titlecase(Lang.locale) }
fun LocalDate.shortLabel(): String = format(shortDateFormatter)
fun timeLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFormatter)
