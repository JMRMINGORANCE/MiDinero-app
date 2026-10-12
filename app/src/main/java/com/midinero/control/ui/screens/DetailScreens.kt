package com.midinero.control.ui.screens

import com.midinero.control.domain.MerchantCatalog
import com.midinero.control.domain.KnownMerchant
import com.midinero.control.domain.tr
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.domain.Categorizer
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.MainViewModel
import kotlin.math.abs

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit, logo: KnownMerchant? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver", "Back")) }
        Spacer(Modifier.width(10.dp))
        if (logo != null) {
            MerchantLogo(logo, size = 36)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).asHeading(),
        )
        actions()
    }
}

@Composable
private fun ExpenseLine(expense: Expense, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(expense.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatCents(expense.amountCents), fontWeight = FontWeight.SemiBold)
    }
}

/** Detalle de una categoría: evolución, media, comercios y gastos del mes. */
@Composable
fun CategoryDetailScreen(
    vm: MainViewModel,
    categoryId: Long,
    onBack: () -> Unit,
    onOpenMerchant: (String) -> Unit,
    onOpenMovements: (Long) -> Unit,
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val suggestions by vm.limitSuggestions.collectAsStateWithLifecycle()
    val flow = remember(categoryId) { vm.categoryInsight(categoryId) }
    val insight by flow.collectAsState(initial = null)
    val category = categories.firstOrNull { it.id == categoryId }
    var editing by rememberSaveable { mutableStateOf(false) }
    val snackbar = com.midinero.control.ui.LocalSnackbarHostState.current


    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DetailHeader(category?.let { "${it.emoji} ${it.name}" } ?: tr("Categoría", "Category"), onBack) {
                if (category != null) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = tr("Editar categoría", "Edit category")) }
                }
            }
            MonthSelector(month, vm::previousMonth, vm::nextMonth)
        }
        val c = insight ?: return@LazyColumn
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val change = if (c.previousCents > 0) ((c.totalCents - c.previousCents) * 100 / c.previousCents).toInt() else null
                StatTile(
                    tr("Este mes", "This month"),
                    formatCents(c.totalCents),
                    change?.let { if (it >= 0) tr("▲ $it % vs mes anterior", "▲ $it % vs previous month") else tr("▼ ${abs(it)} % vs mes anterior", "▼ ${abs(it)} % vs previous month") },
                    Modifier.weight(1f),
                )
                StatTile(
                    tr("Sueles gastar", "You usually spend"),
                    formatCents(c.averageCents),
                    category?.monthlyLimitCents?.let { tr("Límite: ${formatCents(it)}", "Limit: ${formatCents(it)}") } ?: tr("al mes", "a month"),
                    Modifier.weight(1f),
                )
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Evolución", "Trend"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        values = c.trend.map { it.second },
                        labels = c.trend.map { it.first.shortName() },
                        selected = c.trend.indexOfFirst { it.first == month },
                        valueLabel = { formatCents(it) },
                        onSelect = { vm.setMonth(c.trend[it].first) },
                    )
                }
            }
        }
        if (c.topMerchants.isNotEmpty()) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("Dónde", "Where"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val top = c.topMerchants.first().second.coerceAtLeast(1)
                        c.topMerchants.forEach { (name, cents) ->
                            Column(Modifier.fillMaxWidth().clickable { onOpenMerchant(Categorizer.merchantKey(name)) }.padding(top = 10.dp)) {
                                Row {
                                    Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatCents(cents), fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(4.dp))
                                RankBar(cents.toFloat() / top, MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Gastos del mes (${c.monthExpenses.size})", "This month's expenses (${c.monthExpenses.size})"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onOpenMovements(categoryId) }) { Text(tr("Ver en Movimientos", "View in Transactions")) }
            }
        }
        items(c.monthExpenses.take(50), key = { it.id }) { e ->
            ExpenseLine(e, dateOf(e.timestamp).shortLabel()) { onOpenMerchant(Categorizer.merchantKey(e.merchant)) }
        }
    }

    if (editing && category != null) {
        CategoryDialog(
            category = category,
            onDismiss = { editing = false },
            onSave = { vm.saveCategory(it) { ok -> if (ok) editing = false } },
            onDelete = { to -> vm.deleteCategory(category, to, snackbar); editing = false; onBack() },
            suggestionCents = suggestions[category.id],
            moveTargets = categories,
        )
    }
}

