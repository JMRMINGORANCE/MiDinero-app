package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import com.midinero.control.data.RecurringExpense
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Pago que se repite cada mes por un importe parecido y aún no está en Cargos fijos. */
data class SubscriptionCandidate(
    val merchant: String,
    val merchantKey: String,
    val amountCents: Long,
    val dayOfMonth: Int,
    val lastDate: LocalDate,
    val occurrences: Int,
    val categoryId: Long?,
) {
    val nextDate: LocalDate get() = Recurrence.next(lastDate, com.midinero.control.data.Frequency.MONTHLY, dayOfMonth)
}

/** Consejo o dato curioso sobre tus gastos. */
data class Tip(val emoji: String, val text: String, val categoryId: Long? = null, val important: Boolean = false)

object SubscriptionDetector {

    /**
     * Busca comercios con al menos 3 pagos en meses distintos, separados unos 30 días (25-35)
     * y con importes que no varían más de un 10 %.
     */
    fun detect(expenses: List<Expense>, recurring: List<RecurringExpense>, today: LocalDate = LocalDate.now()): List<SubscriptionCandidate> {
        val known = recurring.map { Categorizer.merchantKey(it.name) }.toSet()
        return expenses
            .filter { !it.isIncome && !it.isRefund && it.source != ExpenseSource.RECURRING }
            .groupBy { Categorizer.merchantKey(it.merchant) }
            .filterKeys { it.isNotBlank() && it !in known }
            .mapNotNull { (key, list) ->
                val sorted = list.sortedBy { it.timestamp }
                val dates = sorted.map { dateOf(it.timestamp) }
                val months = dates.map { YearMonth.from(it) }.toSet()
                if (sorted.size < 3 || months.size < 3) return@mapNotNull null
                // Solo los últimos pagos cuentan (hasta 6)
                val recent = sorted.takeLast(6)
                val recentDates = recent.map { dateOf(it.timestamp) }
                val gaps = recentDates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
                if (gaps.any { it !in 25..35 }) return@mapNotNull null
                val amounts = recent.map { it.amountCents }
                val avg = amounts.average()
                if (amounts.any { abs(it - avg) > avg * 0.10 }) return@mapNotNull null
                val last = recentDates.last()
                // Si hace más de 45 días del último pago, probablemente se canceló
                if (ChronoUnit.DAYS.between(last, today) > 45) return@mapNotNull null
                SubscriptionCandidate(
                    merchant = recent.last().merchant,
                    merchantKey = key,
                    amountCents = avg.toLong(),
                    dayOfMonth = recentDates.map { it.dayOfMonth }.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key,
                    lastDate = last,
                    occurrences = sorted.size,
                    categoryId = recent.last().categoryId,
                )
            }
            .sortedByDescending { it.amountCents }
    }
}

object Tips {

    /**
     * Consejos a partir del mes visto y los 3 anteriores. Solo se muestran los que tienen algo que decir.
     */
    fun generate(
        month: YearMonth,
        expenses: List<Expense>,
        categories: Map<Long, Category>,
        recurring: List<RecurringExpense>,
        today: LocalDate = LocalDate.now(),
    ): List<Tip> {
        val tips = mutableListOf<Tip>()
        val spending = expenses.filter { !it.isIncome }
        val current = spending.filter { monthOf(it.timestamp) == month }
        val previous = (1..3).map { month.minusMonths(it.toLong()) }
        val isCurrent = month == periodOf(today)
        // Para comparar un mes a medias, se escala al ritmo del mes
        val scale = if (isCurrent) month.lengthInDays().toDouble() / month.dayIndex(today).coerceAtLeast(1) else 1.0

        val byCatNow = current.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
        val byCatBefore = previous.map { m ->
            spending.filter { monthOf(it.timestamp) == m }.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
        }
        val monthsWithData = byCatBefore.count { it.isNotEmpty() }
        if (monthsWithData >= 2) {
            byCatNow.forEach { (catId, cents) ->
                val cat = catId?.let { categories[it] } ?: return@forEach
                val avg = byCatBefore.sumOf { it[catId] ?: 0L } / monthsWithData
                if (avg < 2000) return@forEach // menos de 20 €: no merece la pena
                val projected = (cents * scale).toLong()
                val pct = ((projected - avg) * 100 / avg).toInt()
                if (pct >= 30) {
                    tips += Tip(
                        "📈",
                        (if (isCurrent) tr("A este ritmo gastarás", "At this pace you'll spend") else tr("Gastaste", "You spent")) + tr(" un $pct % más de lo habitual en ${cat.name}", " $pct % more than usual on ${cat.name}"),
                        catId, important = pct >= 60,
                    )
                } else if (pct <= -30 && !isCurrent) {
                    tips += Tip("👏", tr("Gastaste un ${-pct} % menos de lo habitual en ${cat.name}", "You spent ${-pct} % less than usual on ${cat.name}"), catId)
                }
            }
        }

        PriceChanges.detect(expenses, today).take(2).forEach { c ->
            tips += Tip(
                "💸",
                tr("${c.merchant} ha subido de ${formatCents(c.oldCents)} a ${formatCents(c.newCents)} (+${c.percent} %)", "${c.merchant} went up from ${formatCents(c.oldCents)} to ${formatCents(c.newCents)} (+${c.percent} %)"),
                important = true,
            )
        }

        val delivery = current.filter { e -> !e.isRefund && CategoryNames.matches(e.categoryId?.let { categories[it]?.name }, com.midinero.control.data.DefaultCategories.COMIDA_DOMICILIO) }
        if (delivery.size >= 4) {
            tips += Tip("🛵", tr("Has pedido comida a domicilio ${delivery.size} veces este mes (${formatCents(delivery.sumOf { it.amountCents })})", "You've ordered food delivery ${delivery.size} times this month (${formatCents(delivery.sumOf { it.amountCents })})"))
        }

        val subscriptionsYear = recurring.filter { it.active && !it.isIncome }
            .filter { r -> CategoryNames.matches(r.categoryId?.let { categories[it]?.name }, com.midinero.control.data.DefaultCategories.SUSCRIPCIONES) }
            .sumOf { Recurrence.yearlyCents(it) }
        if (subscriptionsYear >= 10000) {
            tips += Tip("📺", tr("Tus suscripciones suman ${formatCents(subscriptionsYear)} al año. ¿Usas todas?", "Your subscriptions add up to ${formatCents(subscriptionsYear)} a year. Do you use them all?"))
        }

        if (isCurrent) {
            val daysSoFar = month.dayIndex(today)
            val spendDays = current.filter { !it.isRefund }.map { dateOf(it.timestamp) }.toSet()
            var streak = 0
            var d = today
            while (d >= month.startDate() && d !in spendDays) { streak++; d = d.minusDays(1) }
            if (streak >= 3) tips += Tip("🔥", tr("Llevas $streak días seguidos sin gastar", "$streak days in a row without spending"))
            val small = current.filter { it.amountCents in 1 until 500 }
            if (small.size >= 10 && daysSoFar > 0) {
                tips += Tip("☕", tr("${small.size} gastos pequeños (menos de 5 €) suman ${formatCents(small.sumOf { it.amountCents })} este mes", "${small.size} small expenses (under €5) add up to ${formatCents(small.sumOf { it.amountCents })} this month"))
            }
        }
        return tips.sortedByDescending { it.important }
    }
}
