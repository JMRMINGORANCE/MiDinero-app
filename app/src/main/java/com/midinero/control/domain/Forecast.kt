package com.midinero.control.domain

import com.midinero.control.data.RecurringExpense
import java.time.LocalDate

/**
 * Cómo acabará el mes si sigues al ritmo actual: gasto previsto frente al presupuesto y lo que sobrará (o faltará)
 * contando tus ingresos y los ingresos fijos que aún tienen que llegar. Todo en céntimos.
 */
data class MonthForecast(
    val projectedSpendCents: Long,
    /** Presupuesto del mes o null si no tiene. */
    val budgetCents: Long?,
    val incomeCents: Long,
    val pendingIncomeCents: Long,
    val daysLeft: Int,
) {
    /** Lo que se pasará del presupuesto (> 0) o lo que sobrará de él (< 0); null sin presupuesto. */
    val vsBudgetCents: Long? get() = budgetCents?.let { projectedSpendCents - it }

    /** Ingresos (los apuntados más los fijos pendientes) menos el gasto previsto; null si no hay ingresos. */
    val balanceCents: Long?
        get() = (incomeCents + pendingIncomeCents).takeIf { it > 0 }?.let { it - projectedSpendCents }
}

object Forecast {
    /** null si el mes no es el actual o aún no hay gastos con los que estimar. */
    fun of(stats: MonthStats, budgetCents: Long, charges: List<RecurringExpense>, today: LocalDate = LocalDate.now()): MonthForecast? {
        val projected = stats.projectionCents ?: return null
        if (stats.totalCents <= 0) return null
        val end = stats.month.endDateExclusive()
        return MonthForecast(
            projectedSpendCents = projected,
            budgetCents = budgetCents.takeIf { it > 0 },
            incomeCents = stats.incomeCents,
            pendingIncomeCents = pendingIncome(charges, today, end),
            daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt().coerceAtLeast(0),
        )
    }

    /** Ingresos fijos activos (nómina…) que aún llegarán desde [today] hasta antes de [end]. */
    fun pendingIncome(charges: List<RecurringExpense>, today: LocalDate, end: LocalDate): Long =
        Stats.pendingCharges(charges.filter { it.isIncome }.map { it.copy(isIncome = false) }, today, end)
}
