package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Resumen de un mes para la pantalla de estadísticas. Todo en céntimos. */
data class MonthStats(
    val month: YearMonth,
    val totalCents: Long,
    val count: Int,
    val previousTotalCents: Long,
    /** Gasto por categoría (null = sin categoría), de mayor a menor. */
    val byCategory: List<Pair<Long?, Long>>,
    /** Total de los últimos meses, del más antiguo al [month]. */
    val trend: List<Pair<YearMonth, Long>>,
    val dailyAverageCents: Long,
    /** Estimación de cómo acabará el mes; null si el mes ya terminó. */
    val projectionCents: Long?,
    val topMerchants: List<Pair<String, Long>>,
    /** Gasto por día de la semana, lunes = índice 0. */
    val byWeekday: List<Long>,
    val biggest: Expense?,
    val recurringCents: Long,
    /** Ingresos del mes y su evolución (mismos meses que [trend]). */
    val incomeCents: Long = 0,
    val incomeTrend: List<Pair<YearMonth, Long>> = emptyList(),
    /** Cargos fijos que aún se cobrarán antes de que acabe el mes (solo en el mes actual). */
    val pendingRecurringCents: Long = 0,
    /** Cada categoría frente a su media de los 6 meses anteriores. */
    val vsAverage: List<CategoryComparison> = emptyList(),
) {
    /** Ingresos menos gastos. */
    val balanceCents: Long get() = incomeCents - totalCents

    /** % de los ingresos que se ahorra; null si no hay ingresos apuntados. */
    val savingsRate: Int?
        get() = if (incomeCents <= 0) null else ((balanceCents * 100.0) / incomeCents).toInt()

    /** Variación frente al mes anterior en %, o null si el mes anterior no tuvo gastos. */
    val changeVsPrevious: Int?
        get() = if (previousTotalCents <= 0) null
        else (((totalCents - previousTotalCents) * 100.0) / previousTotalCents).toInt()
}

object Stats {

    /**
     * Calcula las estadísticas de [month]. [expenses] debe incluir al menos los [trendMonths]
     * meses que terminan en [month]; lo demás se ignora.
     */
    fun compute(
        month: YearMonth,
        expenses: List<Expense>,
        today: LocalDate = LocalDate.now(),
        trendMonths: Int = 6,
        recurringCharges: List<com.midinero.control.data.RecurringExpense> = emptyList(),
    ): MonthStats {
        val incomesByMonth = expenses.filter { it.isIncome }.groupBy { monthOf(it.timestamp) }
        val byMonth = expenses.filter { !it.isIncome }.groupBy { monthOf(it.timestamp) }
        val current = byMonth[month].orEmpty()
        val total = current.sumOf { it.amountCents }
        val previous = byMonth[month.minusMonths(1)].orEmpty().sumOf { it.amountCents }

        val byCategory = current.groupBy { it.categoryId }
            .map { (cat, list) -> cat to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }

        val trend = (trendMonths - 1 downTo 0).map { back ->
            val ym = month.minusMonths(back.toLong())
            ym to byMonth[ym].orEmpty().sumOf { it.amountCents }
        }

        val thisMonth = periodOf(today)
        val daysElapsed = when {
            month == thisMonth -> month.dayIndex(today)
            month.isBefore(thisMonth) -> month.lengthInDays()
            else -> 0
        }
        val dailyAverage = if (daysElapsed > 0) total / daysElapsed else 0L
        // Previsión: lo fijo ya apuntado cuenta una vez, se suman los cargos fijos que faltan por llegar
        // y lo variable se extrapola al ritmo actual.
        val recurring = current.filter { it.source == ExpenseSource.RECURRING }.sumOf { it.amountCents }
        val pending = if (month == thisMonth) pendingCharges(recurringCharges, today, month.endDateExclusive()) else 0L
        val projection = if (month == thisMonth && daysElapsed > 0) {
            val variable = total - recurring
            recurring + variable * month.lengthInDays() / daysElapsed + pending
        } else null

        val topMerchants = current
            .groupBy { Categorizer.merchantKey(it.merchant).ifBlank { it.merchant.lowercase() } }
            .map { (_, list) -> list.first().merchant to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
            .take(5)

        val weekday = LongArray(7)
        current.forEach { weekday[dateOf(it.timestamp).dayOfWeek.value - DayOfWeek.MONDAY.value] += it.amountCents }

        return MonthStats(
            month = month,
            totalCents = total,
            count = current.size,
            previousTotalCents = previous,
            byCategory = byCategory,
            trend = trend,
            dailyAverageCents = dailyAverage,
            projectionCents = projection,
            topMerchants = topMerchants,
            byWeekday = weekday.toList(),
            biggest = current.maxByOrNull { it.amountCents },
            recurringCents = recurring,
            incomeCents = incomesByMonth[month].orEmpty().sumOf { it.amountCents },
            incomeTrend = trend.map { (ym, _) -> ym to incomesByMonth[ym].orEmpty().sumOf { it.amountCents } },
            pendingRecurringCents = pending,
            vsAverage = CategoryAverages.compare(month, expenses, today),
        )
    }

    /** Suma de los cargos fijos (gastos activos) que caen desde [today] hasta antes de [end]. */
    fun pendingCharges(charges: List<com.midinero.control.data.RecurringExpense>, today: LocalDate, end: LocalDate): Long =
        charges.filter { it.active && !it.isIncome }.sumOf { r ->
            var d = LocalDate.ofEpochDay(r.nextDueEpochDay)
            var sum = 0L
            var count = 0
            while (d < end && (r.remainingCharges == null || count < r.remainingCharges) && count < 60) {
                if (d >= today) sum += r.amountCents
                d = Recurrence.next(d, r.frequency, r.dayOfMonth, r.weekdays)
                count++
            }
            sum
        }
}
