package com.midinero.control.domain

import com.midinero.control.data.Expense
import java.time.LocalDate
import java.time.YearMonth

/** Situación de un presupuesto (global o de una categoría) en un mes. */
data class BudgetPace(
    val spentCents: Long,
    val limitCents: Long,
    /** Parte del mes transcurrida (0..1). 1 si el mes ya terminó. */
    val monthFraction: Float,
    /** Días que quedan incluyendo hoy (0 si el mes terminó). */
    val daysLeft: Int,
) {
    val ratio: Float get() = if (limitCents <= 0) 0f else spentCents.toFloat() / limitCents
    val remainingCents: Long get() = limitCents - spentCents

    /** Lo que se puede gastar cada día que queda sin pasarse. */
    val dailyAllowanceCents: Long? get() = if (daysLeft > 0 && remainingCents > 0) remainingCents / daysLeft else null

    /** true si se gasta más rápido de lo que permitiría repartir el límite por igual en el mes (con margen del 10 %). */
    val aheadOfPace: Boolean get() = monthFraction < 1f && ratio > monthFraction + 0.10f && ratio < 1f
}

object Budgets {

    fun pace(month: YearMonth, spentCents: Long, limitCents: Long, today: LocalDate = LocalDate.now()): BudgetPace {
        val current = periodOf(today)
        val length = month.lengthInDays()
        val (fraction, daysLeft) = when {
            month == current -> month.dayIndex(today).toFloat() / length to (length - month.dayIndex(today) + 1)
            month.isBefore(current) -> 1f to 0
            else -> 0f to length
        }
        return BudgetPace(spentCents, limitCents, fraction, daysLeft)
    }

    /**
     * Gasto medio mensual por categoría en los [months] meses anteriores a [before]
     * (solo gastos, sin ingresos). Sirve para sugerir límites.
     */
    fun monthlyAverageByCategory(expenses: List<Expense>, before: YearMonth, months: Int = 3): Map<Long, Long> {
        val from = before.minusMonths(months.toLong())
        val inWindow = expenses.filter { !it.isIncome }.filter { val m = monthOf(it.timestamp); !m.isBefore(from) && m.isBefore(before) }
        // Solo cuentan los meses con algún gasto: quien empieza con un mes de datos no recibe un límite a un tercio
        val activeMonths = inWindow.map { monthOf(it.timestamp) }.distinct().size.coerceIn(1, months)
        return inWindow
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } / activeMonths }
    }

    /**
     * Cuántos de los [months] meses anteriores a [before] se quedó cada categoría dentro de [limits].
     * Solo cuenta meses con algún movimiento en la app (para no premiar meses sin datos).
     */
    fun compliance(
        expenses: List<Expense>,
        limits: Map<Long, Long>,
        before: YearMonth,
        months: Int = 6,
    ): Map<Long, Pair<Int, Int>> {
        val spending = expenses.filter { !it.isIncome }
        val activeMonths = spending.map { monthOf(it.timestamp) }.toSet()
        val monthsToCheck = (1..months).map { before.minusMonths(it.toLong()) }.filter { it in activeMonths }
        val totals = spending.filter { it.categoryId != null }
            .groupBy { it.categoryId!! to monthOf(it.timestamp) }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        return limits.mapValues { (cat, limit) ->
            val ok = monthsToCheck.count { (totals[cat to it] ?: 0L) <= limit }
            ok to monthsToCheck.size
        }
    }

    /** Redondea una sugerencia hacia arriba a 5 € (o a 10 € por encima de 100 €) para que sea "redonda". */
    fun roundSuggestion(cents: Long): Long {
        val step = if (cents >= 10_000) 1_000 else 500
        return ((cents + step - 1) / step) * step
    }
}
