package com.midinero.control.domain

import com.midinero.control.data.Expense
import java.time.LocalDate
import java.time.YearMonth

/** Resumen de una categoría: evolución, media y comercios principales. */
data class CategoryInsight(
    val month: YearMonth,
    val totalCents: Long,
    val previousCents: Long,
    val averageCents: Long,
    val trend: List<Pair<YearMonth, Long>>,
    val topMerchants: List<Pair<String, Long>>,
    val monthExpenses: List<Expense>,
)

/** Historial de un comercio. */
data class MerchantInsight(
    val displayName: String,
    val totalCents: Long,
    val count: Int,
    val averageTicketCents: Long,
    val first: LocalDate?,
    val last: LocalDate?,
    val trend: List<Pair<YearMonth, Long>>,
    /** Categoría más usada para este comercio. */
    val categoryId: Long?,
    val recent: List<Expense>,
)

object Insights {

    private fun trendOf(expenses: List<Expense>, month: YearMonth, months: Int): List<Pair<YearMonth, Long>> {
        val byMonth = expenses.groupBy { monthOf(it.timestamp) }
        return (months - 1 downTo 0).map { back ->
            val ym = month.minusMonths(back.toLong())
            ym to byMonth[ym].orEmpty().sumOf { it.amountCents }
        }
    }

    fun category(categoryId: Long, month: YearMonth, expenses: List<Expense>, months: Int = 6): CategoryInsight {
        val mine = expenses.filter { it.categoryId == categoryId && !it.isIncome }
        val trend = trendOf(mine, month, months)
        val current = mine.filter { monthOf(it.timestamp) == month }
        // Media de los meses anteriores con gasto (el mes en curso está incompleto).
        val previousMonths = trend.dropLast(1).map { it.second }.filter { it > 0 }
        return CategoryInsight(
            month = month,
            totalCents = trend.last().second,
            previousCents = trend.getOrNull(trend.size - 2)?.second ?: 0L,
            averageCents = if (previousMonths.isEmpty()) 0L else previousMonths.sum() / previousMonths.size,
            trend = trend,
            topMerchants = current
                .groupBy { Categorizer.merchantKey(it.merchant).ifBlank { it.merchant.lowercase() } }
                .map { (_, list) -> list.first().merchant to list.sumOf { it.amountCents } }
                .sortedByDescending { it.second }
                .take(5),
            monthExpenses = current.sortedByDescending { it.timestamp },
        )
    }

    fun merchant(merchantKey: String, expenses: List<Expense>, month: YearMonth = currentPeriod(), months: Int = 6): MerchantInsight {
        val mine = expenses
            .filter { !it.isIncome && Categorizer.merchantKey(it.merchant) == merchantKey }
            .sortedByDescending { it.timestamp }
        val total = mine.sumOf { it.amountCents }
        return MerchantInsight(
            displayName = mine.firstOrNull()?.merchant ?: merchantKey,
            totalCents = total,
            count = mine.size,
            averageTicketCents = if (mine.isEmpty()) 0L else total / mine.size,
            first = mine.lastOrNull()?.let { dateOf(it.timestamp) },
            last = mine.firstOrNull()?.let { dateOf(it.timestamp) },
            trend = trendOf(mine, month, months),
            categoryId = mine.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
            recent = mine.take(30),
        )
    }
}

/** Resumen de un año natural de periodos (enero..diciembre). */
data class YearSummary(
    val year: Int,
    val totalCents: Long,
    val incomeCents: Long,
    val byMonth: List<Pair<java.time.YearMonth, Long>>,
    val byCategory: List<Pair<Long?, Long>>,
    val topMerchants: List<Pair<String, Long>>,
    /** Media de los meses con algún gasto. */
    val monthlyAverageCents: Long,
    val maxMonth: java.time.YearMonth?,
    val minMonth: java.time.YearMonth?,
    val count: Int,
    /** Gasto de cada día del año (para el mapa de calor). */
    val daily: Map<LocalDate, Long> = emptyMap(),
    /** El año anterior: entero si el año ya acabó, o hasta la misma fecha si es el año en curso. */
    val previousCents: Long = 0,
) {
    val balanceCents: Long get() = incomeCents - totalCents

    /** Diferencia en % con el año anterior (null si no hay datos de ese año). */
    val versusPreviousPercent: Int? get() = if (previousCents <= 0) null else ((totalCents - previousCents) * 100 / previousCents).toInt()
}

object SpendingCalendar {
    /** Gasto por día dentro del periodo [month] (solo gastos). */
    fun dailyTotals(month: java.time.YearMonth, expenses: List<Expense>): Map<LocalDate, Long> =
        expenses.filter { !it.isIncome && monthOf(it.timestamp) == month }
            .groupBy { dateOf(it.timestamp) }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }

    /**
     * Nivel de intensidad 0..4 de cada día, por cuartiles de los días con gasto
     * (así un solo gasto enorme no deja todos los demás días en blanco).
     */
    fun levels(totals: Map<LocalDate, Long>): Map<LocalDate, Int> {
        val values = totals.values.filter { it > 0 }.sorted()
        if (values.isEmpty()) return emptyMap()
        fun q(p: Double) = values[((values.size - 1) * p).toInt()]
        val q1 = q(0.25); val q2 = q(0.5); val q3 = q(0.75)
        return totals.mapValues { (_, v) ->
            when {
                v <= 0 -> 0
                v <= q1 -> 1
                v <= q2 -> 2
                v <= q3 -> 3
                else -> 4
            }
        }
    }

    /** Resumen del [year]. [expenses] puede incluir el año anterior para compararlo. */
    fun year(year: Int, expenses: List<Expense>, today: LocalDate = LocalDate.now()): YearSummary {
        val months = (1..12).map { java.time.YearMonth.of(year, it) }
        val inYear = expenses.filter { monthOf(it.timestamp).year == year }
        val spending = inYear.filter { !it.isIncome }
        val byMonth = months.map { m -> m to spending.filter { monthOf(it.timestamp) == m }.sumOf { it.amountCents } }
        val withData = byMonth.filter { it.second > 0 }
        return YearSummary(
            year = year,
            totalCents = spending.sumOf { it.amountCents },
            incomeCents = inYear.filter { it.isIncome }.sumOf { it.amountCents },
            byMonth = byMonth,
            byCategory = spending.groupBy { it.categoryId }.map { (k, v) -> k to v.sumOf { it.amountCents } }.sortedByDescending { it.second },
            topMerchants = spending
                .groupBy { Categorizer.merchantKey(it.merchant).ifBlank { it.merchant.lowercase() } }
                .map { (_, list) -> list.first().merchant to list.sumOf { it.amountCents } }
                .sortedByDescending { it.second }
                .take(5),
            monthlyAverageCents = if (withData.isEmpty()) 0 else withData.sumOf { it.second } / withData.size,
            maxMonth = withData.maxByOrNull { it.second }?.first,
            minMonth = withData.minByOrNull { it.second }?.first,
            count = spending.size,
            daily = spending.groupBy { dateOf(it.timestamp) }.mapValues { (_, l) -> l.sumOf { it.amountCents } },
            previousCents = YearCompare.previousCents(year, expenses, today),
        )
    }
}
