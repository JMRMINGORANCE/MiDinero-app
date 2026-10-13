package com.midinero.control.watch

import androidx.compose.ui.graphics.toArgb
import com.midinero.control.MiDineroApp
import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.domain.Budgets
import com.midinero.control.domain.DailyCurve
import com.midinero.control.domain.Lang
import com.midinero.control.domain.Period
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.dayIndex
import com.midinero.control.domain.lengthInDays
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.tr
import com.midinero.control.link.WatchCategory
import com.midinero.control.link.WatchMovement
import com.midinero.control.link.WatchState
import com.midinero.control.service.PaymentNotificationListener
import com.midinero.control.ui.theme.parseHexColor
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.abs

/** Calcula en el móvil todo lo que enseña el reloj, con los textos ya listos. */
object WatchSnapshot {

    suspend fun build(app: MiDineroApp, acked: List<String>, today: LocalDate = LocalDate.now()): WatchState {
        val prefs = app.prefs
        val repo = app.repository
        Period.startDay = prefs.periodStartDay
        val month = currentPeriod(today)
        val expenses = repo.expensesIn(month).first()
        val previous = repo.expensesIn(month.minusMonths(1)).first()
        val categories = repo.categories.first()
        val byId = categories.associateBy { it.id }

        val spending = expenses.filter { !it.isIncome }
        val spent = spending.sumOf { it.amountCents }
        val limit = prefs.monthlyBudgetCents
        val budget = if (limit > 0) limit + repo.totalRolloverBonus(limit, month) else 0L
        val spentToday = spending.filter { dateOf(it.timestamp) == today }.sumOf { it.amountCents }

        // «Hoy puedes gastar»: lo que quedaba al empezar el día repartido entre los días que faltan (como el widget)
        val pace = Budgets.pace(month, spent - spentToday, budget, today)
        val allowance = pace.dailyAllowanceCents
        val todayLabel: String
        val todayValue: String
        val todayOver: Boolean
        val todayNote: String
        if (budget > 0 && allowance != null) {
            val left = allowance - spentToday
            todayLabel = tr("Hoy puedes gastar", "You can spend today")
            todayValue = formatCents(left.coerceAtLeast(0))
            todayOver = left < 0
            todayNote = if (left < 0) tr("Te has pasado ${formatCents(-left)}", "${formatCents(-left)} over")
            else tr("Llevas ${formatCents(spentToday)} hoy", "${formatCents(spentToday)} spent today")
        } else {
            todayLabel = tr("Hoy llevas", "Spent today")
            todayValue = formatCents(spentToday)
            todayOver = budget in 1..spent
            val days = pace.daysLeft
            todayNote = when {
                budget > 0 -> tr("Presupuesto agotado", "Budget used up")
                days > 0 -> tr("Quedan $days días del mes", "$days days left this month")
                else -> ""
            }
        }

        // Frente al mes anterior a estas mismas alturas
        val day = month.dayIndex(today)
        val before = previous.filter { !it.isIncome && month.minusMonths(1).dayIndex(dateOf(it.timestamp)) <= day }.sumOf { it.amountCents }
        val versus = if (before >= 1000 && spent > 0) {
            val pct = ((spent - before) * 100 / before).toInt()
            val name = month.minusMonths(1).format(Lang.dateFormat("LLLL"))
            when {
                abs(pct) < 3 -> tr("Igual que en $name", "Same as $name")
                pct > 0 -> tr("$pct % más que en $name", "$pct% more than $name")
                else -> tr("${-pct} % menos que en $name", "${-pct}% less than $name")
            } to (pct >= 3)
        } else "" to false

        // La semana (de lunes a hoy): con presupuesto, lo que le toca a la semana menos lo gastado en ella
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        fun millis(d: LocalDate) = d.atStartOfDay(com.midinero.control.domain.zone).toInstant().toEpochMilli()
        val spentWeek = repo.spentBetween(millis(monday), millis(today.plusDays(1)))
        val weekBudget = if (budget > 0) budget * 7 / month.lengthInDays().coerceAtLeast(1) else 0L
        val weekLeft = weekBudget - spentWeek

        // Importes ocultos: el reloj enseña la parte del presupuesto gastada en lugar de puntos
        val share = if (prefs.hideAmounts) com.midinero.control.domain.PrivateShare.label(spent, budget) else null
        return WatchState(
            updated = System.currentTimeMillis(),
            english = Lang.english,
            hidden = prefs.hideAmounts,
            monthLabel = month.format(Lang.dateFormat("LLLL")).replaceFirstChar { it.uppercase() },
            spent = if (share != null) "" else formatCents(spent),
            budget = if (budget > 0 && share == null) formatCents(budget) else "",
            budgetRatio = if (budget > 0) spent.toFloat() / budget else -1f,
            pace = pace.monthFraction,
            todayLabel = if (share != null) tr("Del presupuesto llevas", "Of your budget") else todayLabel,
            todayValue = share ?: todayValue,
            todayOver = todayOver,
            todayNote = if (share != null) "" else todayNote,
            versus = versus.first,
            versusUp = versus.second,
            curve = DailyCurve.cumulative(month, expenses, today),
            recent = expenses.sortedByDescending { it.timestamp }.take(8).map { movement(it, byId, today) },
            categories = categoryTotals(spending, categories),
            quick = quickCategories(spending + previous.filter { !it.isIncome }, categories),
            acked = acked,
            currencySymbol = "€",
            symbolFirst = Lang.english,
            detection = PaymentNotificationListener.isEnabled(app),
            weekLabel = if (weekBudget > 0) tr("Te queda esta semana", "Left this week") else tr("Esta semana llevas", "Spent this week"),
            weekValue = when {
                share != null -> com.midinero.control.domain.PrivateShare.label(spentWeek, weekBudget) ?: "•••"
                weekBudget > 0 -> formatCents(weekLeft.coerceAtLeast(0))
                else -> formatCents(spentWeek)
            },
            weekRatio = if (weekBudget > 0) spentWeek.toFloat() / weekBudget else -1f,
            weekOver = weekBudget > 0 && weekLeft < 0,
        )
    }

