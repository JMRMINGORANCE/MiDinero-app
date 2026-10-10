package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.Frequency
import com.midinero.control.data.RecurringExpense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Un comercio en el ranking: cuánto, cuántas veces, ticket medio y última visita. */
data class MerchantStat(
    val key: String,
    val name: String,
    val totalCents: Long,
    val count: Int,
    val lastTimestamp: Long,
    val categoryId: Long?,
) {
    val averageCents: Long get() = if (count == 0) 0 else totalCents / count
}

object MerchantRanking {
    /**
     * Comercios con gastos entre [from] y [to] (incluidos), de más a menos gasto. Se agrupan por comercio normalizado
     * («MERCADONA 1234» = «Mercadona»); el nombre que se muestra es el del gasto más reciente.
     */
    fun rank(expenses: List<Expense>, from: LocalDate, to: LocalDate): List<MerchantStat> =
        expenses.asSequence()
            .filter { !it.isIncome && it.recurringId == null }
            .filter { val d = dateOf(it.timestamp); !d.isBefore(from) && !d.isAfter(to) }
            .groupBy { Categorizer.merchantKey(it.merchant).ifBlank { it.merchant.lowercase() } }
            .map { (key, list) ->
                val latest = list.maxBy { it.timestamp }
                MerchantStat(
                    key = key,
                    name = latest.merchant,
                    totalCents = list.sumOf { it.amountCents },
                    // Una devolución resta del total, pero no es una visita más
                    count = list.count { !it.isRefund },
                    lastTimestamp = latest.timestamp,
                    categoryId = list.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value }?.key,
                )
            }
            .sortedByDescending { it.totalCents }
}

/** Gasto medio de cada día de la semana. */
data class WeekdayPattern(
    /** Lunes..domingo: media de lo gastado ese día de la semana (contando los días sin gastos). */
    val averages: Map<DayOfWeek, Long>,
) {
    val busiest: DayOfWeek? get() = averages.filterValues { it > 0 }.maxByOrNull { it.value }?.key
}

object Weekdays {
    /**
     * Patrón semanal de los días entre [from] y [to] (incluidos). Hacen falta al menos 14 días para que diga algo:
     * con menos, devuelve null.
     */
    fun pattern(expenses: List<Expense>, from: LocalDate, to: LocalDate): WeekdayPattern? {
        val days = ChronoUnit.DAYS.between(from, to).toInt() + 1
        if (days < 14) return null
        val totals = expenses.filter { !it.isIncome && it.recurringId == null }
            .groupBy { dateOf(it.timestamp) }
            .filterKeys { !it.isBefore(from) && !it.isAfter(to) }
            .mapValues { (_, l) -> l.sumOf { it.amountCents } }
        val counts = mutableMapOf<DayOfWeek, Int>()
        val sums = mutableMapOf<DayOfWeek, Long>()
        var d = from
        while (!d.isAfter(to)) {
            counts.merge(d.dayOfWeek, 1, Int::plus)
            sums.merge(d.dayOfWeek, totals[d] ?: 0L, Long::plus)
            d = d.plusDays(1)
        }
        if (totals.isEmpty()) return null
        return WeekdayPattern(DayOfWeek.entries.associateWith { (sums[it] ?: 0L) / (counts[it] ?: 1) })
    }
}

/** Comparación del año con el anterior. En el año en curso, con lo que llevaba a estas alturas el año pasado. */
object YearCompare {
    fun previousCents(year: Int, expenses: List<Expense>, today: LocalDate = LocalDate.now()): Long {
        val spending = expenses.filter { !it.isIncome }
        val limit = if (year == today.year) today.minusYears(1) else LocalDate.of(year - 1, 12, 31)
        return spending.filter {
            val d = dateOf(it.timestamp)
            d.year == year - 1 && !d.isAfter(limit)
        }.sumOf { it.amountCents }
    }

    fun currentCents(year: Int, expenses: List<Expense>, today: LocalDate = LocalDate.now()): Long =
        expenses.filter { !it.isIncome && dateOf(it.timestamp).year == year && !dateOf(it.timestamp).isAfter(today) }
            .sumOf { it.amountCents }
}

/** Renovaciones anuales: los cargos que se cobran una vez al año pillan desprevenido. */
object Renewals {
    /** Cargos anuales (y trimestrales) activos que se cobran en los próximos [days] días, por fecha. */
    fun upcoming(recurring: List<RecurringExpense>, today: LocalDate = LocalDate.now(), days: Long = 60): List<RecurringExpense> =
        recurring.filter { it.active && !it.isIncome && it.frequency in setOf(Frequency.YEARLY, Frequency.QUARTERLY) }
            .filter { val due = LocalDate.ofEpochDay(it.nextDueEpochDay); !due.isBefore(today) && !due.isAfter(today.plusDays(days)) }
            .sortedBy { it.nextDueEpochDay }

    /** Cargos anuales que se renuevan exactamente dentro de [days] días (para avisar con tiempo de darse de baja). */
    fun dueIn(recurring: List<RecurringExpense>, days: Long, today: LocalDate = LocalDate.now()): List<RecurringExpense> =
        recurring.filter { it.active && !it.isIncome && it.frequency == Frequency.YEARLY && it.nextDueEpochDay == today.plusDays(days).toEpochDay() }
}

/** Con los importes ocultos, el presupuesto se enseña como porcentaje («64 %»), que no dice cuánto dinero es. */
object PrivateShare {
    fun percent(spentCents: Long, budgetCents: Long): Int? =
        if (budgetCents <= 0) null else (spentCents * 100 / budgetCents).toInt().coerceAtLeast(0)

    fun label(spentCents: Long, budgetCents: Long): String? = percent(spentCents, budgetCents)?.let { "$it %" }
}
