package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import java.time.LocalDate
import java.time.YearMonth

enum class ChallengeType {
    /** N días sin gastos en el mes (los cargos fijos no cuentan). */
    NO_SPEND_DAYS,
    /** Este mes, gastar menos de X € en una categoría. */
    CATEGORY_CAP,
    /** N días seguidos sin gastar en una categoría. */
    CATEGORY_STREAK,
}

/** Reto de ahorro. Los mensuales vuelven a empezar cada mes. [target]: días, o céntimos en [ChallengeType.CATEGORY_CAP]. */
data class Challenge(
    val id: String,
    val type: ChallengeType,
    val target: Long,
    val categoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class ChallengeState { IN_PROGRESS, COMPLETED, FAILED }

data class ChallengeProgress(
    val challenge: Challenge,
    val current: Long,
    val state: ChallengeState,
    /** Si hay un logro que avisar: identifica cada vez que se cumple (mes o racha), para avisar una sola vez. */
    val achievementKey: String?,
    /** Mensuales: el mes anterior se cumplió. */
    val wonLastMonth: Boolean = false,
) {
    val ratio: Float get() = if (challenge.target <= 0) 0f else (current.toFloat() / challenge.target).coerceIn(0f, 1f)
}

object Challenges {

    fun evaluate(c: Challenge, expenses: List<Expense>, today: LocalDate = LocalDate.now()): ChallengeProgress {
        val period = periodOf(today)
        val spending = expenses.filter { !it.isIncome && it.amountCents > 0 }
        return when (c.type) {
            ChallengeType.NO_SPEND_DAYS -> {
                val start = period.startDate()
                val spentDays = spending.filter { it.source != ExpenseSource.RECURRING }
                    .map { dateOf(it.timestamp) }.filter { it >= start && it <= today }.toSet()
                // Hoy solo cuenta cuando acaba: se cuentan los días ya cerrados
                val closed = generateSequence(start) { it.plusDays(1) }.takeWhile { it < today }.count { it !in spentDays }.toLong()
                val left = java.time.temporal.ChronoUnit.DAYS.between(today, period.endDateExclusive())
                val state = when {
                    closed >= c.target -> ChallengeState.COMPLETED
                    closed + left < c.target -> ChallengeState.FAILED
                    else -> ChallengeState.IN_PROGRESS
                }
                ChallengeProgress(c, closed, state, if (state == ChallengeState.COMPLETED) "${c.id}:$period" else null)
            }
            ChallengeType.CATEGORY_CAP -> {
                fun spentIn(ym: YearMonth) = spending.filter { it.categoryId == c.categoryId && periodOf(dateOf(it.timestamp)) == ym }.sumOf { it.amountCents }
                val spent = spentIn(period)
                // El mes anterior cuenta si el reto ya existía cuando empezó y no se pasó del límite
                val last = period.minusMonths(1)
                val lastWon = c.createdAt <= last.startDate().atStartOfDay(zone).toInstant().toEpochMilli() && spentIn(last) < c.target
                val state = if (spent >= c.target) ChallengeState.FAILED else ChallengeState.IN_PROGRESS
                ChallengeProgress(c, spent, state, if (lastWon) "${c.id}:$last" else null, wonLastMonth = lastWon)
            }
            ChallengeType.CATEGORY_STREAK -> {
                val lastInCategory = spending.filter { it.categoryId == c.categoryId }.maxOfOrNull { dateOf(it.timestamp) }
                val created = dateOf(c.createdAt)
                val from = listOfNotNull(lastInCategory?.plusDays(1), created).max()
                val streak = java.time.temporal.ChronoUnit.DAYS.between(from, today).coerceAtLeast(0)
                val state = if (streak >= c.target) ChallengeState.COMPLETED else ChallengeState.IN_PROGRESS
                ChallengeProgress(c, streak, state, if (state == ChallengeState.COMPLETED) "${c.id}:${from.toEpochDay()}" else null)
            }
        }
    }

    fun evaluateAll(list: List<Challenge>, expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<ChallengeProgress> =
        list.map { evaluate(it, expenses, today) }

    /** Texto del reto («5 días sin gastos este mes»). */
    fun title(c: Challenge, categoryName: String?): String = when (c.type) {
        ChallengeType.NO_SPEND_DAYS -> tr("${c.target} días sin gastos este mes", "${c.target} no-spend days this month")
        ChallengeType.CATEGORY_CAP -> tr("Menos de ${formatCents(c.target)} en ${categoryName ?: "?"} este mes", "Under ${formatCents(c.target)} on ${categoryName ?: "?"} this month")
        ChallengeType.CATEGORY_STREAK -> tr("${c.target} días seguidos sin ${categoryName ?: "?"}", "${c.target} days in a row without ${categoryName ?: "?"}")
    }
}
