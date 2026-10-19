package com.midinero.control.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.domain.DateRange
import com.midinero.control.domain.TrackerKind
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.shortLabel
import com.midinero.control.domain.signedAmount
import com.midinero.control.domain.tr
import com.midinero.control.ui.HouseholdViewModel
import com.midinero.control.ui.MainViewModel
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.listSaver

/** Tramos de fechas que se ofrecen en los paneles. Mes y año se mueven con las flechas. */
enum class RangePreset {
    MONTH, YEAR, LAST_12, ALL, CUSTOM;

    val label: String get() = when (this) {
        MONTH -> tr("Mes", "Month")
        YEAR -> tr("Año", "Year")
        LAST_12 -> tr("12 meses", "12 months")
        ALL -> tr("Todo", "All")
        CUSTOM -> tr("Fechas…", "Dates…")
    }
}

/** El tramo elegido en un panel: un mes o un año (que se pueden mover), los últimos 12 meses, todo o unas fechas. */
@Stable
class RangeSelection(preset: RangePreset, anchor: LocalDate, start: LocalDate, end: LocalDate) {
    var preset by mutableStateOf(preset)
    /** Un día del mes o del año que se enseña. */
    var anchor by mutableStateOf(anchor)
    var start by mutableStateOf(start)
    var end by mutableStateOf(end)

    /** null = sin límite. */
    val range: DateRange?
        get() {
            val today = LocalDate.now()
            return when (preset) {
                RangePreset.MONTH -> DateRange.month(YearMonth.from(anchor))
                RangePreset.YEAR -> DateRange.year(anchor.year)
                RangePreset.LAST_12 -> DateRange(today.withDayOfMonth(1).minusMonths(11), today)
                RangePreset.ALL -> null
                RangePreset.CUSTOM -> DateRange(minOf(start, end), maxOf(start, end))
            }
        }

    fun shift(by: Long) {
        anchor = if (preset == RangePreset.MONTH) anchor.plusMonths(by) else anchor.plusYears(by)
    }

    companion object {
        val Saver = listSaver<RangeSelection, Long>(
            save = { listOf(it.preset.ordinal.toLong(), it.anchor.toEpochDay(), it.start.toEpochDay(), it.end.toEpochDay()) },
            restore = { RangeSelection(RangePreset.entries[it[0].toInt()], LocalDate.ofEpochDay(it[1]), LocalDate.ofEpochDay(it[2]), LocalDate.ofEpochDay(it[3])) },
        )
    }
}

@Composable
fun rememberRangeSelection(preset: RangePreset = RangePreset.YEAR): RangeSelection = rememberSaveable(saver = RangeSelection.Saver) {
    val today = LocalDate.now()
    RangeSelection(preset, today, today.withDayOfYear(1), today)
}

