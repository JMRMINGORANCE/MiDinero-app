package com.midinero.control.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.data.Expense
import com.midinero.control.domain.ExpenseDetails
import com.midinero.control.domain.Lang
import com.midinero.control.domain.MeterMonth
import com.midinero.control.domain.Meters
import com.midinero.control.domain.TrackerKind
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.formatQuantity
import com.midinero.control.domain.formatUnitPrice
import com.midinero.control.domain.tr
import com.midinero.control.ui.HouseholdViewModel
import com.midinero.control.ui.MainViewModel
import java.time.YearMonth
import kotlin.math.roundToLong

/** Colores de los tramos de la luz (con su nombre siempre al lado: nunca solo el color). */
private val peakColor = Color(0xFFE8590C)
private val flatColor = Color(0xFFF2B705)
private val valleyColor = Color(0xFF2B7BC4)

/** Panel de combustible, luz, gas o agua: lo gastado, el consumo, el precio por unidad y cada recibo. */
@Composable
fun MeterScreen(kind: TrackerKind, vm: MainViewModel, onBack: () -> Unit, hvm: HouseholdViewModel = viewModel()) {
    val categories by hvm.categories.collectAsStateWithLifecycle()
    val expenses by hvm.expenses.collectAsStateWithLifecycle()
    val vehicles by hvm.vehicles.collectAsStateWithLifecycle()
    val selection = rememberRangeSelection()
    var assetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var chartMode by rememberSaveable { mutableStateOf(0) }
    var selectedBar by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val all = remember(kind, categories, expenses) { Meters.entries(kind, categories, expenses) }
    val range = selection.range
    // El combustible se filtra por coche; la luz, el agua y el gas, por vivienda
    val assets = vehicles.filter { (it.isHome == (kind != TrackerKind.FUEL)) && (!it.archived || it.id == assetId) }
    val entries = remember(all, range, assetId) { com.midinero.control.domain.Garage.forAsset(Meters.inRange(all, range), assetId) }
    val byId = remember(categories) { categories.associateBy { it.id } }
    val kindCategories = remember(categories) { Meters.categoriesOf(kind, categories) }
    val fuel = remember(entries, kind) { if (kind == TrackerKind.FUEL) Meters.fuel(entries) else null }
    val months = remember(entries) { Meters.monthly(entries) }
    // El gráfico enseña los meses del tramo (como mucho los 12 últimos con datos si es «Todo»)
    val chartMonths: List<YearMonth> = remember(range, months) {
        range?.months()?.filter { !it.isAfter(YearMonth.now()) } ?: months.map { it.month }.takeLast(12)
    }
    val unit = Meters.unit(kind)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    "${kind.emoji} ${kind.label}",
                    subtitle = when (kind) {
                        TrackerKind.FUEL -> tr("Lo que gastas, el precio del litro y lo que consume el coche", "What you spend, the price per litre and what the car uses")
                        TrackerKind.ELECTRICITY -> tr("Recibos, kWh por tramos y excedentes de tus placas", "Bills, kWh by period and what your panels export")
                        TrackerKind.GAS -> tr("Recibos y kWh de cada mes", "Bills and kWh each month")
                        else -> tr("Recibos y m³ de cada mes", "Bills and m³ each month")
                    },
                    onBack = onBack,
                )
            }
            item { KindCategoryCard(kind, hvm) }
            if (kindCategories.isNotEmpty()) {
                item { RangeSelector(selection, onChange = { selectedBar = null }) }
                if (assets.size >= 2) item { AssetFilter(assets, assetId, { assetId = it; selectedBar = null }) }
                item {
                    val total = entries.sumOf { it.cents }
                    val tiles = mutableListOf<Triple<String, String, String?>>()
                    tiles += Triple(tr("Gastado", "Spent"), formatCents(total), countLabel(kind, entries.size))
                    if (fuel != null) {
                        tiles += Triple(tr("Litros", "Litres"), if (fuel.liters > 0) formatQuantity(fuel.liters) + " l" else "—", fuel.pricePerLiter?.let { formatUnitPrice(it) + tr(" el litro", " per litre") })
                        tiles += Triple(tr("Consumo", "Consumption"), fuel.litersPer100?.let { formatQuantity(it) + " l/100 km" } ?: "—", tr("de media", "on average"))
                        tiles += Triple(tr("Cada 100 km", "Per 100 km"), fuel.costPer100?.let { formatCents(it.roundToLong()) } ?: "—", if (fuel.km > 0) formatQuantity(fuel.km, 0) + " km" else null)
                    } else {
                        val qty = entries.sumOf { Meters.quantity(kind, it.details) ?: 0.0 }
                        val price = Meters.unitPrice(kind, entries)
                        tiles += Triple(tr("Consumo", "Usage"), if (qty > 0) formatQuantity(qty, 0) + " $unit" else "—", price?.let { formatUnitPrice(it, 4) + " / $unit" })
                        val withData = months.size
                        if (withData > 0) tiles += Triple(tr("Al mes", "Per month"), formatCents(total / withData), tr("de media ($withData meses)", "on average ($withData months)"))
                        if (kind == TrackerKind.ELECTRICITY) {
                            val exported = entries.sumOf { it.details.kwhExported ?: 0.0 }
                            if (exported > 0) tiles += Triple(tr("Excedentes", "Exported"), formatQuantity(exported, 0) + " kWh", tr("vertidos a la red", "sent to the grid"))
                        }
                    }
                    StatGrid(tiles)
                }
                fuel?.let { f ->
                    Meters.consumptionAlert(f.fills)?.let { over ->
                        item {
                            AppCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.errorContainer) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(tr("⚠️ El último repostaje gastó un ${formatQuantity(over * 100, 0)} % más", "⚠️ The last refuel used ${formatQuantity(over * 100, 0)} % more"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(
                                        tr("Si se repite, puede ser buen momento para un limpiainyectores o una revisión (presión de ruedas, filtros…).", "If it happens again, it may be time for an injector cleaner or a check-up (tyre pressure, filters…)."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }
                if (chartMonths.isNotEmpty() && entries.isNotEmpty()) item {
                    MeterChart(kind, chartMonths, months, fuel, chartMode, { chartMode = it; selectedBar = null }, selectedBar, { selectedBar = if (selectedBar == it) null else it })
                }
                if (kind == TrackerKind.ELECTRICITY && months.any { it.kwhConsumed > 0 }) item {
                    ElectricityCard(months, context)
                }
                item {
                    SectionHeader(if (kind == TrackerKind.FUEL) tr("Repostajes", "Refuels") else tr("Recibos", "Bills"))
                }
                if (entries.isEmpty()) item {
                    Text(
                        tr("Aún no hay nada en estas fechas. Apunta uno con el botón de abajo o desde el editor de gastos eligiendo su categoría.", "Nothing in these dates yet. Add one with the button below or from the expense editor by choosing its category."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val fills = fuel?.fills?.associateBy { it.entry.expense.id }
                items(entries.reversed(), key = { it.expense.id }) { e ->
                    Column {
                        PanelEntryRow(e.expense, e.expense.categoryId?.let { byId[it] }, entryDetail(kind, e.details, fills?.get(e.expense.id), e.cents)) { editingId = e.expense.id }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        if (kindCategories.isNotEmpty()) {
            AppFab(
                text = if (kind == TrackerKind.FUEL) tr("Añadir repostaje", "Add refuel") else tr("Añadir recibo", "Add bill"),
                action = { adding = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (adding) PanelExpenseEditor(
        vm, expense = null, initialCategoryId = kindCategories.firstOrNull()?.id,
        initialDetails = assetId?.let { com.midinero.control.domain.ExpenseDetails(vehicleId = it).encode() },
        onDismiss = { adding = false },
    )
    expenses.firstOrNull { it.id == editingId }?.let { old: Expense ->
        PanelExpenseEditor(vm, expense = old, initialCategoryId = null, onDismiss = { editingId = null })
    }
}

private fun countLabel(kind: TrackerKind, n: Int): String = when {
    kind == TrackerKind.FUEL -> if (n == 1) tr("1 repostaje", "1 refuel") else tr("$n repostajes", "$n refuels")
    else -> if (n == 1) tr("1 recibo", "1 bill") else tr("$n recibos", "$n bills")
}

/** Línea de datos de cada recibo o repostaje. */
private fun entryDetail(kind: TrackerKind, d: ExpenseDetails, fill: com.midinero.control.domain.FuelFill?, cents: Long): String? {
    val parts = mutableListOf<String>()
    when (kind) {
        TrackerKind.FUEL -> {
            fill?.liters?.let { parts += formatQuantity(it, 2) + " l" }
            fill?.pricePerLiter?.let { parts += formatUnitPrice(it) + "/l" }
            fill?.litersPer100?.let { parts += formatQuantity(it) + " l/100" }
            fill?.km?.let { parts += formatQuantity(it, 0) + " km" }
        }
        TrackerKind.ELECTRICITY -> {
            d.kwhConsumed?.let { total ->
                parts += formatQuantity(total, 0) + " kWh"
                if (total > 0) parts += formatUnitPrice(cents / total, 4) + "/kWh"
            }
            d.kwhExported?.takeIf { it > 0 }?.let { parts += "☀️ " + formatQuantity(it, 0) + " kWh" }
        }
        TrackerKind.GAS, TrackerKind.WATER -> Meters.quantity(kind, d)?.let { q ->
            val unit = Meters.unit(kind)
            parts += formatQuantity(q, 1) + " $unit"
            if (q > 0) parts += formatUnitPrice(cents / q, 4) + "/$unit"
        }
        TrackerKind.CAR, TrackerKind.HOME -> Unit
    }
    return parts.joinToString(" · ").ifBlank { null }
}

@Composable
private fun MeterChart(
    kind: TrackerKind,
    chartMonths: List<YearMonth>,
    months: List<MeterMonth>,
    fuel: com.midinero.control.domain.FuelSummary?,
    mode: Int,
    onMode: (Int) -> Unit,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val byMonth = months.associateBy { it.month }
    val unit = Meters.unit(kind)
    // Valores como enteros para el gráfico: céntimos, o la cantidad ×100
    val modes = if (kind == TrackerKind.FUEL) listOf(tr("Importe", "Amount"), tr("Litros", "Litres"), "l/100 km") else listOf(tr("Importe", "Amount"), unit)
    val fillsByMonth = fuel?.fills?.groupBy { YearMonth.from(it.entry.date) }.orEmpty()
    val values: List<Long> = chartMonths.map { m ->
        when (mode) {
            0 -> byMonth[m]?.cents ?: 0L
            1 -> if (kind == TrackerKind.FUEL) ((fillsByMonth[m]?.sumOf { it.liters ?: 0.0 } ?: 0.0) * 100).roundToLong()
                else ((byMonth[m]?.let { Meters.quantity(kind, it) } ?: 0.0) * 100).roundToLong()
            else -> {
                val list = fillsByMonth[m].orEmpty().filter { it.liters != null && it.km != null }
                val km = list.sumOf { it.km!! }
                if (km > 0) (list.sumOf { it.liters!! } / km * 100 * 100).roundToLong() else 0L
            }
        }
    }
    val monthFormat = Lang.dateFormat("MMM")
    val labels = chartMonths.map { it.atDay(1).format(monthFormat).take(3) }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SegmentedTabs(modes, mode, onMode, track = MaterialTheme.colorScheme.surfaceContainerHigh)
            Spacer(Modifier.height(12.dp))
            BarChart(
                values = values,
                labels = labels,
                selected = selected,
                valueLabel = { v ->
                    when (mode) {
                        0 -> formatCents(v)
                        1 -> formatQuantity(v / 100.0, 0) + if (kind == TrackerKind.FUEL) " l" else " $unit"
                        else -> formatQuantity(v / 100.0)
                    }
                },
                onSelect = onSelect,
            )
            selected?.let { i ->
                chartMonths.getOrNull(i)?.let { m ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        m.atDay(1).format(Lang.dateFormat("MMMM yyyy")).replaceFirstChar { it.uppercase() } + ": " + when (mode) {
                            0 -> formatCents(values[i])
                            1 -> formatQuantity(values[i] / 100.0, 1) + if (kind == TrackerKind.FUEL) " l" else " $unit"
                            else -> if (values[i] > 0) formatQuantity(values[i] / 100.0) + " l/100 km" else tr("sin km apuntados", "no km logged")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Reparto por tramos y el resumen para comparar tarifas. */
@Composable
private fun ElectricityCard(months: List<MeterMonth>, context: Context) {
    val peak = months.sumOf { it.kwhPeak }
    val flat = months.sumOf { it.kwhFlat }
    val valley = months.sumOf { it.kwhValley }
    val total = peak + flat + valley
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Reparto por tramos", "Split by period"), style = MaterialTheme.typography.titleMedium)
            ShareBar(
                listOf(
                    ShareSegment(tr("Punta", "Peak"), (peak * 10).roundToLong(), peakColor),
                    ShareSegment(tr("Llano", "Flat"), (flat * 10).roundToLong(), flatColor),
                    ShareSegment(tr("Valle", "Off-peak"), (valley * 10).roundToLong(), valleyColor),
                )
            )
            listOf(Triple(tr("Punta", "Peak"), peak, peakColor), Triple(tr("Llano", "Flat"), flat, flatColor), Triple(tr("Valle", "Off-peak"), valley, valleyColor)).forEach { (name, v, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(end = 8.dp).size(10.dp).background(color, CircleShape))
                    Text(name, Modifier.weight(1f))
                    Text(formatQuantity(v, 0) + " kWh" + if (total > 0) " · " + formatQuantity(v / total * 100, 0) + " %" else "", fontWeight = FontWeight.Medium)
                }
            }
            Text(
                tr(
                    "Con estos datos y los precios de tu contrato (potencia, kWh y lo que te pagan por los excedentes) puedes comprobar en un comparador si hay una tarifa mejor.",
                    "With these figures and your contract prices (power, kWh and what you're paid for exports) you can check a comparison site for a better tariff.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val text = Meters.electricitySummary(months)
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("MiDinero", text))
                    Toast.makeText(context, tr("Resumen copiado", "Summary copied"), Toast.LENGTH_SHORT).show()
                }) { Text(tr("Copiar resumen", "Copy summary")) }
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.luzfija.es")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Toast.makeText(context, tr("No hay navegador para abrirlo", "No browser to open it"), Toast.LENGTH_SHORT).show() }
                }) { Text(tr("Abrir luzfija.es", "Open luzfija.es")) }
            }
        }
    }
}
