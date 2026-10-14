package com.midinero.control.ui.screens

import com.midinero.control.domain.signedAmount
import com.midinero.control.domain.tr
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Trip
import com.midinero.control.domain.Currencies
import com.midinero.control.domain.TripStats
import com.midinero.control.domain.TripStatus
import com.midinero.control.domain.amountsHidden
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.LocalSnackbarHostState
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.BudgetColors
import com.midinero.control.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Viajes y eventos: cada uno con su presupuesto y sus gastos, aunque abarque varios meses. */
@Composable
fun TripsScreen(vm: MainViewModel, onBack: () -> Unit, onOpenTrip: (Long) -> Unit) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    val totals by vm.tripTotals.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val groups = trips.groupBy { TripStats.status(it, today) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ScreenHeader(
                    tr("Viajes y eventos", "Trips and events"),
                    subtitle = tr("Unas vacaciones, una boda, la reforma de casa... Ponle presupuesto y verás lo que llevas gastado, ", "Holidays, a wedding, a home renovation... Give it a budget and see how much you've spent, ") +
                        tr("aunque dure varios meses. Los gastos siguen contando en su mes y su categoría.", "even if it lasts several months. Expenses still count in their month and category."),
                    onBack = onBack,
                )
            }
            if (trips.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "✈️",
                        title = tr("Aún no tienes viajes ni eventos", "No trips or events yet"),
                        text = tr("Crea uno antes de irte y lo que pagues esos días se apuntará en él.", "Create one before you go and what you pay on those days will be logged in it."),
                    )
                }
            }
            listOf(
                TripStatus.ACTIVE to tr("En curso", "In progress"),
                TripStatus.UPCOMING to tr("Próximos", "Upcoming"),
                TripStatus.FINISHED to tr("Terminados", "Finished"),
            ).forEach { (status, title) ->
                val list = groups[status].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "h_$status") {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.asHeading())
                    }
                    items(list, key = { it.id }) { trip ->
                        TripCard(trip, totals[trip.id] ?: 0L, onClick = { onOpenTrip(trip.id) })
                    }
                }
            }
        }
        AppFab(
            text = tr("Nuevo", "New"),
            action = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        TripDialog(
            trip = null,
            onDismiss = { creating = false },
            onSave = { trip, collect ->
                creating = false
                vm.saveTrip(trip, collect) { n ->
                    if (n > 0) scope.launch { snackbar.showSnackbar("$n ${if (n == 1) tr("gasto ya apuntado se ha añadido", "existing expense has been added") else tr("gastos ya apuntados se han añadido", "existing expenses have been added")}${tr(" a ", " to ")}${trip.name}") }
                }
            },
        )
    }
}

