package com.midinero.control.ui.screens

import com.midinero.control.domain.signedAmount
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.ui.DebtsViewModel
import com.midinero.control.domain.Duplicates
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.foundation.shape.CircleShape
import com.midinero.control.ui.theme.MoneyStyle
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.itemsIndexed
import com.midinero.control.domain.tr
import androidx.compose.material3.TextButton
import com.midinero.control.domain.Currencies
import com.midinero.control.domain.amountsHidden
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import com.midinero.control.domain.Categorizer
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.dayLabel
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.shortLabel
import com.midinero.control.domain.timeLabel
import com.midinero.control.service.AppNotifications
import com.midinero.control.ui.LocalSnackbarHostState
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.showUndo
import kotlinx.coroutines.launch

private enum class ExpenseFilter(private val es: String, private val en: String) {
    ALL("Todos", "All"),
    UNCATEGORIZED("Sin categoría", "Uncategorised"),
    INCOME("Ingresos", "Income"),
    GOOGLE_PAY("Detectados", "Detected"),
    RECURRING("Recurrentes", "Recurring"),
    IMPORTED("Banco", "Bank"),
    MANUAL("Manuales", "Manual"),
    RECEIPT("Con ticket", "With receipt"),
    DUPLICATES("Posibles duplicados", "Possible duplicates");

    val label: String get() = tr(es, en)
}

/** Filtros que pueden llegar desde otras pantallas: "uncategorized" o "category:ID". */
private fun initialCategory(initialFilter: String?): Long? =
    if (initialFilter != null && initialFilter.startsWith("category:")) {
        initialFilter.removePrefix("category:").toLongOrNull()
    } else null

