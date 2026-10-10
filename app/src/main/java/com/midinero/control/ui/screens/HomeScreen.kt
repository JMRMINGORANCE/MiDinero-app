package com.midinero.control.ui.screens

import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.ui.GoalsViewModel
import com.midinero.control.ui.DebtsViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Repeat
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.Lang
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import com.midinero.control.domain.tr
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.midinero.control.domain.Budgets
import com.midinero.control.domain.Split
import com.midinero.control.domain.Tip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.midinero.control.ui.PrivacyState
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.service.PaymentNotificationListener
import com.midinero.control.ui.MainViewModel

data class BudgetStatus(val category: Category, val spentCents: Long, val limitCents: Long) {
    val ratio: Float get() = if (limitCents <= 0) 0f else spentCents.toFloat() / limitCents
    val percent: Int get() = (ratio * 100).toInt()
    val remainingCents: Long get() = limitCents - spentCents
}

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenUncategorized: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onOpenLimits: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenDebts: () -> Unit = {},
    onOpenTrips: () -> Unit = {},
    onOpenTrip: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenCategoryMovements: (Long) -> Unit = onOpenCategory,
    onAdd: () -> Unit = {},
    onOpenMovements: () -> Unit = {},
    onOpenExpense: (Long) -> Unit = {},
    goalsVm: GoalsViewModel = viewModel(),
    debtsVm: DebtsViewModel = viewModel(),
) {
    val month by vm.month.collectAsStateWithLifecycle()
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val tripTotals by vm.tripTotals.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val goals by goalsVm.goals.collectAsStateWithLifecycle()
    val tips by vm.tips.collectAsStateWithLifecycle()
    val debts by debtsVm.debts.collectAsStateWithLifecycle()
    val debtBalances = remember(debts) { Split.balances(debts) }
    val prefsVersion by vm.prefs.changes.collectAsStateWithLifecycle()
    val rollover by vm.rollover.collectAsStateWithLifecycle()
    val baseBudget = remember(prefsVersion) { vm.prefs.monthlyBudgetCents }
    // Con «arrastrar lo que sobra», los límites de este mes incluyen lo que no se gastó el anterior
    val monthlyBudget = if (baseBudget > 0) baseBudget + (rollover[null] ?: 0L) else 0L
    val lastBackup = remember(prefsVersion) { vm.prefs.lastBackupAt }
    val demoLoaded = remember(prefsVersion) { vm.prefs.demoIds.isNotEmpty() }
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    var detectionOn by remember { mutableStateOf(PaymentNotificationListener.isEnabled(context)) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showShare by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        detectionOn = PaymentNotificationListener.isEnabled(context)
    }

    val totalByCategory = remember(totals) { totals.associate { it.categoryId to it.totalCents } }
    val monthTotal = remember(totals) { totals.sumOf { it.totalCents } }
    val uncategorized = remember(expenses) { expenses.count { it.categoryId == null && !it.isIncome } }
    val budgets = remember(categories, totalByCategory, rollover) {
        categories
            .filter { (it.monthlyLimitCents ?: 0L) > 0 }
            .map { BudgetStatus(it, totalByCategory[it.id] ?: 0L, it.monthlyLimitCents!! + (rollover[it.id] ?: 0L)) }
            .sortedByDescending { it.ratio }
    }
    // Vibra si, con la app abierta, una categoría pasa de su límite (por ejemplo, al apuntar un gasto)
    val haptics = rememberHaptics()
    val overLimit = budgets.count { it.ratio > 1f }
    var lastOverLimit by remember { mutableStateOf(overLimit) }
    LaunchedEffect(overLimit) {
        if (overLimit > lastOverLimit) haptics.reject()
        lastOverLimit = overLimit
    }
    // Tarjetas de categorías: primero las que tienen gasto (de más a menos) y luego las que tienen
    // límite pero aún no tienen gasto. Los gastos sin categoría van en su propia tarjeta.
    val spends: List<CategorySpend> = remember(categories, totalByCategory, rollover) {
        buildList {
            categories.forEach { c ->
                val spent = totalByCategory[c.id] ?: 0L
                if (spent > 0 || (c.monthlyLimitCents ?: 0L) > 0) add(CategorySpend(c, spent, c.monthlyLimitCents?.let { it + (rollover[c.id] ?: 0L) }))
            }
            val none = totalByCategory[null] ?: 0L
            if (none > 0) add(CategorySpend(null, none, null))
        }.sortedWith(compareByDescending<CategorySpend> { it.spentCents }.thenByDescending { it.limitCents ?: 0L })
    }

    val entrance = rememberEntrance()
    val recent = remember(expenses) { expenses.sortedByDescending { it.timestamp }.take(5) }
    val categoryById = remember(categories, incomeCategories) { (categories + incomeCategories).associateBy { it.id } }

    Box(Modifier.fillMaxSize()) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        ScrollToTopOnReselect("home", listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            LocalDate.now().format(Lang.dateFormat("EEEE, d 'de' MMMM", "EEEE, d MMMM")).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(greeting(), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.asHeading())
                    }
                    RoundAction(Icons.Filled.Search, tr("Buscar en todos los meses", "Search all months"), onOpenSearch)
                    Spacer(Modifier.width(8.dp))
                    RoundAction(
                        if (PrivacyState.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (PrivacyState.hidden) tr("Mostrar importes", "Show amounts") else tr("Ocultar importes", "Hide amounts"),
                        { vm.prefs.hideAmounts = !PrivacyState.hidden },
                    )
                    Spacer(Modifier.width(8.dp))
                    RoundAction(Icons.Filled.Settings, tr("Ajustes", "Settings"), onOpenSettings)
                }
            }

            item {
                HomeHero(
                    month = month,
                    total = monthTotal,
                    stats = stats?.takeIf { it.month == month },
                    count = expenses.count { !it.isIncome },
                    expenses = expenses,
                    budget = monthlyBudget,
                    onPrevious = vm::previousMonth,
                    onNext = vm::nextMonth,
                    onShare = { showShare = true },
                    modifier = Modifier.entrance(entrance, 0),
                )
            }

            item { QuickEntryBar(vm, Modifier.entrance(entrance, 1)) }

            item {
                QuickActions(
                    modifier = Modifier.entrance(entrance, 2),
                    actions = listOf(
                        QuickAction(Icons.Filled.Repeat, SectionColors.recurring, tr("Fijos", "Recurring"), onOpenRecurring),
                        QuickAction(Icons.Filled.Speed, SectionColors.limits, tr("Límites", "Limits"), onOpenLimits),
                        QuickAction(Icons.Filled.EmojiEvents, SectionColors.goals, tr("Metas", "Goals"), onOpenGoals),
                        QuickAction(Icons.Filled.FlightTakeoff, SectionColors.trips, tr("Viajes", "Trips"), onOpenTrips),
                        QuickAction(Icons.Filled.Groups, SectionColors.debts, tr("Amigos", "Friends"), onOpenDebts),
                    ),
                )
            }

            if (demoLoaded) {
                item {
                    InfoCard(
                        title = tr("Estás viendo datos de ejemplo", "You're viewing sample data"),
                        text = tr("Explora la app con ellos. Cuando quieras empezar con tus datos, bórralos: lo que hayas apuntado tú se queda.", "Explore the app with it. When you want to start with your own data, delete it: anything you logged yourself stays."),
                        button = tr("Borrar ejemplos", "Delete samples"),
                        onClick = { vm.clearDemo() },
                    )
                }
            }

            if (expenses.isEmpty() && recurring.isEmpty()) {
                item {
                    InfoCard(
                        title = tr("Empieza aquí 👋", "Start here 👋"),
                        text = tr("Apunta tu primer gasto, o añade tus cargos fijos (alquiler, suscripciones, nómina…) ", "Log your first expense, or add your recurring charges (rent, subscriptions, salary…) ") +
                            tr("para ver desde hoy cuánto te queda cada mes.", "to see from today how much you have left each month."),
                        button = tr("Añadir gasto", "Add expense"),
                        onClick = { showEditor = true },
                        secondary = tr("Cargos fijos", "Recurring") to onOpenRecurring,
                    )
                }
            }

            // Recordatorio de copia de seguridad si hay datos y hace más de un mes de la última
            if (merchants.sumOf { it.uses } >= 20 && System.currentTimeMillis() - lastBackup > 30L * 24 * 3600 * 1000) {
                item {
                    TipCard(
                        Tip("💾", if (lastBackup == 0L) tr("Aún no has guardado ninguna copia de seguridad. Actívala en Ajustes → Tus datos.", "You haven't saved a backup yet. Turn it on in Settings → Your data.")
                        else tr("Hace más de un mes de tu última copia de seguridad. Guárdala en Ajustes → Tus datos.", "Your last backup is more than a month old. Save one in Settings → Your data.")),
                        onClick = onOpenSettings,
                    )
                }
            }

            tips.take(2).forEach { tip ->
                item {
                    TipCard(tip, onClick = tip.categoryId?.let { id -> { onOpenCategory(id) } })
                }
            }

            if (monthlyBudget > 0) {
                item {
                    GlobalBudgetCard(Budgets.pace(month, monthTotal, monthlyBudget), onClick = onOpenLimits)
                }
            }

            if (!detectionOn) {
                item {
                    InfoCard(
                        title = tr("Activa la detección de Google Pay", "Turn on Google Pay detection"),
                        text = tr("Dale a MiDinero acceso a las notificaciones para registrar automáticamente lo que pagas con el móvil.", "Give MiDinero notification access to log what you pay with your phone automatically."),
                        button = tr("Activar", "Turn on"),
                        onClick = {
                            // Algunos móviles (Android Go, perfiles de trabajo) no traen esta pantalla
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure {
                                android.widget.Toast.makeText(context, tr("Abre Ajustes → Notificaciones → Acceso a notificaciones", "Open Settings → Notifications → Notification access"), android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        secondary = tr("Más info", "Learn more") to onOpenSettings,
                    )
                }
            }

            if (uncategorized > 0) {
                item {
                    AppCard(
                        onClick = onOpenUncategorized,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (uncategorized == 1) tr("1 gasto sin categoría", "1 uncategorised expense") else tr("$uncategorized gastos sin categoría", "$uncategorized uncategorised expenses"),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(tr("Toca para asignarles una", "Tap to assign one"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    modifier = Modifier.entrance(entrance, 3),
                    title = tr("Gasto por categoría", "Spending by category"),
                    action = if (budgets.isNotEmpty()) tr("Límites", "Limits") else tr("Poner límites", "Set limits"),
                    onAction = onOpenLimits,
                )
            }

            if (spends.isEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            emoji = "🌱",
                            title = if (month == currentPeriod()) tr("Empieza tu mes", "Start your month") else tr("Sin gastos este mes", "No spending this month"),
                            text = if (month == currentPeriod()) {
                                tr(
                                    "Añade tu primer gasto o paga con Google Pay y aquí verás en qué se va tu dinero.",
                                    "Add your first expense or pay with Google Pay and you'll see here where your money goes.",
                                )
                            } else {
                                tr("No hay gastos apuntados en este mes.", "No expenses were logged this month.")
                            },
                        )
                    }
                }
            } else {
                // Cuadrícula de 2 columnas dentro de la lista, de más a menos gasto
                val rows = spends.chunked(2)
                items(rows.size, key = { "cats_$it" }) { index ->
                    val row = rows[index]
                    Row(Modifier.fillMaxWidth().entrance(entrance, 4 + index), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { spend ->
                            CategorySpendCard(
                                spend = spend,
                                monthTotal = monthTotal,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val id = spend.category?.id
                                    if (id == null) onOpenUncategorized() else onOpenCategoryMovements(id)
                                },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = tr("Últimos movimientos", "Latest activity"),
                        action = tr("Ver todos", "See all"),
                        onAction = onOpenMovements,
                    )
                }
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            recent.forEachIndexed { i, e ->
                                if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                RecentRow(e, e.categoryId?.let { categoryById[it] }, onClick = { onOpenExpense(e.id) })
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = tr("Tu plan", "Your plan")) }

            val activeRecurring = recurring.filter { it.active && !it.isIncome }
            item { RecurringPlanCard(activeRecurring, categoryById, onOpenRecurring) }

            item { GoalsPlanCard(goals, onOpenGoals) }

            item { TripsPlanCard(trips, tripTotals, onOpenTrips, onOpenTrip) }

            if (debtBalances.isNotEmpty()) {
                item { DebtsPlanCard(debtBalances, onOpenDebts) }
            }

        }

    }

    if (showShare) {
        ShareMonthSheet(
            onDismiss = { showShare = false },
            onImage = {
                showShare = false
                shareScope.launch {
                    val data = vm.monthReportData() ?: return@launch
                    val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        com.midinero.control.ui.ReportImage.render(context, data)
                    }
                    runCatching { com.midinero.control.ui.ReportImage.share(context, bitmap) }
                }
            },
            onText = {
                showShare = false
                shareScope.launch {
                    val text = vm.monthReport()
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, text)
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, tr("Compartir resumen del mes", "Share month summary"))) }
                }
            },
        )
    }

    if (showEditor) {
        ExpenseEditorDialog(
            expense = null,
            categories = categories,
            merchantSuggestions = merchants,
            onDismiss = { showEditor = false },
            rateFor = vm.prefs::rateFor,
            onCreateCategory = vm::createCategory,
            trips = trips,
            onSave = { d ->
                vm.addExpense(d)
                showEditor = false
            },
            incomeCategories = incomeCategories,
            defaultDate = defaultDateFor(month),
        )
    }
}
