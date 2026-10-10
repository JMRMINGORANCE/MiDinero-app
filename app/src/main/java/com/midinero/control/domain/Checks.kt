package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Posibles pagos duplicados: mismo importe con menos de [windowMinutes] minutos de diferencia y al menos uno
 * apuntado solo (detectado o importado). Pasa cuando Google Wallet y el banco avisan del mismo pago con textos distintos.
 */
object Duplicates {
    fun find(expenses: List<Expense>, windowMinutes: Long = 10, ignored: Set<Long> = emptySet()): Set<Long> {
        val window = windowMinutes * 60_000
        val spending = expenses.filter { !it.isIncome && !it.isRefund && it.id !in ignored }.sortedBy { it.timestamp }
        val found = mutableSetOf<Long>()
        for (i in spending.indices) {
            val a = spending[i]
            var j = i + 1
            while (j < spending.size && spending[j].timestamp - a.timestamp <= window) {
                val b = spending[j]
                val automatic = a.source.isAutomatic() || b.source.isAutomatic()
                // Dos filas del extracto del banco son dos movimientos distintos (dos cafés), aunque lleven la misma hora
                val bothImported = a.source == com.midinero.control.data.ExpenseSource.IMPORTED && b.source == com.midinero.control.data.ExpenseSource.IMPORTED
                if (a.amountCents == b.amountCents && automatic && !bothImported && a.recurringId == null && b.recurringId == null) {
                    found += a.id
                    found += b.id
                }
                j++
            }
        }
        // Movimientos importados del banco: llevan las 12:00 como hora, así que se comparan por día
        for (a in spending) {
            if (a.source != com.midinero.control.data.ExpenseSource.IMPORTED || a.recurringId != null) continue
            val day = dateOf(a.timestamp)
            for (b in spending) {
                if (b.id == a.id || b.source == com.midinero.control.data.ExpenseSource.IMPORTED || b.recurringId != null) continue
                if (b.amountCents == a.amountCents && dateOf(b.timestamp) == day) {
                    found += a.id
                    found += b.id
                }
            }
        }
        return found
    }

    private fun ExpenseSource.isAutomatic() = this == ExpenseSource.GOOGLE_PAY || this == ExpenseSource.IMPORTED
}

/** Un pago que se repite (suscripción, cuota) ha subido de precio. */
data class PriceChange(val merchant: String, val merchantKey: String, val oldCents: Long, val newCents: Long, val date: LocalDate) {
    val percent: Int get() = ((newCents - oldCents) * 100 / oldCents).toInt()
}

object PriceChanges {
    /**
     * Pagos a un mismo comercio más o menos cada mes (25 a 35 días) que llevaban al menos dos veces el mismo importe
     * y el último es al menos un 3 % (y 0,30 €) más caro. Solo cuenta si el último pago es de los últimos [recentDays] días.
     */
    fun detect(expenses: List<Expense>, today: LocalDate = LocalDate.now(), recentDays: Long = 45): List<PriceChange> =
        expenses.filter { !it.isIncome && !it.isRefund && it.recurringId == null }
            .groupBy { Categorizer.merchantKey(it.merchant) }
            .filterKeys { it.isNotBlank() }
            .mapNotNull { (key, list) ->
                val sorted = list.sortedBy { it.timestamp }
                if (sorted.size < 3) return@mapNotNull null
                val last = sorted[sorted.size - 1]
                val prev = sorted[sorted.size - 2]
                val before = sorted[sorted.size - 3]
                val lastDate = dateOf(last.timestamp)
                if (ChronoUnit.DAYS.between(lastDate, today) > recentDays) return@mapNotNull null
                val gap1 = ChronoUnit.DAYS.between(dateOf(before.timestamp), dateOf(prev.timestamp))
                val gap2 = ChronoUnit.DAYS.between(dateOf(prev.timestamp), lastDate)
                val monthly = gap1 in 25..35 && gap2 in 25..35
                val stable = abs(prev.amountCents - before.amountCents) <= prev.amountCents / 100
                val rise = last.amountCents - prev.amountCents
                if (monthly && stable && rise >= 30 && rise * 100 >= prev.amountCents * 3) {
                    PriceChange(last.merchant, key, prev.amountCents, last.amountCents, lastDate)
                } else null
            }
            .sortedByDescending { it.date }
}

/** Cómo va una categoría este mes frente a su media de los meses anteriores. */
data class CategoryComparison(
    val categoryId: Long?,
    val currentCents: Long,
    /** Lo que se gastará a fin de mes a este ritmo (igual que [currentCents] en un mes ya cerrado). */
    val projectedCents: Long,
    val averageCents: Long,
) {
    /** Diferencia de lo previsto frente a la media, en %. */
    val deltaPercent: Int get() = if (averageCents <= 0) 0 else ((projectedCents - averageCents) * 100 / averageCents).toInt()
}

object CategoryAverages {
    /**
     * Compara cada categoría del [month] con su media de los [months] meses anteriores que tienen algún gasto.
     * En el mes en curso se compara lo previsto a fin de mes (al ritmo actual). Ordenado por la desviación en euros.
     */
    fun compare(month: YearMonth, expenses: List<Expense>, today: LocalDate = LocalDate.now(), months: Int = 6): List<CategoryComparison> {
        val spending = expenses.filter { !it.isIncome }
        val byMonth = spending.groupBy { monthOf(it.timestamp) }
        val previous = (1..months).map { month.minusMonths(it.toLong()) }.filter { !byMonth[it].isNullOrEmpty() }
        if (previous.isEmpty()) return emptyList()
        val isCurrent = month == periodOf(today)
        val scale = if (isCurrent) month.lengthInDays().toDouble() / month.dayIndex(today).coerceAtLeast(1) else 1.0
        val current = byMonth[month].orEmpty().groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
        val averages = previous.flatMap { byMonth[it].orEmpty() }.groupBy { it.categoryId }
            .mapValues { (_, l) -> l.sumOf { it.amountCents } / previous.size }
        return (current.keys + averages.keys).distinct().map { cat ->
            val now = current[cat] ?: 0L
            CategoryComparison(cat, now, (now * scale).toLong(), averages[cat] ?: 0L)
        }
            .filter { it.averageCents >= 1000 || it.projectedCents >= 1000 }
            .sortedByDescending { abs(it.projectedCents - it.averageCents) }
    }
}

/** Presupuesto que se arrastra: lo que sobró el mes anterior se suma al límite de este. */
object Rollover {
    /**
     * Lo que se suma al límite: lo que no se gastó del [limitCents] el mes anterior. Si ese mes no hubo ningún
     * movimiento en la app, no se arrastra nada (para no duplicar el límite al empezar a usarla).
     */
    fun bonus(limitCents: Long, previousSpentCents: Long, previousMonthHadActivity: Boolean): Long =
        if (!previousMonthHadActivity || limitCents <= 0) 0L else (limitCents - previousSpentCents).coerceAtLeast(0L)
}

/** Gasto acumulado día a día de un mes (hasta hoy en el mes en curso), para las curvas. */
object DailyCurve {
    fun cumulative(month: YearMonth, expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<Long> {
        val days = month.lengthInDays()
        val last = if (month == periodOf(today)) month.dayIndex(today) else days
        val perDay = LongArray(days)
        expenses.filter { !it.isIncome && monthOf(it.timestamp) == month }.forEach { e ->
            val d = month.dayIndex(dateOf(e.timestamp)) - 1
            if (d in 0 until days) perDay[d] += e.amountCents
        }
        var run = 0L
        return perDay.take(last.coerceIn(1, days)).map { run += it; run }
    }
}
