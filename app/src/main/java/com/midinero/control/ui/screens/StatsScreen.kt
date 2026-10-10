package com.midinero.control.ui.screens

import com.midinero.control.domain.tr
import com.midinero.control.domain.Lang
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.domain.Categorizer
import com.midinero.control.domain.MonthStats
import com.midinero.control.domain.SPANISH
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.parseHexColor
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val shortMonth get() = Lang.dateFormat("MMM")
private val weekdayLabels get() = if (Lang.english) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("L", "M", "X", "J", "V", "S", "D")
private val weekdayNames get() = if (Lang.english) listOf("Mondays", "Tuesdays", "Wednesdays", "Thursdays", "Fridays", "Saturdays", "Sundays")
    else listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos")

/** Nombre corto del mes sin punto: "sept", "oct". */
fun YearMonth.shortName(): String = format(shortMonth).trimEnd('.')

/** "▲ 12 % vs agosto" / "▼ 5 % vs agosto". Sin color: la flecha y el texto llevan el significado. */
fun MonthStats.changeLabel(): String? {
    val pct = changeVsPrevious ?: return null
    val prev = month.minusMonths(1).format(Lang.dateFormat("LLLL"))
    return when {
        pct > 0 -> tr("▲ $pct % más que en $prev", "▲ $pct % more than in $prev")
        pct < 0 -> tr("▼ ${abs(pct)} % menos que en $prev", "▼ ${abs(pct)} % less than in $prev")
        else -> tr("Igual que en $prev", "Same as in $prev")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel, onOpenCategory: (Long) -> Unit, onOpenMerchant: (String) -> Unit, onOpenMerchants: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val month by vm.month.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    var selectedWeekday by rememberSaveable { mutableStateOf<Int?>(null) }
    var view by rememberSaveable { mutableIntStateOf(0) }
    val year by vm.year.collectAsStateWithLifecycle()
    val yearSummary by vm.yearSummary.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val tips by vm.tips.collectAsStateWithLifecycle()

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    ScrollToTopOnReselect("stats", listState)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(tr("Análisis", "Insights"), subtitle = tr("En qué se va tu dinero y cómo cambia", "Where your money goes and how it changes"))
            SegmentedTabs(
                listOf(tr("Mes", "Month"), tr("Calendario", "Calendar"), tr("Año", "Year")),
                selected = view,
                onSelect = { view = it },
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (view != 2) MonthSelector(month, vm::previousMonth, vm::nextMonth)
        }

        item { HintCard(vm, "hint_stats", tr("Toca las barras para ver cada mes y usa Calendario o Año para otras vistas.", "Tap the bars to see each month and use Calendar or Year for other views.")) }

        if (view == 2) {
            item {
                YearSummaryView(
                    year = year,
                    summary = yearSummary,
                    categoryById = categoryById,
                    canGoNext = year < currentPeriod().year,
                    onPrevious = vm::previousYear,
                    onNext = vm::nextYear,
                    onOpenMonth = { m -> vm.setMonth(m); view = 0 },
                    onShare = {
                        vm.yearReportData()?.let { data ->
                            runCatching { com.midinero.control.ui.ReportImage.share(context, com.midinero.control.ui.ReportImage.renderYear(context, data)) }
                        }
                    },
                    onOpenMerchants = onOpenMerchants,
                )
            }
            return@LazyColumn
        }
        if (view == 1) {
            item { SpendingCalendarCard(month, expenses, categoryById) }
            return@LazyColumn
        }

        val s = stats
        if (s == null || s.month != month) {
            item { Spacer(Modifier.height(80.dp)) }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(tr("Total del mes", "Month total"), formatCents(s.totalCents), s.changeLabel(), Modifier.weight(1f))
                StatTile(
                    tr("Media al día", "Daily average"),
                    formatCents(s.dailyAverageCents),
                    "${s.count} ${if (s.count == 1) tr("movimiento", "transaction") else tr("movimientos", "transactions")}",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.projectionCents != null) {
                    StatTile(tr("Previsión a fin de mes", "Month-end forecast"), formatCents(s.projectionCents), tr("Al ritmo actual", "At the current pace"), Modifier.weight(1f))
                } else {
                    StatTile(tr("Mes anterior", "Previous month"), formatCents(s.previousTotalCents), null, Modifier.weight(1f))
                }
                val fixedPct = if (s.totalCents > 0) s.recurringCents * 100 / s.totalCents else 0
                StatTile(
                    tr("Gastos fijos", "Recurring costs"),
                    formatCents(s.recurringCents),
                    if (s.pendingRecurringCents > 0) tr("Faltan ${formatCents(s.pendingRecurringCents)} por cobrar", "${formatCents(s.pendingRecurringCents)} still to come")
                    else tr("$fixedPct % del total", "$fixedPct % of the total"),
                    Modifier.weight(1f),
                )
            }
        }

        if (s.vsAverage.isNotEmpty()) {
            item { VersusAverageCard(s, categoryById, onOpenCategory) }
        }

        if (s.incomeCents > 0 || s.incomeTrend.any { it.second > 0 }) {
            item { IncomeVsSpending(s) }
        }

        if (s.totalCents == 0L) {
            item {
                EmptyState(
                    emoji = "📊",
                    title = tr("Aún no hay datos", "No data yet"),
                    text = tr("Cuando apuntes gastos en ${month.shortName()} verás aquí en qué se va tu dinero.", "Once you log expenses in ${month.shortName()} you'll see here where your money goes."),
                )
            }
        }

        if (tips.isNotEmpty()) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(tr("Ideas para ti", "Ideas for you"), action = null)
                        tips.forEach { tip ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = tip.categoryId != null) { tip.categoryId?.let(onOpenCategory) }
                                    .padding(top = 10.dp),
                            ) {
                                Text(tip.emoji)
                                Spacer(Modifier.width(10.dp))
                                Text(tip.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        if (s.byCategory.isNotEmpty()) {
            item { CategoryBreakdown(s, categoryById, onOpenCategory) }
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionHeader(tr("Evolución", "Trend"), action = tr("Últimos 6 meses · toca un mes para verlo", "Last 6 months · tap a month to see it"))
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        values = s.trend.map { it.second },
                        labels = s.trend.map { it.first.shortName() },
                        selected = s.trend.indexOfFirst { it.first == month },
                        valueLabel = { formatCents(it) },
                        onSelect = { vm.setMonth(s.trend[it].first) },
                    )
                    val withData = s.trend.filter { it.second > 0 }
                    if (withData.size > 1) {
                        Text(
                            tr("Media: ${formatCents(withData.sumOf { it.second } / withData.size)} al mes", "Average: ${formatCents(withData.sumOf { it.second } / withData.size)} a month"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        if (s.totalCents > 0) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val maxDay = s.byWeekday.indices.maxByOrNull { s.byWeekday[it] }
                        SectionHeader(
                            tr("Por día de la semana", "By day of the week"),
                            action = maxDay?.let { tr("Los ${weekdayNames[it]} es cuando más gastas", "${weekdayNames[it]} are when you spend the most") },
                        )
                        Spacer(Modifier.height(8.dp))
                        BarChart(
                            values = s.byWeekday,
                            labels = weekdayLabels,
                            selected = selectedWeekday ?: maxDay,
                            valueLabel = { formatCents(it) },
                            onSelect = { selectedWeekday = it },
                            barAreaHeight = 80,
                        )
                    }
                }
            }

            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { SectionHeader(tr("Dónde más gastas", "Where you spend the most"), action = null) }
                            androidx.compose.material3.TextButton(onClick = onOpenMerchants) { Text(tr("Ver todos", "See all")) }
                        }
                        val top = s.topMerchants.firstOrNull()?.second ?: 1L
                        s.topMerchants.forEachIndexed { i, (name, cents) ->
                            Column(Modifier.fillMaxWidth().clickable { onOpenMerchant(Categorizer.merchantKey(name)) }.padding(top = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${i + 1}. $name",
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(formatCents(cents), fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(4.dp))
                                RankBar(cents.toFloat() / top, MaterialTheme.colorScheme.primary)
                            }
                        }
                        s.biggest?.let { b ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                tr("Gasto más alto: ${formatCents(b.amountCents)} en ${b.merchant} (${dateOf(b.timestamp).shortLabel()})", "Biggest expense: ${formatCents(b.amountCents)} at ${b.merchant} (${dateOf(b.timestamp).shortLabel()})"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tabla de los últimos meses: ingresos, gastos y lo que queda. Dos medidas del mismo tipo: mejor como tabla que como gráfico doble. */
@Composable
private fun IncomeVsSpending(s: MonthStats) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                tr("Ingresos y ahorro", "Income and savings"),
                action = s.savingsRate?.let { rate ->
                    if (rate >= 0) tr("Este mes ahorras el $rate % de lo que ingresas", "This month you're saving $rate % of your income") else tr("Este mes gastas más de lo que ingresas", "This month you're spending more than you earn")
                },
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Text(tr("Mes", "Month"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                listOf(tr("Ingresos", "Income"), tr("Gastos", "Expenses"), tr("Queda", "Left")).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
            s.trend.zip(s.incomeTrend).reversed().forEach { (spent, income) ->
                val bold = if (spent.first == s.month) FontWeight.SemiBold else FontWeight.Normal
                Row(Modifier.padding(top = 6.dp)) {
                    Text(spent.first.shortName(), fontWeight = bold, modifier = Modifier.weight(1f))
                    Text(formatCents(income.second), fontWeight = bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f), maxLines = 1)
                    Text(formatCents(spent.second), fontWeight = bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f), maxLines = 1)
                    Text(
                        formatCents(income.second - spent.second),
                        fontWeight = bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.3f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.asHeading())
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Reparto por categoría: barra apilada con las 5 mayores + tr("Resto", "Other"), y lista completa ordenada. */
@Composable
private fun CategoryBreakdown(s: MonthStats, categoryById: Map<Long, Category>, onOpenCategory: (Long) -> Unit) {
    val uncategorizedColor = MaterialTheme.colorScheme.outline
    val restColor = MaterialTheme.colorScheme.outlineVariant
    fun colorOf(id: Long?): Color = id?.let { categoryById[it] }?.let { parseHexColor(it.colorHex) } ?: uncategorizedColor
    val top = s.byCategory.take(5)
    val rest = s.byCategory.drop(5).sumOf { it.second }
    val segments = top.map { (id, v) -> ShareSegment(categoryById[id ?: -1]?.name ?: tr("Sin categoría", "Uncategorised"), v, colorOf(id)) } +
        listOfNotNull(if (rest > 0) ShareSegment(tr("Resto", "Other"), rest, restColor) else null)
    val max = s.byCategory.first().second.coerceAtLeast(1L)

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(tr("En qué se va", "Where it goes"), action = tr("Toca una categoría para ver su detalle", "Tap a category to see its details"))
            Spacer(Modifier.height(12.dp))
            ShareBar(segments)
            Spacer(Modifier.height(8.dp))
            s.byCategory.forEach { (id, cents) ->
                val cat = id?.let { categoryById[it] }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = id != null) { id?.let(onOpenCategory) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(cat, size = 32)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(
                                cat?.name ?: tr("Sin categoría", "Uncategorised"),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(formatCents(cents), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RankBar(cents.toFloat() / max, colorOf(id), Modifier.weight(1f))
                            Text(
                                "${cents * 100 / s.totalCents.coerceAtLeast(1)} %",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(44.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Cada categoría frente a su media de los 6 meses anteriores (en el mes en curso, lo previsto a fin de mes). */
@Composable
private fun VersusAverageCard(s: MonthStats, categoryById: Map<Long, com.midinero.control.data.Category>, onOpenCategory: (Long) -> Unit) {
    val isCurrent = s.month == currentPeriod()
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("Frente a tu media", "Versus your average"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
            Text(
                if (isCurrent) tr("Lo que llevarás a fin de mes a este ritmo, comparado con la media de los 6 meses anteriores.", "What you'll reach by month-end at this pace, compared with the average of the previous 6 months.")
                else tr("Lo gastado comparado con la media de los 6 meses anteriores.", "Spending compared with the average of the previous 6 months."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val max = s.vsAverage.take(6).maxOf { maxOf(it.projectedCents, it.averageCents) }.coerceAtLeast(1)
            s.vsAverage.take(6).forEach { c ->
                val cat = c.categoryId?.let { categoryById[it] }
                val color = cat?.let { com.midinero.control.ui.theme.parseHexColor(it.colorHex) } ?: MaterialTheme.colorScheme.error
                val delta = c.deltaPercent
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .then(if (cat != null) Modifier.clickable { onOpenCategory(cat.id) } else Modifier),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${cat?.emoji ?: "❔"} ${cat?.name ?: tr("Sin categoría", "Uncategorised")}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (c.averageCents > 0) {
                            Pill(
                                (if (delta > 0) "+" else "") + "$delta %",
                                when {
                                    delta >= 10 -> com.midinero.control.ui.theme.BudgetColors.over
                                    delta <= -10 -> com.midinero.control.ui.theme.BudgetColors.ok
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        } else {
                            Pill(tr("nueva", "new"), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    BudgetBar(animatedRatio(c.projectedCents.toFloat() / max), color, height = 8)
                    Spacer(Modifier.height(3.dp))
                    BudgetBar(animatedRatio(c.averageCents.toFloat() / max), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), height = 4)
                    Text(
                        (if (isCurrent) tr("Previsto ", "Expected ") else "") + formatCents(c.projectedCents) + tr(" · media ", " · average ") + formatCents(c.averageCents),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
