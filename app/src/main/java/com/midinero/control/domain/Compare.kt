package com.midinero.control.domain

import com.midinero.control.data.Expense
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Tramo de fechas, con los dos días incluidos. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "end antes que start" } }

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    /** El mismo tramo un año antes (29 de febrero → 28). */
    fun previousYear(): DateRange = DateRange(start.minusYears(1), end.minusYears(1))

    /** El tramo de la misma duración justo antes de este. */
    fun previousSpan(): DateRange = DateRange(start.minusDays(days), start.minusDays(1))

    /** Meses naturales que toca, del primero al último. */
    fun months(): List<YearMonth> {
        val list = mutableListOf<YearMonth>()
        var m = YearMonth.from(start)
        val last = YearMonth.from(end)
        while (!m.isAfter(last)) { list += m; m = m.plusMonths(1) }
        return list
    }

    fun label(): String {
        val f = Lang.dateFormat("d MMM yyyy")
        return if (start == end) start.format(f) else "${start.format(f)} – ${end.format(f)}"
    }

    companion object {
        fun year(year: Int) = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        fun month(month: YearMonth) = DateRange(month.atDay(1), month.atEndOfMonth())
    }
}

/** Gasto de una categoría en los dos tramos comparados. [categoryId] null = sin categoría. */
data class CompareRow(val categoryId: Long?, val aCents: Long, val bCents: Long) {
    val diffCents: Long get() = aCents - bCents
    /** Cambio de B a A en tanto por uno; null si en B no hubo gasto. */
    val change: Double? get() = if (bCents > 0) diffCents.toDouble() / bCents else null
}

object Compare {
    private fun spending(expenses: List<Expense>, range: DateRange, categoryIds: Set<Long?>?) =
        expenses.filter { !it.isIncome && (categoryIds == null || it.categoryId in categoryIds) && range.contains(dateOf(it.timestamp)) }

    fun total(expenses: List<Expense>, range: DateRange, categoryIds: Set<Long?>? = null): Long =
        spending(expenses, range, categoryIds).sumOf { it.amountCents }

    /** Gasto por categoría en A y en B, de la que más gastó (en cualquiera de los dos) a la que menos. */
    fun byCategory(expenses: List<Expense>, a: DateRange, b: DateRange?, categoryIds: Set<Long?>? = null): List<CompareRow> {
        val inA = spending(expenses, a, categoryIds).groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
        val inB = if (b == null) emptyMap() else spending(expenses, b, categoryIds).groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
        return (inA.keys + inB.keys).map { CompareRow(it, inA[it] ?: 0, inB[it] ?: 0) }
            .filter { it.aCents != 0L || it.bCents != 0L }
            .sortedByDescending { maxOf(it.aCents, it.bCents) }
    }

    /** Gasto de cada mes natural del tramo (los meses sin gasto salen a 0). */
    fun monthly(expenses: List<Expense>, range: DateRange, categoryIds: Set<Long?>? = null): List<Pair<YearMonth, Long>> {
        val byMonth = spending(expenses, range, categoryIds).groupBy { YearMonth.from(dateOf(it.timestamp)) }
        return range.months().map { m -> m to (byMonth[m]?.sumOf { it.amountCents } ?: 0L) }
    }
}
