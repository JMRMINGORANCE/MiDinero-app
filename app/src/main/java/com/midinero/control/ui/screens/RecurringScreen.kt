package com.midinero.control.ui.screens

import androidx.compose.foundation.lazy.itemsIndexed
import com.midinero.control.domain.CategoryNames
import com.midinero.control.domain.tr
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.data.Frequency
import com.midinero.control.data.RecurringExpense
import com.midinero.control.data.RecurringHistory
import com.midinero.control.domain.Recurrence
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.recurringTemplates
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.LocalSnackbarHostState
import com.midinero.control.ui.MainViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

private val frequencyLabels get() = listOf(
    Frequency.WEEKLY to tr("Semanal", "Weekly"),
    Frequency.MONTHLY to tr("Mensual", "Monthly"),
    Frequency.QUARTERLY to tr("Trimestral", "Quarterly"),
    Frequency.YEARLY to tr("Anual", "Yearly"),
)

@Composable
fun RecurringScreen(vm: MainViewModel, onBack: (() -> Unit)? = null) {
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val candidates by vm.subscriptionCandidates.collectAsStateWithLifecycle()
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val editing = editingId?.let { id -> recurring.firstOrNull { it.id == id } }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val active = recurring.filter { it.active && !it.isIncome }
    val equivalent = active.sumOf { Recurrence.monthlyEquivalentCents(it) }
    val yearly = active.sumOf { Recurrence.yearlyCents(it) }
    // Activos primero, por fecha del próximo cargo; luego los pausados.
    val sorted = recurring.sortedWith(compareBy<RecurringExpense>({ !it.active }, { it.nextDueEpochDay }))
    val next30 = LocalDate.now().plusDays(30).toEpochDay()
    val upcoming30 = active.filter { it.nextDueEpochDay <= next30 }.sumOf { it.amountCents }
    val fixedIncome = recurring.filter { it.active && it.isIncome }.sumOf { Recurrence.monthlyEquivalentCents(it) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                ScreenHeader(
                    tr("Cargos fijos", "Recurring"),
                    subtitle = tr("Alquileres, suscripciones, cuotas y otros cargos. Se apuntan solos el día que toca.", "Rent, subscriptions, instalments and other charges. They're logged automatically on the due date."),
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item {
                HeroCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(tr("Gastos fijos al mes", "Recurring costs per month"), style = MaterialTheme.typography.labelLarge)
                        AnimatedMoney(equivalent, MaterialTheme.typography.displaySmall.merge(com.midinero.control.ui.theme.MoneyStyle))
                        Text(tr("Al año: ${formatCents(yearly)}", "Per year: ${formatCents(yearly)}"), style = MaterialTheme.typography.bodyMedium)
                        if (upcoming30 > 0) {
                            Text(
                                tr("En los próximos 30 días: ${formatCents(upcoming30)}", "In the next 30 days: ${formatCents(upcoming30)}"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (fixedIncome > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr("Ingresos fijos: +${formatCents(fixedIncome)}/mes · te quedan ${formatCents(fixedIncome - equivalent)} libres", "Recurring income: +${formatCents(fixedIncome)}/month · ${formatCents(fixedIncome - equivalent)} left free"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            val renewals = com.midinero.control.domain.Renewals.upcoming(recurring)
            if (renewals.isNotEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("📅 Próximas renovaciones", "📅 Upcoming renewals"), fontWeight = FontWeight.SemiBold)
                            Text(
                                tr("Cargos anuales y trimestrales de los próximos 60 días. Te avisamos una semana antes de cada renovación anual, por si quieres darte de baja.", "Yearly and quarterly charges in the next 60 days. You get a reminder a week before each yearly renewal, in case you want to cancel."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            renewals.forEach { r ->
                                val due = LocalDate.ofEpochDay(r.nextDueEpochDay)
                                val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), due)
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            when (days) {
                                                0L -> tr("Hoy", "Today")
                                                1L -> tr("Mañana", "Tomorrow")
                                                else -> tr("En $days días", "In $days days")
                                            } + " · " + due.format(com.midinero.control.domain.Lang.dateFormat("d 'de' MMMM", "d MMMM")),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(formatCents(r.amountCents), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                HintCard(vm, "hint_recurring", tr("Añade alquileres, suscripciones o tu nómina: se apuntan solos el día que toca.", "Add rent, subscriptions or your salary: they're logged automatically on the due date."), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            if (candidates.isNotEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("🔎 Parecen suscripciones", "🔎 Look like subscriptions"), fontWeight = FontWeight.SemiBold)
                            Text(
                                tr("Pagos que se repiten cada mes por un importe parecido. Añádelos para que se apunten solos.", "Payments that repeat every month for a similar amount. Add them so they're logged automatically."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            candidates.take(5).forEach { c ->
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(c.merchant, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            tr("${formatCents(c.amountCents)} el día ${c.dayOfMonth} · ${c.occurrences} pagos", "${formatCents(c.amountCents)} on day ${c.dayOfMonth} · ${c.occurrences} payments"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = { vm.ignoreSubscription(c) }) { Text(tr("No", "No")) }
                                    TextButton(onClick = {
                                        vm.addSubscription(c)
                                        scope.launch { snackbar.showSnackbar(tr("${c.merchant} añadido a tus cargos fijos", "${c.merchant} added to your recurring charges")) }
                                    }) { Text(tr("Añadir", "Add")) }
                                }
                            }
                        }
                    }
                }
            }

            if (recurring.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🔁",
                        title = tr("Todavía no tienes cargos fijos", "No recurring charges yet"),
                        text = tr("Añade el alquiler, Netflix, Spotify o la cuota del coche y se apuntarán solos el día que toca.", "Add your rent, Netflix, Spotify or car payment and they'll be logged automatically on the due date."),
                    )
                }
            }

            itemsIndexed(sorted, key = { _, it -> it.id }) { i, r ->
                Column(Modifier.grouped(i, sorted.size)) {
                    RecurringRow(
                        recurring = r,
                        category = r.categoryId?.let { categoryById[it] },
                        onClick = { editingId = r.id },
                        onToggle = { vm.setRecurringActive(r, it) },
                    )
                    GroupDivider(i, sorted.size)
                }
            }
        }

        AppFab(
            text = tr("Añadir", "Add"),
            action = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { r ->
        var history by remember(r.id) { mutableStateOf<RecurringHistory?>(null) }
        LaunchedEffect(r.id) { history = vm.recurringHistory(r.id) }
        RecurringDialog(
            recurring = r,
            categories = expenseCategories,
            incomeCategories = incomeCategories,
            onCreateCategory = vm::createCategory,
            history = history,
            onDismiss = { editingId = null },
            onSave = { vm.saveRecurring(it); editingId = null },
            onDelete = { vm.deleteRecurring(r); editingId = null },
            onSkip = {
                vm.skipNextCharge(r)
                editingId = null
                scope.launch { snackbar.showSnackbar(tr("Se ha saltado el cargo del ${LocalDate.ofEpochDay(r.nextDueEpochDay).shortLabel()}", "Skipped the charge on ${LocalDate.ofEpochDay(r.nextDueEpochDay).shortLabel()}")) }
            },
            onChargeNow = {
                vm.chargeNow(r)
                editingId = null
                scope.launch { snackbar.showSnackbar(tr("Apuntado ${r.name}: ${formatCents(r.amountCents)}", "Logged ${r.name}: ${formatCents(r.amountCents)}")) }
            },
        )
    }
    if (creating) {
        RecurringDialog(
            recurring = null,
            categories = expenseCategories,
            incomeCategories = incomeCategories,
            onCreateCategory = vm::createCategory,
            history = null,
            onDismiss = { creating = false },
            onSave = { vm.saveRecurring(it); creating = false },
            onDelete = null,
            onSkip = null,
            onChargeNow = null,
        )
    }
}

@Composable
private fun RecurringRow(
    recurring: RecurringExpense,
    category: Category?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val dim = if (recurring.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantBadge(recurring.name, category)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(recurring.name, fontWeight = FontWeight.Medium, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Recurrence.describe(recurring),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    recurring.active -> tr("Próximo: ${Recurrence.nextLabel(recurring)}", "Next: ${Recurrence.nextLabel(recurring)}") + if (recurring.remindDayBefore) " · 🔔" else ""
                    recurring.remainingCharges == 0 -> tr("Terminado", "Finished")
                    else -> tr("En pausa", "Paused")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (recurring.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (recurring.isIncome) "+" else "") + formatCents(recurring.amountCents),
                fontWeight = FontWeight.SemiBold,
                color = dim,
            )
            Text(
                Recurrence.periodLabel(recurring.frequency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = recurring.active,
            onCheckedChange = onToggle,
            enabled = recurring.remainingCharges != 0,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecurringDialog(
    recurring: RecurringExpense?,
    categories: List<Category>,
    incomeCategories: List<Category>,
    onCreateCategory: (Category, (Long?) -> Unit) -> Unit,
    history: RecurringHistory?,
    onDismiss: () -> Unit,
    onSave: (RecurringExpense) -> Unit,
    onDelete: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    onChargeNow: (() -> Unit)?,
) {
    var isIncome by rememberSaveable { mutableStateOf(recurring?.isIncome ?: false) }
    var name by rememberSaveable { mutableStateOf(recurring?.name ?: "") }
    var amountText by rememberSaveable { mutableStateOf(recurring?.let { centsToInput(it.amountCents) } ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(recurring?.categoryId) }
    var frequency by rememberSaveable { mutableStateOf(recurring?.frequency ?: Frequency.MONTHLY) }
    var date by rememberSaveable {
        mutableStateOf(recurring?.let { LocalDate.ofEpochDay(it.nextDueEpochDay) } ?: LocalDate.now())
    }
    var note by rememberSaveable { mutableStateOf(recurring?.note ?: "") }
    var limited by rememberSaveable { mutableStateOf(recurring?.remainingCharges != null) }
    var chargesText by rememberSaveable { mutableStateOf(recurring?.remainingCharges?.toString() ?: "") }
    var remind by rememberSaveable { mutableStateOf(recurring?.remindDayBefore ?: false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val amount = parseUserAmount(amountText)
    val charges = chargesText.toIntOrNull()?.takeIf { it in 1..999 }
    val canSave = amount != null && amount <= MAX_AMOUNT_CENTS && name.isNotBlank() && (!limited || charges != null)
    val today = LocalDate.now()
    // Al editar, cambiar la fecha a una pasada volvería a apuntar cargos: se avisa.
    val dateChanged = recurring != null && date.toEpochDay() != recurring.nextDueEpochDay

    EditorSheet(
        title = when {
                    recurring == null && isIncome -> tr("Nuevo ingreso fijo", "New recurring income")
                    recurring == null -> tr("Nuevo cargo fijo", "New recurring charge")
                    isIncome -> tr("Editar ingreso fijo", "Edit recurring income")
                    else -> tr("Editar cargo fijo", "Edit recurring charge")
                },
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = canSave,
        onPrimary = {
            val base = recurring ?: RecurringExpense(name = "", amountCents = 0, dayOfMonth = 1, nextDueEpochDay = 0)
            val remaining = if (limited) charges else null
            onSave(
                base.copy(
                    name = name.trim(),
                    amountCents = amount!!,
                    categoryId = categoryId,
                    frequency = frequency,
                    dayOfMonth = date.dayOfMonth,
                    nextDueEpochDay = date.toEpochDay(),
                    note = note.trim(),
                    remainingCharges = remaining,
                    remindDayBefore = remind,
                    isIncome = isIncome,
                    active = if (remaining != null && remaining > 0) true else base.active || recurring == null,
                )
            )
        },
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to tr("Gasto", "Expense"), true to tr("Ingreso", "Income")).forEachIndexed { i, (income, label) ->
                SegmentedButton(
                    selected = isIncome == income,
                    onClick = {
                        if (isIncome != income) {
                            isIncome = income
                            categoryId = null
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (recurring == null && !isIncome) {
            Text(tr("Atajos", "Shortcuts"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recurringTemplates.forEach { t ->
                    AssistChip(
                        onClick = {
                            name = t.name
                            categoryId = categories.firstOrNull { CategoryNames.matches(it.name, t.categoryName) }?.id
                        },
                        label = { Text("${t.emoji} ${t.name}") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (history != null && history.count > 0) {
            Text(
                tr("Apuntado", "Logged") + " ${history.count} ${if (history.count == 1) tr("vez", "time") else tr("veces", "times")} · ${formatCents(history.totalCents)} " + tr("en total", "in total"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text(if (isIncome) tr("Nombre (ej. Nómina)", "Name (e.g. Salary)") else tr("Nombre (ej. Alquiler cochera)", "Name (e.g. Parking rent)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) },
            label = { Text(tr("Importe (€)", "Amount (€)")) },
            singleLine = true,
            isError = amountText.isNotEmpty() && amount == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(tr("Frecuencia", "Frequency"), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            frequencyLabels.forEachIndexed { i, (f, label) ->
                SegmentedButton(
                    selected = frequency == f,
                    onClick = { frequency = f },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = frequencyLabels.size),
                    icon = {},
                ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (recurring == null) tr("Fecha del primer cargo", "Date of the first charge") else tr("Fecha del próximo cargo", "Date of the next charge"),
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DateRange, contentDescription = tr("Fecha", "Date"))
            Spacer(Modifier.width(8.dp))
            Text(date.shortLabel())
        }
        Text(
            when {
                date < today && (recurring == null || dateChanged) ->
                    tr("Esa fecha ya ha pasado: se apuntarán también los cargos desde ese día hasta hoy.", "That date has passed: charges from that day until today will be logged too.")
                else -> tr("Después se repetirá ", "Then it will repeat ") + when (frequency) {
                    Frequency.WEEKLY -> tr("cada semana.", "every week.")
                    Frequency.MONTHLY -> tr("el día ${date.dayOfMonth} de cada mes.", "on day ${date.dayOfMonth} of every month.")
                    Frequency.QUARTERLY -> tr("cada 3 meses, el día ${date.dayOfMonth}.", "every 3 months, on day ${date.dayOfMonth}.")
                    Frequency.YEARLY -> tr("cada año en la misma fecha.", "every year on the same date.")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Tiene fin (cuotas)", "Has an end (instalments)"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    tr("Para financiaciones o pagos a plazos", "For financing or payment plans"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = limited, onCheckedChange = { limited = it })
        }
        if (limited) {
            OutlinedTextField(
                value = chargesText,
                onValueChange = { chargesText = it.filter(Char::isDigit).take(3) },
                label = { Text(tr("Cargos que quedan", "Charges left")) },
                singleLine = true,
                isError = chargesText.isNotEmpty() && charges == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (amount != null && charges != null) {
                Text(
                    tr("Total pendiente: ${formatCents(amount * charges)}", "Total pending: ${formatCents(amount * charges)}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Avisarme el día antes", "Remind me the day before"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = remind, onCheckedChange = { remind = it })
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Categoría", "Category"), style = MaterialTheme.typography.labelLarge)
        if (categoryId == null && !isIncome) {
            Text(
                tr("Si no eliges ninguna, MiDinero la intentará adivinar por el nombre.", "If you don't pick one, MiDinero will try to guess it from the name."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        CategoryPicker(if (isIncome) incomeCategories else categories, categoryId, onCreate = onCreateCategory, isIncome = isIncome) { categoryId = it }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(300) },
            label = { Text(tr("Nota (opcional)", "Note (optional)")) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (recurring != null && recurring.active) {
            Spacer(Modifier.height(8.dp))
            Row {
                if (onChargeNow != null) TextButton(onClick = onChargeNow) { Text(tr("Apuntar ya", "Log now")) }
                if (onSkip != null) TextButton(onClick = onSkip) { Text(tr("Saltar el próximo", "Skip the next one")) }
            }
        }
        if (onDelete != null) {
            TextButton(onClick = { confirmDelete = true }) {
                Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPicked = { date = it; showDatePicker = false },
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar «${recurring?.name}»?", "Delete “${recurring?.name}”?")) },
            text = { Text(tr("Dejará de apuntarse. Los cargos ya apuntados se conservan en Movimientos.", "It will stop being logged. Charges already logged stay in Transactions.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}