@Composable
fun ExpensesScreen(
    vm: MainViewModel,
    initialFilter: String?,
    openExpenseId: Long? = null,
    openNewExpense: Boolean = false,
    onOpenMerchant: (String) -> Unit = {},
    newExpenseCategory: Long? = null,
    onOpenSearch: () -> Unit = {},
    debtsVm: DebtsViewModel = viewModel(),
) {
    val month by vm.month.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val pickerCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var filter by rememberSaveable(initialFilter) {
        mutableStateOf(if (initialFilter == "uncategorized") ExpenseFilter.UNCATEGORIZED else ExpenseFilter.ALL)
    }
    var categoryFilter by rememberSaveable(initialFilter) { mutableStateOf(initialCategory(initialFilter)) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortByAmount by rememberSaveable { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var editingId by rememberSaveable(openExpenseId) { mutableStateOf(openExpenseId) }
    var creating by rememberSaveable(openNewExpense) { mutableStateOf(openNewExpense) }
    var sharingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> expenses.firstOrNull { it.id == id } }
    val context = LocalContext.current
    val haptics = rememberHaptics()
    // Selección múltiple (mantener pulsado un movimiento)
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()
    var bulkCategory by remember { mutableStateOf(false) }
    var bulkDelete by remember { mutableStateOf(false) }
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    // Posibles duplicados (mismo importe con pocos minutos de diferencia)
    val prefsVersion by vm.prefs.changes.collectAsStateWithLifecycle()
    val duplicates = remember(expenses, prefsVersion) { Duplicates.find(expenses, ignored = vm.prefs.notDuplicates) }
    LaunchedEffect(openExpenseId) {
        // Abierto desde el aviso de un pago: ese aviso ya no hace falta.
        openExpenseId?.let { AppNotifications.cancelPayment(context, it) }
    }

    fun delete(expense: Expense) {
        vm.deleteExpense(expense)
        scope.launch {
            snackbar.showUndo("${if (expense.isIncome) tr("Ingreso", "Income") else tr("Gasto", "Expense")} eliminado: ${expense.merchant}") { vm.restoreExpense(expense) }
        }
    }

    val normalizedQuery = Categorizer.normalize(query)
    // Filtrar y agrupar solo cuando cambian los datos o los filtros, no en cada recomposición.
    val visible = remember(expenses, filter, categoryFilter, normalizedQuery, duplicates) {
        expenses.filter {
            when (filter) {
                ExpenseFilter.ALL -> true
                ExpenseFilter.UNCATEGORIZED -> it.categoryId == null && !it.isIncome
                ExpenseFilter.INCOME -> it.isIncome
                ExpenseFilter.GOOGLE_PAY -> it.source == ExpenseSource.GOOGLE_PAY
                ExpenseFilter.RECURRING -> it.source == ExpenseSource.RECURRING
                ExpenseFilter.IMPORTED -> it.source == ExpenseSource.IMPORTED
                ExpenseFilter.MANUAL -> it.source == ExpenseSource.MANUAL
                ExpenseFilter.RECEIPT -> it.receipt != null
                ExpenseFilter.DUPLICATES -> it.id in duplicates
            }
        }.filter { categoryFilter == null || it.categoryId == categoryFilter }
            .filter { e ->
                normalizedQuery.isEmpty() ||
                    Categorizer.normalize(e.merchant).contains(normalizedQuery) ||
                    Categorizer.normalize(e.note).contains(normalizedQuery) ||
                    centsToInput(e.amountCents).startsWith(query.trim().replace('.', ','))
            }
    }
    val filtered = filter != ExpenseFilter.ALL || categoryFilter != null || normalizedQuery.isNotEmpty()
    val byDay = remember(visible) { visible.groupBy { dateOf(it.timestamp) } }
    val selectedCategory = categoryFilter?.let { categoryById[it] }

    Box(Modifier.fillMaxSize()) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        ScrollToTopOnReselect("expenses", listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                if (selecting) {
                    SelectionBar(
                        count = selected.size,
                        onClose = { selected = emptySet() },
                        onSelectAll = { selected = visible.map { it.id }.toSet() },
                        onCategory = { bulkCategory = true },
                        onDelete = { bulkDelete = true },
                    )
                } else ScreenHeader(
                    tr("Actividad", "Activity"),
                    modifier = Modifier.padding(start = 12.dp, end = 16.dp),
                ) {
                    RoundAction(
                        Icons.AutoMirrored.Filled.Sort,
                        if (sortByAmount) tr("Ordenar por fecha", "Sort by date") else tr("Ordenar por importe", "Sort by amount"),
                        { sortByAmount = !sortByAmount },
                        tint = if (sortByAmount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    RoundAction(
                        if (searching) Icons.Filled.Close else Icons.Filled.Search,
                        tr("Buscar", "Search"),
                        {
                            searching = !searching
                            if (!searching) query = ""
                        },
                    )
                }
                androidx.compose.animation.AnimatedVisibility(visible = searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(tr("Buscar comercio, nota o importe", "Search merchant, note or amount")) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                MonthSelector(month, vm::previousMonth, vm::nextMonth)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStat(
                        tr("Gastos", "Spent"), expenses.filter { !it.isIncome }.sumOf { it.amountCents },
                        com.midinero.control.ui.theme.BudgetColors.over, Icons.Filled.ArrowUpward, Modifier.weight(1f),
                    )
                    MiniStat(
                        tr("Ingresos", "Income"), expenses.filter { it.isIncome }.sumOf { it.amountCents },
                        com.midinero.control.ui.theme.BudgetColors.ok, Icons.Filled.ArrowDownward, Modifier.weight(1f),
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Box {
                            FilterChip(
                                shape = CircleShape,
                                colors = pillChipColors(),
                                border = null,
                                selected = selectedCategory != null,
                                onClick = { categoryMenu = true },
                                label = {
                                    Text(selectedCategory?.let { "${it.emoji} ${it.name}" } ?: tr("Categoría", "Category"))
                                },
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                            )
                            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(tr("Todas las categorías", "All categories")) },
                                    onClick = { categoryFilter = null; categoryMenu = false },
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text("${cat.emoji} ${cat.name}") },
                                        onClick = { categoryFilter = cat.id; categoryMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    items(ExpenseFilter.entries.filter { f ->
                        when (f) {
                            ExpenseFilter.RECEIPT -> filter == f || expenses.any { it.receipt != null }
                            ExpenseFilter.DUPLICATES -> filter == f || duplicates.isNotEmpty()
                            else -> true
                        }
                    }) { f ->
                        val count = when (f) {
                            ExpenseFilter.UNCATEGORIZED -> expenses.count { it.categoryId == null && !it.isIncome }
                            ExpenseFilter.DUPLICATES -> duplicates.size
                            else -> null
                        }
                        FilterChip(
                            shape = CircleShape,
                            colors = pillChipColors(),
                            border = null,
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(if (count != null && count > 0) "${f.label} ($count)" else f.label) },
                        )
                    }
                }
                if (filtered && visible.isNotEmpty()) {
                    Text(
                        "${visible.size} ${if (visible.size == 1) tr("movimiento", "transaction") else tr("movimientos", "transactions")} · " +
                            signedTotals(visible),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
            }

            if (duplicates.isNotEmpty() && filter != ExpenseFilter.DUPLICATES && !selecting) {
                item {
                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Filled.ContentCopy, com.midinero.control.ui.theme.BudgetColors.warning, size = 34.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (duplicates.size == 2) tr("2 movimientos parecen el mismo pago", "2 transactions look like the same payment")
                                    else tr("${duplicates.size} movimientos parecen pagos repetidos", "${duplicates.size} transactions look like repeated payments"),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                tr("Mismo importe con pocos minutos de diferencia: a veces Google Wallet y el banco avisan del mismo pago.", "Same amount a few minutes apart: sometimes Google Wallet and your bank both report the same payment."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.Button(onClick = { filter = ExpenseFilter.DUPLICATES }) { Text(tr("Revisar", "Review")) }
                                androidx.compose.material3.OutlinedButton(onClick = { vm.markNotDuplicates(duplicates) }) { Text(tr("No lo son", "They're not")) }
                            }
                        }
                    }
                }
            }

            if (visible.isNotEmpty()) {
                item {
                    HintCard(vm, "hint_expenses", tr("Toca un movimiento para editarlo; desliza a la izquierda para borrarlo.", "Tap a transaction to edit it; swipe left to delete it."), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            if (visible.isEmpty()) {
                item {
                    when {
                        normalizedQuery.isNotEmpty() -> EmptyState(
                            "🔎",
                            tr("Nada coincide con «${query.trim()}» este mes.", "Nothing matches “${query.trim()}” this month."),
                            tr("Prueba con otra palabra o busca en todos los meses.", "Try another word or search all months."),
                        )
                        filter == ExpenseFilter.UNCATEGORIZED -> EmptyState(
                            "👌",
                            tr("Todo categorizado", "Everything categorised"),
                            tr("No te queda ningún gasto sin categoría este mes.", "You have no uncategorised expenses this month."),
                        )
                        filtered -> EmptyState(
                            "🧐",
                            tr("No hay movimientos con estos filtros.", "No transactions with these filters."),
                            tr("Quita algún filtro para ver más.", "Remove a filter to see more."),
                        )
                        else -> EmptyState(
                            "🌱",
                            tr("Aún no hay movimientos este mes", "No transactions this month yet"),
                            tr("Añade uno con el botón o paga con Google Pay y aparecerá aquí solo.", "Add one with the button or pay with Google Pay and it will show up here by itself."),
                            action = tr("Añadir gasto", "Add expense"),
                            onAction = { creating = true },
                        )
                    }
                    if (normalizedQuery.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                vm.setSearch(query)
                                onOpenSearch()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(tr("Buscar en todos los meses", "Search all months")) }
                    }
                }
            }

            if (sortByAmount) {
                val sorted = visible.sortedByDescending { it.amountCents }
                itemsIndexed(sorted, key = { _, e -> e.id }) { i, expense ->
                    SwipeableExpenseRow(
                        first = i == 0,
                        last = i == sorted.lastIndex,
                        expense = expense,
                        category = expense.categoryId?.let { categoryById[it] },
                        showDate = true,
                        onClick = { if (selecting) toggle(expense.id) else editingId = expense.id },
                        onLongClick = { haptics.longPress(); toggle(expense.id) },
                        selected = expense.id in selected,
                        duplicate = expense.id in duplicates,
                        onDelete = { delete(expense) },
                    )
                }
            } else {
                byDay.forEach { (day, list) ->
                    item(key = "h$day", contentType = "day") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        ) {
                            Text(
                                day.dayLabel(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                signedTotals(list),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(list, key = { _, e -> e.id }, contentType = { _, _ -> "expense" }) { i, expense ->
                        SwipeableExpenseRow(
                            expense = expense,
                            category = expense.categoryId?.let { categoryById[it] },
                            showDate = false,
                            onClick = { if (selecting) toggle(expense.id) else editingId = expense.id },
                            onLongClick = { haptics.longPress(); toggle(expense.id) },
                            selected = expense.id in selected,
                            duplicate = expense.id in duplicates,
                            onDelete = { delete(expense) },
                            first = i == 0,
                            last = i == list.lastIndex,
                        )
                    }
                }
            }
        }

    }

    if (bulkCategory) {
        var chosen by remember { mutableStateOf<Long?>(null) }
        EditorSheet(
            title = if (selected.size == 1) tr("Categoría de 1 movimiento", "Category for 1 transaction") else tr("Categoría de ${selected.size} movimientos", "Category for ${selected.size} transactions"),
            onDismiss = { bulkCategory = false },
            primaryText = tr("Aplicar", "Apply"),
            primaryEnabled = chosen != null,
            onPrimary = {
                chosen?.let { vm.setCategory(selected.toList(), it) }
                haptics.confirm()
                selected = emptySet()
            },
        ) {
            CategoryPicker(pickerCategories, chosen, onCreate = vm::createCategory) { chosen = it }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (bulkDelete) {
        val toDelete = expenses.filter { it.id in selected }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { bulkDelete = false },
            title = { Text(if (toDelete.size == 1) tr("¿Borrar 1 movimiento?", "Delete 1 transaction?") else tr("¿Borrar ${toDelete.size} movimientos?", "Delete ${toDelete.size} transactions?")) },
            text = { Text(tr("Suman ${formatCents(toDelete.sumOf { it.amountCents })}. Podrás deshacerlo justo después.", "They add up to ${formatCents(toDelete.sumOf { it.amountCents })}. You can undo it right after.")) },
            confirmButton = {
                TextButton(onClick = {
                    bulkDelete = false
                    selected = emptySet()
                    haptics.reject()
                    vm.deleteExpenses(toDelete)
                    scope.launch {
                        snackbar.showUndo(tr("${toDelete.size} movimientos eliminados", "${toDelete.size} transactions deleted")) { vm.restoreExpenses(toDelete) }
                    }
                }) { Text(tr("Borrar", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { bulkDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
    androidx.activity.compose.BackHandler(enabled = selecting) { selected = emptySet() }

    editing?.let { old ->
        ExpenseEditorDialog(
            expense = old,
            categories = pickerCategories,
            incomeCategories = incomeCategories,
            merchantSuggestions = merchants,
            onDismiss = { editingId = null },
            rateFor = vm.prefs::rateFor,
            onCreateCategory = vm::createCategory,
            trips = trips,
            onSave = { d ->
                d.rate?.let { r -> d.originalCurrency?.let { vm.prefs.setRate(it, r) } }
                vm.updateExpense(
                    old,
                    old.copy(
                        amountCents = d.amountCents, merchant = d.merchant, note = d.note, categoryId = d.categoryId,
                        timestamp = d.timestamp, isIncome = d.isIncome,
                        originalCurrency = d.originalCurrency, originalAmountCents = d.originalAmountCents,
                        tripId = d.tripId,
                        receipt = d.receipt, accountId = d.accountId,
                    ),
                    d.learn,
                )
                editingId = null
            },
            onDelete = {
                editingId = null
                delete(old)
            },
            onShare = {
                editingId = null
                sharingId = old.id
            },
            onOpenHistory = {
                editingId = null
                onOpenMerchant(Categorizer.merchantKey(old.merchant))
            },
            onDuplicate = {
                editingId = null
                vm.duplicateExpense(old)
                scope.launch { snackbar.showSnackbar(tr("Gasto duplicado con fecha de hoy", "Expense duplicated with today's date")) }
            },
            onRefund = { cents ->
                editingId = null
                vm.addRefund(old, cents)
                scope.launch { snackbar.showSnackbar(tr("Devolución de ${formatCents(cents)} apuntada", "Refund of ${formatCents(cents)} logged")) }
            },
        )
    }

    sharingId?.let { id ->
        expenses.firstOrNull { it.id == id }?.let { e ->
            ShareExpenseDialog(
                totalCents = e.amountCents,
                onDismiss = { sharingId = null },
                onShare = { people ->
                    debtsVm.shareExpense(e, people)
                    sharingId = null
                    scope.launch { snackbar.showSnackbar(tr("Compartido: queda tu parte y lo que te deben está en Cuentas con amigos", "Shared: your part stays here and what you're owed is in Split with friends")) }
                },
            )
        }
    }

    if (creating) {
        ExpenseEditorDialog(
            expense = null,
            categories = pickerCategories,
            incomeCategories = incomeCategories,
            merchantSuggestions = merchants,
            onDismiss = { creating = false },
            rateFor = vm.prefs::rateFor,
            onCreateCategory = vm::createCategory,
            trips = trips,
            onSave = { d ->
                vm.addExpense(d)
                creating = false
            },
            defaultDate = defaultDateFor(month),
            initialCategoryId = newExpenseCategory,
        )
    }
}

/** "-45,00 €" o "-45,00 € · +1.200,00 €" si hay ingresos. */
private fun signedTotals(list: List<Expense>): String {
    val spent = list.filter { !it.isIncome }.sumOf { it.amountCents }
    val income = list.filter { it.isIncome }.sumOf { it.amountCents }
    return listOfNotNull(
        if (spent > 0 || income == 0L) "-${formatCents(spent)}" else null,
        if (income > 0) "+${formatCents(income)}" else null,
    ).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableExpenseRow(
    expense: Expense,
    category: Category?,
    showDate: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    first: Boolean = true,
    last: Boolean = true,
    onLongClick: () -> Unit = {},
    selected: Boolean = false,
    duplicate: Boolean = false,
) {
    // Las filas de un mismo día forman una tarjeta blanca: la primera y la última llevan las esquinas redondeadas.
    val r = 24.dp
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        modifier = Modifier.padding(horizontal = 16.dp).clip(shape),
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val active = state.targetValue == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (active) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = tr("Eliminar", "Delete"), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Column(
            Modifier
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                // Quien usa TalkBack no puede deslizar: se ofrece tr("Eliminar", "Delete") como acción.
                .semantics { customActions = listOf(CustomAccessibilityAction(tr("Eliminar", "Delete")) { onDelete(); true }) }
        ) {
            ExpenseRow(expense, category, showDate, onClick, onLongClick, selected, duplicate)
            if (!last) HorizontalDivider(Modifier.padding(start = 74.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRow(
    expense: Expense,
    category: Category?,
    showDate: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selected: Boolean = false,
    duplicate: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = tr("Seleccionar", "Select"),
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }
        } else MerchantBadge(expense.merchant, category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (expense.receipt != null) "📎 " else "") + expense.merchant,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category?.name ?: if (expense.isIncome) tr("Ingreso", "Income") else tr("Sin categoría · toca para asignar", "Uncategorised · tap to assign"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (category == null && !expense.isIncome) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " · " + if (showDate) dateOf(expense.timestamp).shortLabel() else timeLabel(expense.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                val badge = when (expense.source) {
                    ExpenseSource.GOOGLE_PAY -> "G Pay"
                    ExpenseSource.RECURRING -> tr("Fijo", "Recurring")
                    ExpenseSource.IMPORTED -> tr("Banco", "Bank")
                    ExpenseSource.MANUAL -> null
                }
                if (duplicate) {
                    Spacer(Modifier.width(6.dp))
                    Pill(tr("¿Repetido?", "Repeated?"), com.midinero.control.ui.theme.BudgetColors.warning)
                } else if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill(badge, if (expense.source == ExpenseSource.RECURRING) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                }
            }
            if (expense.note.isNotBlank()) {
                Text(
                    expense.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signedAmount(expense),
                style = MaterialTheme.typography.titleSmall.merge(MoneyStyle),
                color = if (expense.isIncome || expense.isRefund) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            val code = expense.originalCurrency
            val orig = expense.originalAmountCents
            if (code != null && orig != null && !amountsHidden()) {
                Text(
                    Currencies.format(orig, code),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Barra que sustituye a la cabecera mientras hay movimientos seleccionados. */
@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onCategory: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundAction(Icons.Filled.Close, tr("Cancelar la selección", "Cancel selection"), onClose)
        Spacer(Modifier.width(10.dp))
        Text(
            if (count == 1) tr("1 seleccionado", "1 selected") else tr("$count seleccionados", "$count selected"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        RoundAction(Icons.Filled.SelectAll, tr("Seleccionar todos", "Select all"), onSelectAll)
        Spacer(Modifier.width(6.dp))
        RoundAction(Icons.Filled.Category, tr("Cambiar categoría", "Change category"), onCategory)
        Spacer(Modifier.width(6.dp))
        RoundAction(Icons.Filled.Delete, tr("Borrar", "Delete"), onDelete, tint = MaterialTheme.colorScheme.error)
    }
}
