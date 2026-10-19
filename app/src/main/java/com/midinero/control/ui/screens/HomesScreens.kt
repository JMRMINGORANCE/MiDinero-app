package com.midinero.control.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.domain.DateRange
import com.midinero.control.domain.ExpenseDetails
import com.midinero.control.domain.Garage
import com.midinero.control.domain.Meters
import com.midinero.control.domain.TrackerKind
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.formatQuantity
import com.midinero.control.domain.tr
import com.midinero.control.ui.HouseholdViewModel
import com.midinero.control.ui.MainViewModel
import java.time.LocalDate

/** Tus viviendas (la casa, el piso de la playa…) con lo que cuesta cada una. */
@Composable
fun HomesScreen(onBack: () -> Unit, onOpenHome: (Long) -> Unit, hvm: HouseholdViewModel = viewModel()) {
    val assets by hvm.vehicles.collectAsStateWithLifecycle()
    val homes = assets.filter { it.isHome }
    val categories by hvm.categories.collectAsStateWithLifecycle()
    val expenses by hvm.expenses.collectAsStateWithLifecycle()
    val policies by hvm.policies.collectAsStateWithLifecycle()
    val prices by hvm.prices.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val entries = remember(categories, expenses) { Garage.homeKinds.associateWith { Meters.entries(it, categories, expenses) } }
    val year = DateRange.year(LocalDate.now().year)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    tr("🏠 Viviendas", "🏠 Homes"),
                    subtitle = tr("Luz, agua, gas, mantenimiento y seguro de cada casa", "Electricity, water, gas, repairs and insurance for each home"),
                    onBack = onBack,
                )
            }
            if (homes.isEmpty()) item {
                EmptyState(
                    "🏠", tr("Añade tus viviendas", "Add your homes"),
                    tr(
                        "Si tienes más de una casa (o compartes recibos), al apuntar la luz, el agua, el gas o un arreglo eliges de cuál es y ves lo que cuesta cada una. Con una sola no hace falta.",
                        "If you have more than one home (or share bills), when you log electricity, water, gas or a repair you pick which one it's for and see what each costs. With just one you don't need this.",
                    ),
                    action = tr("Añadir vivienda", "Add home"), onAction = { creating = true },
                )
            }
            items(homes, key = { it.id }) { h ->
                val s = remember(h, entries, policies, prices) { Garage.homeSummary(h, entries, policies, prices, year) }
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpenHome(h.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                Garage.homeKinds.mapNotNull { k -> s.byKind[k]?.takeIf { it != 0L }?.let { "${k.emoji} ${formatCents(it)}" } }.joinToString(" · ")
                                    .ifBlank { if (h.archived) tr("Archivada", "Archived") else tr("Sin gastos este año", "No spending this year") },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatCents(s.totalCents), fontWeight = FontWeight.SemiBold)
                            Text(tr("este año", "this year"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionHeader(tr("Dónde se apuntan", "Where they're logged")) }
            Garage.homeKinds.forEach { k ->
                item {
                    Text(k.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    KindCategoryCard(k, hvm)
                }
            }
        }
        if (homes.isNotEmpty()) AppFab(tr("Nueva vivienda", "New home"), action = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
    if (creating) VehicleEditor(null, onDismiss = { creating = false }, onSave = { hvm.saveVehicle(it) }, onDelete = null, home = true)
}

/** Una vivienda: lo que ha costado en el tramo, por suministro, y cada recibo o arreglo. */
@Composable
fun HomeDetailScreen(homeId: Long, vm: MainViewModel, onBack: () -> Unit, hvm: HouseholdViewModel = viewModel()) {
    val assets by hvm.vehicles.collectAsStateWithLifecycle()
    val categories by hvm.categories.collectAsStateWithLifecycle()
    val expenses by hvm.expenses.collectAsStateWithLifecycle()
    val policies by hvm.policies.collectAsStateWithLifecycle()
    val prices by hvm.prices.collectAsStateWithLifecycle()
    val home = assets.firstOrNull { it.id == homeId }
    val selection = rememberRangeSelection()
    var editing by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    if (home == null) {
        Column(Modifier.padding(16.dp)) { ScreenHeader(tr("Vivienda", "Home"), onBack = onBack) }
        return
    }
    val entries = remember(categories, expenses) { Garage.homeKinds.associateWith { Meters.entries(it, categories, expenses) } }
    val s = remember(home, entries, policies, prices, selection.range) { Garage.homeSummary(home, entries, policies, prices, selection.range) }
    val byId = remember(categories) { categories.associateBy { it.id } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    "${home.emoji} ${home.name}",
                    onBack = onBack,
                    actions = { RoundAction(Icons.Filled.Edit, tr("Editar vivienda", "Edit home"), { editing = true }) },
                )
            }
            item { RangeSelector(selection) }
            item {
                StatGrid(
                    listOf(Triple(tr("Total", "Total"), formatCents(s.totalCents), null)) +
                        Garage.homeKinds.map { k -> Triple("${k.emoji} ${if (k == TrackerKind.HOME) tr("Casa", "Home") else k.label}", formatCents(s.byKind[k] ?: 0), null) } +
                        listOf(Triple("🛡️ " + tr("Seguro", "Insurance"), formatCents(s.insuranceCents), tr("renovaciones del tramo", "renewals in range"))),
                )
            }
            item {
                val choices = Garage.homeKinds.mapNotNull { k -> Meters.categoriesOf(k, categories).firstOrNull()?.let { k to it } }
                if (choices.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { (k, c) ->
                            OutlinedButton(onClick = { adding = k.name }, modifier = Modifier.weight(1f)) { Text(c.emoji, maxLines = 1) }
                        }
                    }
                    Text(
                        tr("Apunta un recibo o un gasto de esta vivienda: ", "Log a bill or an expense for this home: ") + choices.joinToString(", ") { it.second.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item { SectionHeader(tr("Historial", "History")) }
            if (s.entries.isEmpty()) item { Text(tr("Nada en estas fechas.", "Nothing in these dates."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.entries, key = { it.second.expense.id }) { (k, e) ->
                val q = Meters.quantity(k, e.details)
                val detail = q?.takeIf { it > 0 }?.let { formatQuantity(it, 1) + " " + Meters.unit(k) }
                Column {
                    PanelEntryRow(e.expense, e.expense.categoryId?.let { byId[it] }, detail) { editingId = e.expense.id }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    adding?.let { name ->
        val kind = TrackerKind.of(name)
        PanelExpenseEditor(
            vm, expense = null,
            initialCategoryId = kind?.let { Meters.categoriesOf(it, categories).firstOrNull()?.id },
            initialDetails = ExpenseDetails(vehicleId = home.id).encode(),
            onDismiss = { adding = null },
        )
    }
    expenses.firstOrNull { it.id == editingId }?.let { old ->
        PanelExpenseEditor(vm, expense = old, initialCategoryId = null, onDismiss = { editingId = null })
    }
    if (editing) VehicleEditor(home, onDismiss = { editing = false }, onSave = { hvm.saveVehicle(it) }, onDelete = { hvm.deleteVehicle(home); onBack() }, home = true)
}
