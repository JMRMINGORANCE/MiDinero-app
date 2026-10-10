package com.midinero.control.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Estado calculado de una meta de ahorro. */
data class GoalProgress(
    val savedCents: Long,
    val targetCents: Long,
    /** Lo que habría que apartar cada mes para llegar a la fecha límite; null si no hay fecha o ya está. */
    val monthlyNeededCents: Long?,
    /** Fecha estimada al ritmo de aportación de los últimos meses; null si no hay ritmo. */
    val estimatedDate: LocalDate?,
    val monthsLeft: Int?,
) {
    val ratio: Float get() = if (targetCents <= 0) 0f else (savedCents.toFloat() / targetCents).coerceAtLeast(0f)
    val remainingCents: Long get() = (targetCents - savedCents).coerceAtLeast(0)
    val completed: Boolean get() = targetCents > 0 && savedCents >= targetCents
}

object Goals {

    /**
     * [contributions]: (fecha, importe) de cada aportación (negativo = retirada).
     * El ritmo se calcula con lo aportado en los últimos 3 meses.
     */
    fun progress(
        targetCents: Long,
        deadline: LocalDate?,
        contributions: List<Pair<LocalDate, Long>>,
        today: LocalDate = LocalDate.now(),
    ): GoalProgress {
        val saved = contributions.sumOf { it.second }
        val remaining = (targetCents - saved).coerceAtLeast(0)
        val monthsLeft = deadline?.let { d ->
            val days = ChronoUnit.DAYS.between(today, d)
            if (days <= 0) 0 else ceil(days / 30.44).toInt()
        }
        val monthlyNeeded = when {
            remaining == 0L -> null
            monthsLeft == null -> null
            monthsLeft <= 0 -> remaining
            else -> (remaining + monthsLeft - 1) / monthsLeft
        }
        val since = today.minusMonths(3)
        val recent = contributions.filter { it.first > since }.sumOf { it.second }
        val perMonth = recent / 3.0
        val estimated = when {
            remaining == 0L -> null
            perMonth <= 0.0 -> null
            else -> today.plusDays(ceil(remaining / perMonth * 30.44).toLong())
        }
        return GoalProgress(saved, targetCents, monthlyNeeded, estimated, monthsLeft)
    }

    /** ¿Llega a tiempo? true si la fecha estimada no pasa de la límite. */
    fun onTrack(p: GoalProgress, deadline: LocalDate?): Boolean? = when {
        p.completed -> true
        deadline == null || p.estimatedDate == null -> null
        else -> !p.estimatedDate.isAfter(deadline)
    }
}
