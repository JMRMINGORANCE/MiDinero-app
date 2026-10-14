package com.midinero.control.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.domain.Compare
import com.midinero.control.domain.DateRange
import com.midinero.control.domain.Lang
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.formatQuantity
import com.midinero.control.domain.tr
import com.midinero.control.ui.HouseholdViewModel
import com.midinero.control.ui.theme.BudgetColors
import java.time.LocalDate
import java.time.YearMonth

/** Con qué se compara el tramo elegido. */
private enum class CompareWith { PREVIOUS_YEAR, PREVIOUS_SPAN, CUSTOM, NONE }

/**
 * Gasto entre dos fechas que elijas, por categoría, y comparado con otro tramo
 * (por ejemplo, la luz de todo 2025 frente a la de 2024).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareScreen(onBack: () -> Unit, onOpenCategory: (Long) -> Unit, hvm: HouseholdViewModel = viewModel()) {
    val categories by hvm.categories.collectAsStateWithLifecycle()
    val expenses by hvm.expenses.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var aStart by rememberSaveable { mutableStateOf(LocalDate.of(today.year, 1, 1).toEpochDay()) }
    var aEnd by rememberSaveable { mutableStateOf(LocalDate.of(today.year, 12, 31).toEpochDay()) }
    var compareWith by rememberSaveable { mutableStateOf(CompareWith.PREVIOUS_YEAR) }
    var bStart by rememberSaveable { mutableStateOf(LocalDate.of(today.year - 1, 1, 1).toEpochDay()) }
    var bEnd by rememberSaveable { mutableStateOf(LocalDate.of(today.year - 1, 12, 31).toEpochDay()) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBar by rememberSaveable { mutableStateOf<Int?>(null) }

    val a = DateRange(LocalDate.ofEpochDay(minOf(aStart, aEnd)), LocalDate.ofEpochDay(maxOf(aStart, aEnd)))
    val b: DateRange? = when (compareWith) {
        CompareWith.PREVIOUS_YEAR -> a.previousYear()
        CompareWith.PREVIOUS_SPAN -> a.previousSpan()
        CompareWith.CUSTOM -> DateRange(LocalDate.ofEpochDay(minOf(bStart, bEnd)), LocalDate.ofEpochDay(maxOf(bStart, bEnd)))
        CompareWith.NONE -> null
    }
    val filter: Set<Long?>? = categoryId?.let { setOf(it) }
    val byId = remember(categories) { categories.associateBy { it.id } }
    val rows = remember(expenses, a, b, filter) { Compare.byCategory(expenses, a, b, filter) }
    val totalA = rows.sumOf { it.aCents }
    val totalB = rows.sumOf { it.bCents }
    val monthsA = remember(expenses, a, filter) { Compare.monthly(expenses, a, filter) }
    val monthsB = remember(expenses, b, filter) { b?.let { Compare.monthly(expenses, it, filter) } }
    val usedCategories = remember(expenses, categories) {
        val used = expenses.filter { !it.isIncome }.mapNotNull { it.categoryId }.toSet()
        categories.filter { !it.isIncome && it.id in used }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                tr("Comparar periodos", "Compare periods"),
                subtitle = tr("Elige las fechas y con qué compararlas", "Pick the dates and what to compare them with"),
                onBack = onBack,
            )
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Fechas", "Dates"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            tr("Este año", "This year") to DateRange.year(today.year),
                            tr("Año pasado", "Last year") to DateRange.year(today.year - 1),
                            tr("Este mes", "This month") to DateRange.month(YearMonth.from(today)),
                            tr("12 meses", "12 months") to DateRange(today.withDayOfMonth(1).minusMonths(11), today),
                        ).forEach { (label, r) ->
                            FilterChip(
                                selected = a == r,
                                onClick = { aStart = r.start.toEpochDay(); aEnd = r.end.toEpochDay(); selectedBar = null },
                                label = { Text(label) }, shape = CircleShape, colors = pillChipColors(), border = null,
                            )
                        }
                    }
                    DateRow(tr("Desde", "From"), a.start) { picking = "aStart" }
                    DateRow(tr("Hasta", "To"), a.end) { picking = "aEnd" }
                    Spacer(Modifier.height(4.dp))
                    Text(tr("Comparar con", "Compare with"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            CompareWith.PREVIOUS_YEAR to tr("Un año antes", "A year earlier"),
                            CompareWith.PREVIOUS_SPAN to tr("El tramo anterior", "The previous span"),
                            CompareWith.CUSTOM to tr("Otras fechas", "Other dates"),
                            CompareWith.NONE to tr("Nada", "Nothing"),
                        ).forEach { (w, label) ->
                            FilterChip(selected = compareWith == w, onClick = { compareWith = w; selectedBar = null }, label = { Text(label) }, shape = CircleShape, colors = pillChipColors(), border = null)
                        }
                    }
                    if (compareWith == CompareWith.CUSTOM) {
                        DateRow(tr("Desde", "From"), LocalDate.ofEpochDay(minOf(bStart, bEnd))) { picking = "bStart" }
                        DateRow(tr("Hasta", "To"), LocalDate.ofEpochDay(maxOf(bStart, bEnd))) { picking = "bEnd" }
                    } else if (b != null) {
                        Text(b.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text(tr("Todas", "All")) }, shape = CircleShape, colors = pillChipColors(), border = null)
                usedCategories.forEach { c ->
                    FilterChip(
                        selected = categoryId == c.id, onClick = { categoryId = if (categoryId == c.id) null else c.id; selectedBar = null },
                        label = { Text("${c.emoji} ${c.name}") }, shape = CircleShape, colors = pillChipColors(), border = null,
                    )
                }
            }
        }
        item {
            val tiles = mutableListOf(Triple(a.label(), formatCents(totalA), tr("${a.days} días", "${a.days} days")))
            if (b != null) {
                tiles += Triple(b.label(), formatCents(totalB), changeLabel(totalA, totalB))
            }
            StatGrid(tiles)
        }
        if (monthsA.size in 2..24) item {
            val labelsFormat = Lang.dateFormat("MMM")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Mes a mes", "Month by month"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    BarChart(
                        values = monthsA.map { it.second },
                        labels = monthsA.map { it.first.atDay(1).format(labelsFormat).take(3) },
                        selected = selectedBar,
                        valueLabel = { formatCents(it) },
                        onSelect = { selectedBar = if (selectedBar == it) null else it },
                    )
                    selectedBar?.let { i ->
                        val (m, v) = monthsA[i]
                        val other = monthsB?.getOrNull(i)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            m.atDay(1).format(Lang.dateFormat("MMMM yyyy")).replaceFirstChar { it.uppercase() } + ": " + formatCents(v) +
                                (other?.let { (om, ov) -> " · " + om.atDay(1).format(Lang.dateFormat("MMM yyyy")) + ": " + formatCents(ov) } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item { SectionHeader(tr("Por categoría", "By category")) }
        if (rows.isEmpty()) item { Text(tr("Sin gastos en estas fechas.", "No spending in these dates."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rows, key = { it.categoryId ?: -1L }) { r ->
            val c = r.categoryId?.let { byId[it] }
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp)
                        .then(if (c != null) Modifier.clickable { onOpenCategory(c.id) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(c, size = 36)
                    Spacer(Modifier.width(12.dp))
                    Text(c?.name ?: tr("Sin categoría", "Uncategorised"), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatCents(r.aCents), fontWeight = FontWeight.SemiBold)
                        if (b != null) {
                            Text(
                                formatCents(r.bCents) + " · " + changeLabel(r.aCents, r.bCents),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    r.bCents == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                                    r.aCents > r.bCents -> BudgetColors.over
                                    else -> BudgetColors.ok
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    picking?.let { which ->
        val initial = LocalDate.ofEpochDay(
            when (which) { "aStart" -> aStart; "aEnd" -> aEnd; "bStart" -> bStart; else -> bEnd }
        )
        DatePickerModal(initial, onDismiss = { picking = null }) { d ->
            when (which) {
                "aStart" -> aStart = d.toEpochDay()
                "aEnd" -> aEnd = d.toEpochDay()
                "bStart" -> bStart = d.toEpochDay()
                else -> bEnd = d.toEpochDay()
            }
            selectedBar = null
            picking = null
        }
    }
}

@Composable
private fun DateRow(label: String, date: LocalDate, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(
            selected = false, onClick = onClick,
            label = { Text(date.format(Lang.dateFormat("d MMM yyyy"))) },
            leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
            shape = CircleShape, colors = pillChipColors(), border = null,
        )
    }
}

/** «+12 %», «−5 %» o «nuevo» si antes no hubo gasto. */
private fun changeLabel(a: Long, b: Long): String = when {
    b == 0L && a == 0L -> "="
    b == 0L -> tr("nuevo", "new")
    else -> {
        val pct = (a - b).toDouble() / b * 100
        (if (pct > 0) "+" else if (pct < 0) "−" else "") + formatQuantity(kotlin.math.abs(pct), 0) + " %"
    }
}