/** Historial de un comercio: cuánto, cuántas veces, ticket medio y categoría. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MerchantDetailScreen(
    vm: MainViewModel,
    merchantKey: String,
    onBack: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onCreateRule: (String) -> Unit = {},
) {
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    val flow = remember(merchantKey) { vm.merchantInsight(merchantKey) }
    val insight by flow.collectAsState(initial = null)
    var changing by rememberSaveable { mutableStateOf(false) }
    val m = insight
    val category: Category? = m?.categoryId?.let { id -> categories.firstOrNull { it.id == id } }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val logo = remember(m?.displayName) { m?.displayName?.let { MerchantCatalog.find(it) } }
            DetailHeader(m?.displayName ?: tr("Comercio", "Merchant"), onBack, logo) {
                if (m != null && m.count > 0) TextButton(onClick = { onCreateRule(m.displayName) }) { Text(tr("Crear regla", "Create rule")) }
            }
        }
        if (m == null) return@LazyColumn
        if (m.count == 0) {
            item { Text(tr("No hay gastos de este comercio.", "No expenses at this merchant."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(tr("En total", "In total"), formatCents(m.totalCents), "${m.count} ${if (m.count == 1) "vez" else "veces"}", Modifier.weight(1f))
                StatTile(tr("Ticket medio", "Average ticket"), formatCents(m.averageTicketCents), m.first?.let { tr("Desde ${it.shortLabel()}", "Since ${it.shortLabel()}") }, Modifier.weight(1f))
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable(enabled = category != null) { category?.let { onOpenCategory(it.id) } }) {
                        Text(tr("Categoría", "Category"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(category?.name ?: tr("Sin categoría", "Uncategorised"), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { changing = true }) { Text(tr("Cambiar", "Change")) }
                }
            }
        }
        item {
            val mine by vm.customMerchants.collectAsStateWithLifecycle()
            val custom = remember(m.displayName, mine) {
                mine.firstOrNull { it.name.equals(m.displayName, ignoreCase = true) } ?: com.midinero.control.domain.CustomMerchants.find(mine, m.displayName)
            }
            var editingMerchant by rememberSaveable { mutableStateOf(false) }
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    MerchantBadge(m.displayName, category)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Foto del comercio", "Merchant photo"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (custom?.photo != null) tr("La tuya", "Yours") else tr("Pon una foto de tu móvil", "Use a photo from your phone"),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedButton(onClick = { editingMerchant = true }) { Text(if (custom?.photo != null) tr("Cambiar", "Change") else tr("Elegir", "Choose")) }
                }
            }
            if (editingMerchant) CustomMerchantEditor(
                existing = custom,
                initialName = m.displayName,
                categories = categories,
                onDismiss = { editingMerchant = false },
                onSave = { merchant, done -> vm.saveCustomMerchant(merchant, done) },
                onDelete = custom?.let { { vm.deleteCustomMerchant(it) } },
            )
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Últimos 6 meses", "Last 6 months"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    var selected by remember { mutableStateOf<Int?>(null) }
                    BarChart(
                        values = m.trend.map { it.second },
                        labels = m.trend.map { it.first.shortName() },
                        selected = selected ?: (m.trend.size - 1),
                        valueLabel = { formatCents(it) },
                        onSelect = { selected = it },
                        barAreaHeight = 90,
                    )
                }
            }
        }
        item { Text(tr("Historial", "History"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(m.recent, key = { it.id }) { e ->
            ExpenseLine(e, dateOf(e.timestamp).shortLabel() + if (e.note.isNotBlank()) " · ${e.note}" else "", null)
        }
    }

    if (changing && m != null) {
        AlertDialog(
            onDismissRequest = { changing = false },
            title = { Text(tr("Categoría de «${m.displayName}»", "Category for “${m.displayName}”")) },
            text = {
                Column {
                    Text(
                        tr("Se cambiará en sus ${m.count} gastos y en los próximos pagos de este comercio.", "It will change in its ${m.count} expenses and in future payments at this merchant."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = cat.id == m.categoryId,
                                onClick = {
                                    vm.recategorizeMerchant(merchantKey, cat.id)
                                    changing = false
                                },
                                label = { Text("${cat.emoji} ${cat.name}") },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { changing = false }) { Text(tr("Cerrar", "Close")) } },
        )
    }
}
