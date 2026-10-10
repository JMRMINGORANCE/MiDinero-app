package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.Trip
import java.time.LocalDate

enum class TripStatus { UPCOMING, ACTIVE, FINISHED }

/** Resumen de un viaje o evento. [projectedCents]: a este ritmo, lo que costará en total (solo si está en curso y tiene fin). */
data class TripSummary(
    val totalCents: Long,
    val count: Int,
    val days: Int,
    val perDayCents: Long,
    val remainingCents: Long?,
    val projectedCents: Long?,
    val byCategory: List<Pair<Long?, Long>>,
)

object TripStats {

    fun status(trip: Trip, today: LocalDate = LocalDate.now()): TripStatus {
        val day = today.toEpochDay()
        return when {
            day < trip.startDay -> TripStatus.UPCOMING
            trip.endDay != null && day > trip.endDay -> TripStatus.FINISHED
            else -> TripStatus.ACTIVE
        }
    }

    /** 80 o 100 cuando se cruza ese porcentaje del presupuesto; null si aún no. */
    fun alertLevel(spentCents: Long, budgetCents: Long): Int? = when {
        budgetCents <= 0 -> null
        spentCents >= budgetCents -> 100
        spentCents * 100 >= budgetCents * 80 -> 80
        else -> null
    }

    fun summarize(trip: Trip, expenses: List<Expense>, today: LocalDate = LocalDate.now()): TripSummary {
        val spending = expenses.filter { !it.isIncome }
        val total = spending.sumOf { it.amountCents }
        val status = status(trip, today)
        val lastDay = when (status) {
            TripStatus.FINISHED -> trip.endDay!!
            TripStatus.ACTIVE -> today.toEpochDay()
            TripStatus.UPCOMING -> trip.startDay
        }
        val days = (lastDay - trip.startDay + 1).toInt().coerceAtLeast(1)
        val perDay = total / days
        val totalDays = trip.endDay?.let { (it - trip.startDay + 1).toInt().coerceAtLeast(1) }
        val projected = if (status == TripStatus.ACTIVE && totalDays != null && days < totalDays && total > 0) {
            total + perDay * (totalDays - days)
        } else null
        val byCategory = spending.groupBy { it.categoryId }
            .map { (cat, list) -> cat to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
        return TripSummary(
            totalCents = total,
            count = spending.size,
            days = days,
            perDayCents = perDay,
            remainingCents = trip.budgetCents?.let { it - total },
            projectedCents = projected,
            byCategory = byCategory,
        )
    }

    /** "12–18 ago 2025", "desde 3 mar" o "3 mar 2025" si es un solo día. */
    fun dateLabel(trip: Trip): String {
        val start = LocalDate.ofEpochDay(trip.startDay)
        val end = trip.endDay?.let { LocalDate.ofEpochDay(it) } ?: return tr("desde ${start.shortLabel()}", "from ${start.shortLabel()}")
        return if (start == end) start.shortLabel() else "${start.shortLabel()} – ${end.shortLabel()}"
    }
}