@Composable
private fun TripCard(trip: Trip, spentCents: Long, onClick: () -> Unit) {
    val budget = trip.budgetCents
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(trip.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(TripStats.dateLabel(trip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatCents(spentCents), fontWeight = FontWeight.SemiBold)
                    if (budget != null && budget > 0) {
                        Text(tr("de ${formatCents(budget)}", "of ${formatCents(budget)}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (budget != null && budget > 0) {
                val ratio = spentCents.toFloat() / budget
                RankBar(ratio, tripColor(ratio), Modifier.padding(top = 10.dp))
            }
        }
    }
}

private fun tripColor(ratio: Float) = when {
    ratio > 1f -> BudgetColors.over
    ratio >= 0.8f -> BudgetColors.warning
    else -> BudgetColors.ok
}

/** Detalle de un viaje: cuánto va, a qué ritmo, en qué, y sus movimientos. */
@Composable
fun TripDetailScreen(vm: MainViewModel, tripId: Long, onBack: () -> Unit) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val trip = trips.firstOrNull { it.id == tripId }
    val expenses by remember(tripId) { vm.tripExpenses(tripId) }.collectAsStateWithLifecycle(emptyList())
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (trip == null) {
        Column(Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver", "Back")) }
            Text(tr("Este viaje ya no existe.", "This trip no longer exists."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val summary = TripStats.summarize(trip, expenses)
    val byId = categories.associateBy { it.id }
    val status = TripStats.status(trip)

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver", "Back")) }
                Text(
                    "${trip.emoji} ${trip.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).asHeading(),
                )
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = tr("Editar viaje", "Edit trip")) }
            }
            Text(
                TripStats.dateLabel(trip) + when (status) {
                    TripStatus.ACTIVE -> tr(" · en curso", " · in progress")
                    TripStatus.UPCOMING -> tr(" · próximamente", " · coming up")
                    TripStatus.FINISHED -> tr(" · terminado", " · finished")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    tr("Gastado", "Spent"),
                    formatCents(summary.totalCents),
                    "${summary.count} ${if (summary.count == 1) tr("gasto", "expense") else tr("gastos", "expenses")}",
                    Modifier.weight(1f),
                )
                StatTile(
                    tr("Por día", "Per day"),
                    formatCents(summary.perDayCents),
                    "${summary.days} ${if (summary.days == 1) tr("día", "day") else tr("días", "days")}",
                    Modifier.weight(1f),
                )
            }
        }
        val budget = trip.budgetCents
        if (budget != null && budget > 0) {
            item {
                val ratio = summary.totalCents.toFloat() / budget
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Text(tr("Presupuesto", "Budget"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(formatCents(budget), fontWeight = FontWeight.SemiBold)
                        }
                        RankBar(ratio, tripColor(ratio), Modifier.padding(vertical = 8.dp))
                        val remaining = summary.remainingCents ?: 0L
                        Text(
                            if (remaining >= 0) tr("Te quedan ${formatCents(remaining)}", "${formatCents(remaining)} left") else tr("Te has pasado ${formatCents(-remaining)}", "You're over by ${formatCents(-remaining)}"),
                            color = if (remaining >= 0) MaterialTheme.colorScheme.onSurface else BudgetColors.over,
                        )
                        summary.projectedCents?.let { p ->
                            Text(
                                tr("A este ritmo acabarás gastando unos ${formatCents(p)}", "At this pace you'll spend about ${formatCents(p)}") + if (p > budget) tr(", más de lo previsto.", ", more than planned.") else ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (summary.byCategory.isNotEmpty()) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("En qué se ha ido", "Where it went"), fontWeight = FontWeight.SemiBold)
                        val max = summary.byCategory.first().second.coerceAtLeast(1L)
                        summary.byCategory.forEach { (catId, cents) ->
                            val cat = catId?.let { byId[it] }
                            Row(Modifier.padding(top = 8.dp)) {
                                Text(cat?.let { "${it.emoji} ${it.name}" } ?: tr("Sin categoría", "Uncategorised"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(formatCents(cents), style = MaterialTheme.typography.bodyMedium)
                            }
                            RankBar(
                                cents.toFloat() / max,
                                cat?.let { parseHexColor(it.colorHex) } ?: MaterialTheme.colorScheme.outline,
                                Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(tr("Movimientos", "Transactions"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.asHeading())
            if (expenses.isEmpty()) {
                Text(
                    if (trip.autoAssign) tr("Lo que apuntes entre esas fechas aparecerá aquí. También puedes elegir el viaje al editar un gasto.", "What you log between those dates will appear here. You can also choose the trip when editing an expense.")
                    else tr("Elige este viaje al apuntar o editar un gasto para que aparezca aquí.", "Choose this trip when logging or editing an expense for it to appear here."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(expenses, key = { it.id }) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { editingId = e.id }, modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            listOfNotNull(e.categoryId?.let { byId[it]?.emoji }, e.merchant).joinToString(" "),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(dateOf(e.timestamp).shortLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(signedAmount(e), fontWeight = FontWeight.SemiBold)
                    val code = e.originalCurrency
                    val orig = e.originalAmountCents
                    if (code != null && orig != null && !amountsHidden()) {
                        Text(Currencies.format(orig, code), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
        }
        item {
            TextButton(onClick = { confirmDelete = true }) { Text(tr("Eliminar viaje", "Delete trip"), color = MaterialTheme.colorScheme.error) }
        }
    }

    expenses.firstOrNull { it.id == editingId }?.let { old ->
        ExpenseEditorDialog(
            expense = old,
            categories = expenseCategories,
            incomeCategories = incomeCategories,
            merchantSuggestions = merchants,
            trips = trips,
            rateFor = vm.prefs::rateFor,
            onCreateCategory = vm::createCategory,
            onDismiss = { editingId = null },
            onSave = { d ->
                d.rate?.let { r -> d.originalCurrency?.let { vm.prefs.setRate(it, r) } }
                vm.updateExpense(
                    old,
                    old.copy(
                        amountCents = d.amountCents, merchant = d.merchant, note = d.note, categoryId = d.categoryId,
                        timestamp = d.timestamp, isIncome = d.isIncome,
                        originalCurrency = d.originalCurrency, originalAmountCents = d.originalAmountCents,
                        tripId = d.tripId,
                        receipt = d.receipt,
                    ),
                    d.learn,
                )
                editingId = null
            },
            onDelete = {
                editingId = null
                vm.deleteExpense(old)
            },
        )
    }

    if (editing) {
        TripDialog(
            trip = trip,
            onDismiss = { editing = false },
            onSave = { t, collect ->
                editing = false
                vm.saveTrip(t, collect)
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar ${trip.name}?", "Delete ${trip.name}?")) },
            text = { Text(tr("Sus gastos no se borran: siguen en su mes y su categoría, solo dejan de estar agrupados en este viaje.", "Its expenses aren't deleted: they stay in their month and category, they're just no longer grouped in this trip.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteTrip(trip)
                    onBack()
                }) { Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

private val tripEmojis = listOf("✈️", "🏖️", "🏔️", "🚗", "🎉", "💍", "🎄", "🏠", "🎓", "⚽")

/** Crear o editar un viaje: nombre, icono, fechas, presupuesto y si recoge solo los gastos. */
@Composable
private fun TripDialog(trip: Trip?, onDismiss: () -> Unit, onSave: (Trip, collectExisting: Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf(trip?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(trip?.emoji ?: tripEmojis.first()) }
    var budgetText by rememberSaveable { mutableStateOf(trip?.budgetCents?.let { centsToInput(it) } ?: "") }
    var start by rememberSaveable { mutableStateOf(trip?.let { LocalDate.ofEpochDay(it.startDay) } ?: LocalDate.now()) }
    var end by rememberSaveable {
        mutableStateOf<LocalDate?>(if (trip != null) trip.endDay?.let { LocalDate.ofEpochDay(it) } else LocalDate.now().plusDays(6))
    }
    var autoAssign by rememberSaveable { mutableStateOf(trip?.autoAssign ?: true) }
    var collect by rememberSaveable { mutableStateOf(true) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val budget = if (budgetText.isBlank()) null else parseUserAmount(budgetText)
    val validDates = end.let { it == null || !it.isBefore(start) }
    val canSave = name.isNotBlank() && (budgetText.isBlank() || budget != null) && validDates
    val startsInPast = !start.isAfter(LocalDate.now())

    EditorSheet(
        title = if (trip == null) tr("Nuevo viaje o evento", "New trip or event") else tr("Editar ${trip.name}", "Edit ${trip.name}"),
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = canSave,
        onPrimary = {
            val base = trip ?: Trip(name = "", startDay = 0)
            onSave(
                base.copy(
                    name = name.trim(),
                    emoji = emoji,
                    budgetCents = budget?.takeIf { it > 0 },
                    startDay = start.toEpochDay(),
                    endDay = end?.toEpochDay(),
                    autoAssign = autoAssign,
                ),
                startsInPast && collect,
            )
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text(tr("Nombre (ej. Verano en Lisboa)", "Name (e.g. Summer in Lisbon)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tripEmojis.forEach { e ->
                TextButton(
                    onClick = { emoji = e },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(e, style = if (e == emoji) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(start.shortLabel(), maxLines = 1)
            }
            OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.weight(1f)) {
                Text(end?.shortLabel() ?: tr("Sin fin", "No end"), maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = end == null, onCheckedChange = { end = if (it) null else start.plusDays(6) })
            Text(tr("Sin fecha de fin (una reforma, un bebé...)", "No end date (a renovation, a baby...)"), style = MaterialTheme.typography.bodySmall)
        }
        if (!validDates) {
            Text(tr("La fecha de fin es anterior a la de inicio", "The end date is before the start date"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = budgetText,
            onValueChange = { budgetText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) },
            label = { Text(tr("Presupuesto (${com.midinero.control.domain.Currencies.symbol()}, opcional)", "Budget (${com.midinero.control.domain.Currencies.symbol()}, optional)")) },
            singleLine = true,
            isError = budgetText.isNotBlank() && budget == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Apuntar aquí lo de esas fechas", "Log those dates here"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    tr("Los gastos nuevos de esos días se asignan solos a este viaje.", "New expenses on those days are assigned to this trip automatically."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoAssign, onCheckedChange = { autoAssign = it })
        }
        if (startsInPast) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = collect, onCheckedChange = { collect = it })
                Text(tr("Añadir también los gastos ya apuntados en esas fechas", "Also add expenses already logged on those dates"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    picking?.let { which ->
        DatePickerModal(
            initial = if (which == "start") start else end ?: start,
            onDismiss = { picking = null },
            onPicked = {
                if (which == "start") {
                    start = it
                    end?.let { e -> if (e.isBefore(it)) end = it }
                } else end = it
                picking = null
            },
        )
    }
}