    private fun color(c: Category?) = c?.let { parseHexColor(it.colorHex).toArgb() } ?: 0xFF8A8A93.toInt()

    private fun movement(e: Expense, byId: Map<Long, Category>, today: LocalDate): WatchMovement {
        val c = e.categoryId?.let { byId[it] }
        val date = dateOf(e.timestamp)
        val time = when (date) {
            today -> java.time.Instant.ofEpochMilli(e.timestamp).atZone(com.midinero.control.domain.zone)
                .toLocalTime().format(Lang.dateFormat("HH:mm"))
            today.minusDays(1) -> tr("Ayer", "Yesterday")
            else -> date.format(Lang.dateFormat("d MMM"))
        }
        return WatchMovement(
            title = e.merchant,
            amount = if (e.isIncome || e.isRefund) "+" + formatCents(kotlin.math.abs(e.amountCents)) else formatCents(e.amountCents),
            emoji = c?.emoji ?: "❔",
            color = color(c),
            time = time,
            income = e.isIncome || e.isRefund,
        )
    }

    private fun categoryTotals(spending: List<Expense>, categories: List<Category>): List<WatchCategory> =
        spending.groupBy { it.categoryId }
            .map { (id, list) -> categories.firstOrNull { it.id == id } to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
            .take(6)
            .map { (c, cents) ->
                val limit = c?.monthlyLimitCents?.takeIf { it > 0 }
                WatchCategory(
                    id = c?.id ?: -1,
                    name = c?.name ?: tr("Sin categoría", "Uncategorised"),
                    emoji = c?.emoji ?: "❔",
                    color = color(c),
                    spent = formatCents(cents),
                    limit = limit?.let { formatCents(it) }.orEmpty(),
                    ratio = limit?.let { cents.toFloat() / it } ?: -1f,
                )
            }

    /** Categorías de gasto para apuntar desde el reloj: las más usadas en los dos últimos meses primero. */
    private fun quickCategories(recent: List<Expense>, categories: List<Category>): List<WatchCategory> {
        val uses = recent.groupingBy { it.categoryId }.eachCount()
        return categories.filter { !it.isIncome && !it.hidden }
            .sortedWith(compareByDescending<Category> { uses[it.id] ?: 0 }.thenBy { it.sortOrder }.thenBy { it.name })
            .take(12)
            .map { WatchCategory(it.id, it.name, it.emoji, color(it)) }
    }
}
