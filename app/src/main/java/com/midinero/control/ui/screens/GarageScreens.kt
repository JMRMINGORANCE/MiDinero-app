package com.midinero.control.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.data.Vehicle
import com.midinero.control.domain.DateRange
import com.midinero.control.domain.ExpenseDetails
import com.midinero.control.domain.Garage
import com.midinero.control.domain.Lang
import com.midinero.control.domain.Meters
import com.midinero.control.domain.Reminder
import com.midinero.control.domain.ReminderKind
import com.midinero.control.domain.ServiceType
import com.midinero.control.domain.TrackerKind
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.formatQuantity
import com.midinero.control.domain.tr
import com.midinero.control.ui.HouseholdViewModel
import com.midinero.control.ui.MainViewModel
import java.time.LocalDate
import kotlin.math.roundToLong

private val longDate get() = Lang.dateFormat("d MMM yyyy")

/** Tus coches con lo que te cuestan y lo que les toca (ITV, aceite, seguro). */
@Composable
fun VehiclesScreen(onBack: () -> Unit, onOpenVehicle: (Long) -> Unit, hvm: HouseholdViewModel = viewModel()) {
    val allAssets by hvm.vehicles.collectAsStateWithLifecycle()
    val vehicles = allAssets.filter { !it.isHome }
    val categories by hvm.categories.collectAsStateWithLifecycle()
    val expenses by hvm.expenses.collectAsStateWithLifecycle()
    val policies by hvm.policies.collectAsStateWithLifecycle()
    val prices by hvm.prices.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val fuel = remember(categories, expenses) { Meters.entries(TrackerKind.FUEL, categories, expenses) }
    val car = remember(categories, expenses) { Meters.entries(TrackerKind.CAR, categories, expenses) }
    val reminders = remember(vehicles, car, policies) { Garage.reminders(vehicles, car, policies).filter { it.kind != ReminderKind.POLICY } }
    val year = DateRange.year(today.year)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader(tr("🚗 Coches", "🚗 Cars"), subtitle = tr("Gastos, km, ITV y mantenimientos de cada coche", "Costs, km, MOT and servicing for each car"), onBack = onBack) }
            val active = reminders.filter { it.isActive(today) }
            if (active.isNotEmpty()) item { RemindersCard(active, today) }
            if (vehicles.isEmpty()) item {
                EmptyState(
                    "🚗", tr("Añade tu coche", "Add your car"),
                    tr("Después, al apuntar un repostaje o un mantenimiento, elige el coche y apunta los km. Te avisaremos de la ITV y del cambio de aceite.", "Then, when you log a refuel or a service, pick the car and add the km. We'll remind you about the MOT and the oil change."),
                    action = tr("Añadir coche", "Add car"), onAction = { creating = true },
                )
            }
            items(vehicles, key = { it.id }) { v ->
                val s = remember(v, fuel, car, policies, prices) { Garage.summary(v, fuel, car, policies, prices, year) }
                val next = reminders.firstOrNull { it.refId == v.id }
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpenVehicle(v.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(v.emoji, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(v.plate.ifBlank { null }, s.lastOdometer?.let { formatQuantity(it.toDouble(), 0) + " km" }, if (v.archived) tr("archivado", "archived") else null).joinToString(" · ").ifBlank { tr("Sin matrícula", "No plate") },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatCents(s.totalCents), fontWeight = FontWeight.SemiBold)
                                Text(tr("este año", "this year"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val line = listOfNotNull(
                            s.fuel.litersPer100?.let { formatQuantity(it) + " l/100 km" },
                            s.centsPerKm?.let { formatCents((it * 100).roundToLong()) + tr(" cada 100 km", " per 100 km") },
                            next?.let { it.message(today) },
                        )
                        if (line.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(line.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionHeader(tr("Dónde se apuntan", "Where they're logged")) }
            item {
                Text(tr("Repostajes", "Refuels"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                KindCategoryCard(TrackerKind.FUEL, hvm)
            }
            item {
                Text(tr("Mantenimientos, ITV, impuesto…", "Servicing, MOT, tax…"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                KindCategoryCard(TrackerKind.CAR, hvm)
            }
        }
        if (vehicles.isNotEmpty()) {
            AppFab(tr("Nuevo coche", "New car"), action = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }
    }
    if (creating) VehicleEditor(null, onDismiss = { creating = false }, onSave = { hvm.saveVehicle(it) }, onDelete = null)
}

@Composable
private fun RemindersCard(reminders: List<Reminder>, today: LocalDate) {
    AppCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("🔔 Te toca", "🔔 Coming up"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            reminders.forEach { r ->
                Text(
                    "${r.title}: ${r.message(today)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Un coche: lo que ha costado en el tramo, sus avisos y cada repostaje y mantenimiento. */
@Composable
fun VehicleDetailScreen(vehicleId: Long, vm: MainViewModel, onBack: () -> Unit, hvm: HouseholdViewModel = viewModel()) {
    val vehicles by hvm.vehicles.collectAsStateWithLifecycle()
    val categories by hvm.categories.collectAsStateWithLifecycle()
    val expenses by hvm.expenses.collectAsStateWithLifecycle()
    val policies by hvm.policies.collectAsStateWithLifecycle()
    val prices by hvm.prices.collectAsStateWithLifecycle()
    val vehicle = vehicles.firstOrNull { it.id == vehicleId }
    val selection = rememberRangeSelection()
    var editing by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingItv by rememberSaveable { mutableStateOf(false) }
    if (vehicle == null) {
        Column(Modifier.padding(16.dp)) { ScreenHeader(tr("Coche", "Car"), onBack = onBack) }
        return
    }
    val today = LocalDate.now()
    val fuel = remember(categories, expenses) { Meters.entries(TrackerKind.FUEL, categories, expenses) }
    val car = remember(categories, expenses) { Meters.entries(TrackerKind.CAR, categories, expenses) }
    val s = remember(vehicle, fuel, car, policies, prices, selection.range) { Garage.summary(vehicle, fuel, car, policies, prices, selection.range) }
    val reminders = remember(vehicle, car) { Garage.reminders(listOf(vehicle), car, emptyList()) }
    val lastOil = remember(vehicle, car) { Garage.lastService(vehicle.id, ServiceType.OIL, car) }
    val byId = remember(categories) { categories.associateBy { it.id } }
    val fuelCategory = Meters.categoriesOf(TrackerKind.FUEL, categories).firstOrNull()
    val carCategory = Meters.categoriesOf(TrackerKind.CAR, categories).firstOrNull()
    val history = remember(s) {
        (s.fuel.fills.map { it.entry } + s.services).sortedByDescending { it.expense.timestamp }
    }
    val fills = s.fuel.fills.associateBy { it.entry.expense.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    "${vehicle.emoji} ${vehicle.name}",
                    subtitle = listOfNotNull(vehicle.plate.ifBlank { null }, s.lastOdometer?.let { formatQuantity(it.toDouble(), 0) + " km" }).joinToString(" · ").ifBlank { null },
                    onBack = onBack,
                    actions = { RoundAction(Icons.Filled.Edit, tr("Editar coche", "Edit car"), { editing = true }) },
                )
            }
            item { RangeSelector(selection) }
            item {
                StatGrid(
                    listOf(
                        Triple(tr("Total", "Total"), formatCents(s.totalCents), s.centsPerKm?.let { formatCents((it * 100).roundToLong()) + tr(" cada 100 km", " per 100 km") }),
                        Triple(tr("Combustible", "Fuel"), formatCents(s.fuel.totalCents), s.fuel.litersPer100?.let { formatQuantity(it) + " l/100 km" }),
                        Triple(tr("Mantenimiento", "Servicing"), formatCents(s.servicesCents), tr("ITV, impuesto y taller", "MOT, tax and garage")),
                        Triple(tr("Seguro", "Insurance"), formatCents(s.insuranceCents), tr("renovaciones del tramo", "renewals in range")),
                        Triple(tr("Km", "Km"), if (s.km > 0) formatQuantity(s.km, 0) else "—", s.fuel.liters.takeIf { it > 0 }?.let { formatQuantity(it, 0) + tr(" litros", " litres") }),
                    )
                )
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Avisos", "Reminders"), style = MaterialTheme.typography.titleMedium)
                        val itv = reminders.firstOrNull { it.kind == ReminderKind.ITV }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("📋 " + tr("ITV", "MOT"), fontWeight = FontWeight.Medium)
                                Text(
                                    itv?.let { tr("El ${it.dueDate.format(longDate)} · aviso ${vehicle.itvNoticeDays} días antes", "On ${it.dueDate.format(longDate)} · reminder ${vehicle.itvNoticeDays} days before") }
                                        ?: tr("Apunta la fecha de la próxima ITV y te avisamos", "Add the next MOT date and we'll remind you"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { pickingItv = true }) { Text(if (itv == null) tr("Poner fecha", "Set date") else tr("Cambiar", "Change")) }
                        }
                        val oil = reminders.firstOrNull { it.kind == ReminderKind.OIL }
                        Column {
                            Text("🛢️ " + tr("Cambio de aceite", "Oil change"), fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    vehicle.oilReminderMonths == null -> tr("Sin aviso (actívalo editando el coche)", "No reminder (turn it on by editing the car)")
                                    lastOil == null -> tr("Apunta un mantenimiento con «Aceite y filtros» y te avisamos ${vehicle.oilReminderMonths} meses después", "Log a service with “Oil and filters” and we'll remind you ${vehicle.oilReminderMonths} months later")
                                    else -> tr("Último: ${lastOil.date.format(longDate)}", "Last: ${lastOil.date.format(longDate)}") +
                                        (lastOil.details.odometerKm?.let { " · " + formatQuantity(it.toDouble(), 0) + " km" } ?: "") +
                                        (oil?.let { tr(" · próximo aviso el ${it.dueDate.format(longDate)}", " · next reminder on ${it.dueDate.format(longDate)}") } ?: "")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        reminders.filter { it.isActive(today) }.forEach { r ->
                            Text("🔔 " + r.message(today), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { adding = "fuel" }, enabled = fuelCategory != null, modifier = Modifier.weight(1f)) { Text(tr("⛽ Repostaje", "⛽ Refuel")) }
                    OutlinedButton(onClick = { adding = "car" }, enabled = carCategory != null, modifier = Modifier.weight(1f)) { Text(tr("🔧 Mantenimiento", "🔧 Service")) }
                }
                if (fuelCategory == null || carCategory == null) {
                    Text(
                        tr("Elige en Coches dónde se apuntan los repostajes y los mantenimientos.", "Choose in Cars where refuels and services are logged."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item { SectionHeader(tr("Historial", "History")) }
            if (history.isEmpty()) item {
                Text(tr("Nada en estas fechas.", "Nothing in these dates."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(history, key = { it.expense.id }) { e ->
                val f = fills[e.expense.id]
                val detail = if (f != null) {
                    listOfNotNull(
                        f.liters?.let { formatQuantity(it, 2) + " l" },
                        f.litersPer100?.let { formatQuantity(it) + " l/100" },
                        e.details.odometerKm?.let { formatQuantity(it.toDouble(), 0) + " km" },
                    ).joinToString(" · ")
                } else {
                    listOfNotNull(
                        e.details.services.joinToString(", ") { "${it.emoji} ${it.label}" }.ifBlank { null },
                        e.details.odometerKm?.let { formatQuantity(it.toDouble(), 0) + " km" },
                    ).joinToString(" · ")
                }
                Column {
                    PanelEntryRow(e.expense, e.expense.categoryId?.let { byId[it] }, detail.ifBlank { null }) { editingId = e.expense.id }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    adding?.let { what ->
        PanelExpenseEditor(
            vm, expense = null,
            initialCategoryId = if (what == "fuel") fuelCategory?.id else carCategory?.id,
            initialDetails = ExpenseDetails(vehicleId = vehicle.id).encode(),
            onDismiss = { adding = null },
        )
    }
    expenses.firstOrNull { it.id == editingId }?.let { old ->
        PanelExpenseEditor(vm, expense = old, initialCategoryId = null, onDismiss = { editingId = null })
    }
    if (editing) VehicleEditor(vehicle, onDismiss = { editing = false }, onSave = { hvm.saveVehicle(it) }, onDelete = { hvm.deleteVehicle(vehicle); onBack() })
    if (pickingItv) {
        DatePickerModal(
            initial = vehicle.itvDueEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today.plusYears(1),
            onDismiss = { pickingItv = false },
            onPicked = { d -> pickingItv = false; hvm.saveVehicle(vehicle.copy(itvDueEpochDay = d.toEpochDay())) },
        )
    }
}

private val vehicleEmojis = listOf("🚗", "🚙", "🚐", "🛻", "🏍️", "🛵", "🚚", "🏎️")
private val homeEmojis = listOf("🏠", "🏡", "🏢", "🏖️", "🏘️", "🏚️", "⛰️", "🏪")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VehicleEditor(
    vehicle: Vehicle?,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit,
    onDelete: (() -> Unit)?,
    /** Vivienda en lugar de coche: sin matrícula, ITV ni aceite. */
    home: Boolean = vehicle?.isHome == true,
) {
    var name by rememberSaveable { mutableStateOf(vehicle?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(vehicle?.emoji ?: if (home) "🏠" else "🚗") }
    var plate by rememberSaveable { mutableStateOf(vehicle?.plate ?: "") }
    var oilOn by rememberSaveable { mutableStateOf(vehicle?.oilReminderMonths != null || vehicle == null) }
    var oilMonths by rememberSaveable { mutableStateOf((vehicle?.oilReminderMonths ?: 11).toString()) }
    var itvDays by rememberSaveable { mutableStateOf((vehicle?.itvNoticeDays ?: 30).toString()) }
    var archived by rememberSaveable { mutableStateOf(vehicle?.archived ?: false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val months = oilMonths.toIntOrNull()?.takeIf { it in 1..60 }
    val days = itvDays.toIntOrNull()?.takeIf { it in 0..120 }
    EditorSheet(
        title = when {
            home -> if (vehicle == null) tr("Nueva vivienda", "New home") else tr("Editar vivienda", "Edit home")
            else -> if (vehicle == null) tr("Nuevo coche", "New car") else tr("Editar coche", "Edit car")
        },
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = name.isNotBlank() && (home || ((!oilOn || months != null) && days != null)),
        onPrimary = {
            onSave(
                (vehicle ?: Vehicle(name = "", kind = if (home) Vehicle.KIND_HOME else Vehicle.KIND_CAR)).copy(
                    name = name.trim(), emoji = emoji, plate = if (home) "" else plate,
                    oilReminderMonths = if (oilOn && !home) months else null, itvNoticeDays = days ?: 30, archived = archived,
                    itvDueEpochDay = if (home) null else vehicle?.itvDueEpochDay,
                )
            )
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (home) homeEmojis else vehicleEmojis).forEach { e ->
                FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) }, shape = CircleShape, colors = sheetChipColors(), border = null)
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) }, label = { Text(tr("Nombre", "Name")) },
            placeholder = { Text(if (home) tr("Ej.: Piso, Casa de la playa…", "E.g. Flat, Beach house…") else tr("Ej.: León, el de papá…", "E.g. León, Dad's car…")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (!home) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = plate, onValueChange = { plate = it.take(12) }, label = { Text(tr("Matrícula (opcional)", "Plate (optional)")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        FieldLabel(tr("Avisos", "Reminders"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Avisar del cambio de aceite", "Remind me about the oil change"), Modifier.weight(1f))
            Switch(checked = oilOn, onCheckedChange = { oilOn = it })
        }
        if (oilOn) {
            OutlinedTextField(
                value = oilMonths, onValueChange = { oilMonths = it.filter(Char::isDigit).take(2) },
                label = { Text(tr("Meses después del último cambio", "Months after the last change")) },
                isError = months == null, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = itvDays, onValueChange = { itvDays = it.filter(Char::isDigit).take(3) },
            label = { Text(tr("Avisar de la ITV estos días antes", "Remind me about the MOT this many days before")) },
            isError = days == null, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        }
        if (vehicle != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Archivado", "Archived"))
                    Text(tr("Ya no lo tienes: no avisa ni se ofrece al apuntar", "You no longer have it: no reminders and not offered when logging"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = archived, onCheckedChange = { archived = it })
            }
        }
        if (onDelete != null) {
            TextButton(onClick = { confirmDelete = true }) { Text(if (home) tr("Eliminar vivienda", "Delete home") else tr("Eliminar coche", "Delete car"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar «${vehicle?.name}»?", "Delete “${vehicle?.name}”?")) },
            text = {
                Text(
                    if (home) tr("Sus recibos y gastos no se borran: siguen en sus categorías, sin vivienda.", "Its bills and expenses aren't deleted: they stay in their categories, with no home.")
                    else tr("Sus repostajes y mantenimientos no se borran: siguen en sus categorías, sin coche.", "Its refuels and services aren't deleted: they stay in their categories, with no car.")
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDismiss(); onDelete() }) { Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}