/** Chips de tramo y, debajo, el mes o año con flechas o las dos fechas. [onChange] avisa de cada cambio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RangeSelector(selection: RangeSelection, modifier: Modifier = Modifier, onChange: () -> Unit = {}) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RangePreset.entries.forEach { p ->
                FilterChip(
                    selected = p == selection.preset,
                    onClick = {
                        if (p != selection.preset && (p == RangePreset.MONTH || p == RangePreset.YEAR)) selection.anchor = LocalDate.now()
                        selection.preset = p
                        onChange()
                    },
                    label = { Text(p.label) },
                    shape = CircleShape,
                    colors = pillChipColors(),
                    border = null,
                )
            }
        }
        when (selection.preset) {
            RangePreset.MONTH, RangePreset.YEAR -> {
                val month = selection.preset == RangePreset.MONTH
                val current = if (month) YearMonth.from(selection.anchor) >= YearMonth.now() else selection.anchor.year >= LocalDate.now().year
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selection.shift(-1); onChange() }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        if (month) selection.anchor.format(com.midinero.control.domain.Lang.dateFormat("MMMM yyyy")).replaceFirstChar { it.uppercase() }
                        else selection.anchor.year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(onClick = { selection.shift(1); onChange() }, enabled = !current) { Text("›", style = MaterialTheme.typography.titleLarge) }
                }
            }
            RangePreset.CUSTOM -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val f = com.midinero.control.domain.Lang.dateFormat("d MMM yyyy")
                FilterChip(selected = false, onClick = { picking = "start" }, label = { Text(tr("Desde ", "From ") + selection.start.format(f)) }, shape = CircleShape, colors = pillChipColors(), border = null)
                FilterChip(selected = false, onClick = { picking = "end" }, label = { Text(tr("Hasta ", "To ") + selection.end.format(f)) }, shape = CircleShape, colors = pillChipColors(), border = null)
            }
            else -> Unit
        }
    }
    picking?.let { which ->
        DatePickerModal(if (which == "start") selection.start else selection.end, onDismiss = { picking = null }) { d ->
            if (which == "start") selection.start = d else selection.end = d
            picking = null
            onChange()
        }
    }
}

/** Fichas para ver solo un coche o una vivienda (o todos). Con uno o ninguno no se enseñan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetFilter(assets: List<com.midinero.control.data.Vehicle>, selectedId: Long?, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier) {
    if (assets.size < 2) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text(tr("Todos", "All")) }, shape = CircleShape, colors = pillChipColors(), border = null)
        assets.forEach { a ->
            FilterChip(
                selected = selectedId == a.id, onClick = { onSelect(if (selectedId == a.id) null else a.id) },
                label = { Text("${a.emoji} ${a.name}") }, shape = CircleShape, colors = pillChipColors(), border = null,
            )
        }
    }
}

/** Cuadrícula de cifras de dos en dos. */
@Composable
fun StatGrid(tiles: List<Triple<String, String, String?>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (title, value, detail) -> StatTile(title, value, detail, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * En qué categorías se apuntan los gastos de un panel, con opción de cambiarlas o crear una.
 * Sin ninguna, explica qué hacer.
 */
@Composable
fun KindCategoryCard(kind: TrackerKind, hvm: HouseholdViewModel, modifier: Modifier = Modifier) {
    val categories by hvm.categories.collectAsStateWithLifecycle()
    val mine = categories.filter { it.kind == kind.name }
    var choosing by rememberSaveable { mutableStateOf(false) }
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (mine.isEmpty()) {
                Text(tr("¿En qué categoría lo apuntas?", "Which category do you use for it?"), fontWeight = FontWeight.SemiBold)
                Text(
                    tr(
                        "Los gastos de esa categoría aparecerán aquí y, al apuntarlos, podrás añadir sus datos (${hintFor(kind)}).",
                        "Expenses in that category show up here and, when you log them, you can add their details (${hintFor(kind)}).",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { hvm.createKindCategory(kind) }) { Text(tr("Crear «${kind.categoryName}»", "Create “${kind.categoryName}”")) }
                    TextButton(onClick = { choosing = true }) { Text(tr("Usar una mía", "Use one of mine")) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Se apunta en", "Logged in"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(mine.joinToString(" · ") { "${it.emoji} ${it.name}" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { choosing = true }) { Text(tr("Cambiar", "Change")) }
                }
            }
        }
    }
    if (choosing) KindCategoryDialog(kind, categories, hvm, onDismiss = { choosing = false })
}

private fun hintFor(kind: TrackerKind): String = when (kind) {
    TrackerKind.FUEL -> tr("litros y km", "litres and km")
    TrackerKind.ELECTRICITY -> tr("kWh punta, llano, valle y excedentes", "peak, flat, off-peak and exported kWh")
    TrackerKind.GAS -> "kWh"
    TrackerKind.WATER -> "m³"
    TrackerKind.CAR -> tr("coche, km y qué se le hizo", "car, km and what was done")
    TrackerKind.HOME -> tr("la vivienda", "the home")
}

@Composable
private fun KindCategoryDialog(kind: TrackerKind, categories: List<Category>, hvm: HouseholdViewModel, onDismiss: () -> Unit) {
    val choices = categories.filter { !it.isIncome && (!it.hidden || it.kind == kind.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Categorías de ${kind.label.lowercase()}", "${kind.label} categories")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { c ->
                    val other = TrackerKind.of(c.kind)?.takeIf { it != kind }
                    Row(
                        Modifier.fillMaxWidth().clickable { hvm.setCategoryKind(c.id, if (c.kind == kind.name) null else kind) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.kind == kind.name, onCheckedChange = { on -> hvm.setCategoryKind(c.id, if (on) kind else null) })
                        Column(Modifier.weight(1f)) {
                            Text("${c.emoji} ${c.name}")
                            if (other != null) Text(tr("Ahora en ${other.label}", "Now in ${other.label}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Hecho", "Done")) } },
    )
}

/** Fila de un gasto de un panel: fecha, comercio, importe y una línea con sus datos. */
@Composable
fun PanelEntryRow(expense: Expense, category: Category?, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantBadge(expense.merchant, category, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(expense.merchant.ifBlank { category?.name.orEmpty() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(dateOf(expense.timestamp).shortLabel(), detail).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(signedAmount(expense), fontWeight = FontWeight.SemiBold)
    }
}

/** Editor de gastos para los paneles: nuevo (con su categoría y datos de partida) o uno ya apuntado. */
@Composable
fun PanelExpenseEditor(
    vm: MainViewModel,
    expense: Expense?,
    initialCategoryId: Long?,
    initialDetails: String? = null,
    onDismiss: () -> Unit,
) {
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    ExpenseEditorDialog(
        expense = expense,
        categories = categories.filter { !it.hidden || it.id == expense?.categoryId || it.id == initialCategoryId },
        incomeCategories = if (expense == null) emptyList() else incomeCategories,
        trips = trips,
        rateFor = vm.prefs::rateFor,
        onCreateCategory = vm::createCategory,
        initialCategoryId = initialCategoryId,
        initialDetails = initialDetails,
        onDismiss = onDismiss,
        onSave = { d ->
            if (expense == null) vm.addExpense(d)
            else {
                d.rate?.let { r -> d.originalCurrency?.let { vm.prefs.setRate(it, r) } }
                vm.updateExpense(
                    expense,
                    expense.copy(
                        amountCents = d.amountCents, merchant = d.merchant, note = d.note, categoryId = d.categoryId,
                        timestamp = d.timestamp, isIncome = d.isIncome,
                        originalCurrency = d.originalCurrency, originalAmountCents = d.originalAmountCents,
                        tripId = d.tripId, receipt = d.receipt, details = d.details, accountId = d.accountId,
                    ),
                    d.learn,
                )
            }
            onDismiss()
        },
        onDelete = if (expense != null) ({ onDismiss(); vm.deleteExpense(expense) }) else null,
    )
}
