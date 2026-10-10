package com.midinero.control.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * "Mes" de gastos. Por defecto es el mes natural, pero se puede hacer que empiece otro día
 * (por ejemplo el día que se cobra la nómina).
 *
 * Con inicio el día 25, "septiembre" va del 25 de agosto al 24 de septiembre: el periodo toma
 * el nombre del mes en el que caen la mayoría de sus días (inicio > 15 -> mes siguiente).
 */
object Period {
    /** Día en que empieza cada periodo (1..28). */
    @Volatile
    var startDay: Int = 1
        set(value) { field = value.coerceIn(1, 28) }
}

fun YearMonth.startDate(): LocalDate {
    val d = Period.startDay
    return when {
        d == 1 -> atDay(1)
        d > 15 -> minusMonths(1).atDay(d)
        else -> atDay(d)
    }
}

/** Primer día del periodo siguiente (el final, sin incluir). */
fun YearMonth.endDateExclusive(): LocalDate = plusMonths(1).startDate()

fun YearMonth.lengthInDays(): Int = ChronoUnit.DAYS.between(startDate(), endDateExclusive()).toInt()

/** Qué número de día del periodo es [date] (1 = primer día). */
fun YearMonth.dayIndex(date: LocalDate): Int = ChronoUnit.DAYS.between(startDate(), date).toInt() + 1

/** Periodo al que pertenece una fecha. */
fun periodOf(date: LocalDate): YearMonth {
    val d = Period.startDay
    val base = YearMonth.from(date)
    return when {
        d == 1 -> base
        d > 15 -> if (date.dayOfMonth >= d) base.plusMonths(1) else base
        else -> if (date.dayOfMonth >= d) base else base.minusMonths(1)
    }
}

fun currentPeriod(today: LocalDate = LocalDate.now()): YearMonth = periodOf(today)

private val rangeFormat get() = Lang.dateFormat("d MMM")

/** "25 ago – 24 sept" cuando el periodo no es el mes natural; null si lo es. */
fun YearMonth.rangeLabel(): String? {
    if (Period.startDay == 1) return null
    return "${startDate().format(rangeFormat)} – ${endDateExclusive().minusDays(1).format(rangeFormat)}"
}
