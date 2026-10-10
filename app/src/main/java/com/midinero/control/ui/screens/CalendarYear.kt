package com.midinero.control.ui.screens

import com.midinero.control.domain.Lang
import com.midinero.control.domain.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.remember
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.domain.SpendingCalendar
import com.midinero.control.domain.YearSummary
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.dayLabel
import com.midinero.control.domain.endDateExclusive
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.startDate
import com.midinero.control.domain.timeLabel
import java.time.LocalDate
import java.time.YearMonth

/** Intensidad de los niveles 0..4 del mapa de calor (un solo tono, de claro a oscuro). */
private val levelAlpha = listOf(0f, 0.18f, 0.38f, 0.62f, 0.88f)

/** Calendario del periodo con un mapa de calor del gasto diario. Tocar un día muestra sus gastos. */
@Composable
fun SpendingCalendarCard(month: YearMonth, expenses: List<Expense>, categoryById: Map<Long, Category>) {
    val totals = SpendingCalendar.dailyTotals(month, expenses)
    val levels = SpendingCalendar.levels(totals)
    var selectedEpoch by rememberSaveable(month) { mutableStateOf<Long?>(null) }
    val selected = selectedEpoch?.let { LocalDate.ofEpochDay(it) }
    val today = LocalDate.now()
    val start = month.startDate()
    val end = month.endDateExclusive()
    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.toList()
    val leading = start.dayOfWeek.value - 1
    val cells: List<LocalDate?> = List(leading) { null } + days
    val primary = MaterialTheme.colorScheme.primary

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("Calendario de gasto", "Spending calendar"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                tr("Cuanto más oscuro, más gastaste ese día. Toca un día para ver sus gastos.", "The darker, the more you spent that day. Tap a day to see its expenses."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                (if (Lang.english) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("L", "M", "X", "J", "V", "S", "D")).forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { i ->
                        val day = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                            if (day != null) {
                                val level = levels[day] ?: 0
                                val isSelected = day == selected
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (level == 0) MaterialTheme.colorScheme.surfaceVariant
                                            else primary.copy(alpha = levelAlpha[level])
                                        )
                                        .then(
                                            when {
                                                isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))
                                                day == today -> Modifier.border(1.dp, primary, RoundedCornerShape(6.dp))
                                                else -> Modifier
                                            }
                                        )
                                        .clickable { selectedEpoch = if (isSelected) null else day.toEpochDay() }
                                        .semantics {
                                            contentDescription = tr("Día ${day.dayOfMonth}: ${formatCents(totals[day] ?: 0L)}", "Day ${day.dayOfMonth}: ${formatCents(totals[day] ?: 0L)}")
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${day.dayOfMonth}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (level >= 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // Leyenda de la escala
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("Menos", "Less"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                levelAlpha.forEachIndexed { i, a ->
                    Box(
                        Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(
                            if (i == 0) MaterialTheme.colorScheme.surfaceVariant else primary.copy(alpha = a)
                        )
                    )
                }
                Text(tr("Más", "More"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                val noSpend = days.count { it <= today && (totals[it] ?: 0L) == 0L }
                Text(
                    "$noSpend ${if (noSpend == 1) tr("día", "day") else tr("días", "days")} " + tr("sin gastos", "without spending"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected != null) {
                val ofDay = expenses.filter { !it.isIncome && dateOf(it.timestamp) == selected }.sortedBy { it.timestamp }
                Spacer(Modifier.height(12.dp))
                Row {
                    Text(selected.dayLabel(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(formatCents(ofDay.sumOf { it.amountCents }), fontWeight = FontWeight.SemiBold)
                }
                if (ofDay.isEmpty()) {
                    Text(tr("Sin gastos ese día 🎉", "No spending that day 🎉"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ofDay.forEach { e ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        MerchantBadge(e.merchant, e.categoryId?.let { categoryById[it] }, size = 28)
                        Spacer(Modifier.width(8.dp))
                        Text(e.merchant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${timeLabel(e.timestamp)} · ${formatCents(e.amountCents)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private val monthInitials = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/** El año día a día: una columna por semana y una fila por día, más oscuro cuanto más se gastó. */
@Composable
private fun YearHeatmap(year: Int, daily: Map<LocalDate, Long>) {
    val levels = remember(daily) { SpendingCalendar.levels(daily) }
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val todayLine = MaterialTheme.colorScheme.onSurface
    val first = LocalDate.of(year, 1, 1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val last = LocalDate.of(year, 12, 31)
    val weeks = (java.time.temporal.ChronoUnit.DAYS.between(start, last) / 7 + 1).toInt()
    val today = LocalDate.now()
    val busiest = daily.maxByOrNull { it.value }
    val spendDays = daily.count { it.value > 0 }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("El año día a día", "The year day by day"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    tr("$spendDays días con gastos", "$spendDays days with spending"),
                    busiest?.let { (d, c) -> tr("el más caro, ${d.dayLabel()} (${formatCents(c)})", "the priciest, ${d.dayLabel()} (${formatCents(c)})") },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.Canvas(
                Modifier.fillMaxWidth().aspectRatio(weeks / 7f)
                    .semantics { contentDescription = tr("Mapa de calor del gasto de $year", "Heat map of $year spending") },
            ) {
                val cell = size.width / weeks
                val gap = cell * 0.16f
                for (w in 0 until weeks) for (dow in 0 until 7) {
                    val day = start.plusDays((w * 7 + dow).toLong())
                    if (day.year != year) continue
                    val level = levels[day] ?: 0
                    val color = if (level == 0) empty else primary.copy(alpha = levelAlpha[level])
                    val tl = androidx.compose.ui.geometry.Offset(w * cell + gap / 2, dow * cell + gap / 2)
                    val sz = androidx.compose.ui.geometry.Size(cell - gap, cell - gap)
                    drawRoundRect(color, tl, sz, androidx.compose.ui.geometry.CornerRadius(cell * 0.2f))
                    if (day == today) {
                        drawRoundRect(todayLine, tl, sz, androidx.compose.ui.geometry.CornerRadius(cell * 0.2f), style = androidx.compose.ui.graphics.drawscope.Stroke(cell * 0.12f))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                monthInitials.forEach {
                    Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Resumen del año: total, media, meses extremos, reparto y comercios. */
@Composable
fun YearSummaryView(
    year: Int,
    summary: YearSummary?,
    categoryById: Map<Long, Category>,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onShare: (() -> Unit)? = null,
    onOpenMerchants: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onPrevious) { Icon(Icons.Filled.ChevronLeft, tr("Año anterior", "Previous year")) }
            Text("$year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row {
                if (onShare != null && (summary?.count ?: 0) > 0) {
                    IconButton(onClick = onShare) { Icon(Icons.Filled.Share, tr("Compartir resumen del año", "Share year summary")) }
                }
                IconButton(onClick = onNext, enabled = canGoNext) { Icon(Icons.Filled.ChevronRight, tr("Año siguiente", "Next year")) }
            }
        }
        val s = summary?.takeIf { it.year == year } ?: return@Column
        if (s.count == 0) {
            EmptyState(
                emoji = "📅",
                title = tr("Sin gastos en $year", "No spending in $year"),
                text = tr("Cuando apuntes gastos este año verás aquí su resumen.", "Once you log expenses this year you'll see its summary here."),
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(tr("Gastado en $year", "Spent in $year"), formatCents(s.totalCents), tr("${s.count} movimientos", "${s.count} transactions"), Modifier.weight(1f))
            val vs = s.versusPreviousPercent
            StatTile(
                tr("Media al mes", "Monthly average"), formatCents(s.monthlyAverageCents),
                vs?.let {
                    val partial = year == LocalDate.now().year
                    when {
                        it > 0 -> tr("▲ $it % que ${year - 1}", "▲ $it % vs ${year - 1}")
                        it < 0 -> tr("▼ ${-it} % que ${year - 1}", "▼ ${-it} % vs ${year - 1}")
                        else -> tr("Igual que ${year - 1}", "Same as ${year - 1}")
                    } + if (partial) tr(" a estas alturas", " so far") else ""
                },
                Modifier.weight(1f),
            )
        }
        YearHeatmap(year, s.daily)
        if (s.incomeCents > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(tr("Ingresos", "Income"), formatCents(s.incomeCents), null, Modifier.weight(1f))
                StatTile(
                    if (s.balanceCents >= 0) tr("Ahorrado", "Saved") else tr("Déficit", "Deficit"),
                    formatCents(s.balanceCents),
                    tr("${s.balanceCents * 100 / s.incomeCents} % de los ingresos", "${s.balanceCents * 100 / s.incomeCents} % of income"),
                    Modifier.weight(1f),
                )
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(tr("Mes a mes", "Month by month"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        s.maxMonth?.let { tr("Mes más caro: ${it.shortName()}", "Most expensive month: ${it.shortName()}") },
                        s.minMonth?.let { tr("más barato: ${it.shortName()}", "cheapest: ${it.shortName()}") },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                BarChart(
                    values = s.byMonth.map { it.second },
                    labels = monthInitials,
                    selected = s.byMonth.indexOfFirst { it.first == s.maxMonth },
                    valueLabel = { formatCents(it) },
                    onSelect = { onOpenMonth(s.byMonth[it].first) },
                    barAreaHeight = 100,
                )
                Text(
                    tr("Toca un mes para ver su análisis", "Tap a month to see its insights"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(tr("Por categoría", "By category"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val max = s.byCategory.firstOrNull()?.second?.coerceAtLeast(1L) ?: 1L
                s.byCategory.forEach { (id, cents) ->
                    val cat = id?.let { categoryById[it] }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(cat, size = 28)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(cat?.name ?: tr("Sin categoría", "Uncategorised"), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatCents(cents), fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            RankBar(cents.toFloat() / max, MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (s.topMerchants.isNotEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Comercios del año", "Merchants of the year"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (onOpenMerchants != null) {
                            androidx.compose.material3.TextButton(onClick = onOpenMerchants) { Text(tr("Ver todos", "See all")) }
                        }
                    }
                    s.topMerchants.forEachIndexed { i, (name, cents) ->
                        Row(Modifier.padding(top = 8.dp)) {
                            Text("${i + 1}. $name", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatCents(cents), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
