package com.midinero.control.ui.screens

import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.foundation.lazy.itemsIndexed
import com.midinero.control.domain.tr
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.domain.Budgets
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.label
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.BudgetColors

@Composable
fun LimitsScreen(vm: MainViewModel, onBack: (() -> Unit)? = null) {
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val suggestions by vm.limitSuggestions.collectAsStateWithLifecycle()
    val compliance by vm.compliance.collectAsStateWithLifecycle()
    val prefsVersion by vm.prefs.changes.collectAsStateWithLifecycle()
    val monthlyBudget = remember(prefsVersion) { vm.prefs.monthlyBudgetCents }
    val rolloverOn = remember(prefsVersion) { vm.prefs.rolloverEnabled }
    val rollover by vm.rollover.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = com.midinero.control.ui.LocalSnackbarHostState.current
    val totalByCategory = totals.associate { it.categoryId to it.totalCents }
    val monthTotal = totals.sumOf { it.totalCents }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var creatingIncome by rememberSaveable { mutableStateOf(false) }
    var editingBudget by rememberSaveable { mutableStateOf(false) }
    val editing = editingId?.let { id -> (categories + incomeCategories).firstOrNull { it.id == id } }

    val withLimit = categories.filter { (it.monthlyLimitCents ?: 0L) > 0L }
    // Las ocultas sin límite no se listan, salvo que tengan gasto este mes
    val withoutLimit = categories.filter { (it.monthlyLimitCents ?: 0L) <= 0L && (!it.hidden || (totalByCategory[it.id] ?: 0L) > 0L) }
    val limitsSum = withLimit.sumOf { it.monthlyLimitCents ?: 0L }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                ScreenHeader(
                    tr("Límites de gasto", "Spending limits"),
                    subtitle = tr("Un presupuesto para el mes y, si quieres, un límite por categoría. ", "A budget for the month and, if you like, a limit per category. ") +
                        tr("Gasto de ${month.label()}.", "Spending for ${month.label()}."),
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item {
                HintCard(vm, "hint_limits", tr("Pon un presupuesto total y límites por categoría: te avisaremos antes de pasarte.", "Set a total budget and limits per category: we'll warn you before you go over."), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item {
                AppCard(
                    onClick = { editingBudget = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Presupuesto total del mes", "Total monthly budget"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.Edit, contentDescription = tr("Editar presupuesto", "Edit budget"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (monthlyBudget > 0) {
                            val carried = rollover[null] ?: 0L
                            val pace = Budgets.pace(month, monthTotal, monthlyBudget + carried)
                            Text(
                                tr("${formatCents(monthTotal)} de ${formatCents(monthlyBudget + carried)}", "${formatCents(monthTotal)} of ${formatCents(monthlyBudget + carried)}") +
                                    if (carried > 0) tr(" (incluye ${formatCents(carried)} que sobraron el mes pasado)", " (includes ${formatCents(carried)} left over from last month)") else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            RankBar(pace.ratio, limitColor(pace.ratio))
                            if (limitsSum > monthlyBudget) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    tr("Ojo: la suma de tus límites (${formatCents(limitsSum)}) supera el presupuesto total.", "Note: the sum of your limits (${formatCents(limitsSum)}) is more than the total budget."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BudgetColors.warning,
                                )
                            }
                        } else {
                            Text(
                                tr("Sin presupuesto. Toca para poner cuánto quieres gastar como máximo cada mes.", "No budget. Tap to set the most you want to spend each month."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    MenuRow(
                        Icons.Filled.Autorenew, SectionColors.limits,
                        tr("Arrastrar lo que sobra", "Carry over what's left"),
                        tr("Lo que no gastes de un límite este mes se suma al del mes que viene.", "Whatever you don't spend of a limit this month is added to next month's."),
                        onClick = { vm.prefs.rolloverEnabled = !rolloverOn },
                        trailing = { androidx.compose.material3.Switch(checked = rolloverOn, onCheckedChange = { vm.prefs.rolloverEnabled = it }) },
                    )
                }
            }
            if (withLimit.isNotEmpty()) {
                item { SectionTitle(tr("Con límite · ${formatCents(limitsSum)} en total", "With a limit · ${formatCents(limitsSum)} in total")) }
                itemsIndexed(withLimit, key = { _, it -> it.id }) { i, cat ->
                    Column(Modifier.grouped(i, withLimit.size)) {
                        LimitRow(cat, totalByCategory[cat.id] ?: 0L, compliance[cat.id], null, rollover[cat.id] ?: 0L) { editingId = cat.id }
                        GroupDivider(i, withLimit.size)
                    }
                }
            }
            if (withoutLimit.isNotEmpty()) {
                item { SectionTitle(tr("Sin límite", "No limit")) }
                itemsIndexed(withoutLimit, key = { _, it -> it.id }) { i, cat ->
                    Column(Modifier.grouped(i, withoutLimit.size)) {
                        LimitRow(cat, totalByCategory[cat.id] ?: 0L, null, suggestions[cat.id]) { editingId = cat.id }
                        GroupDivider(i, withoutLimit.size)
                    }
                }
            }
            item { SectionTitle(tr("Categorías de ingresos", "Income categories")) }
            itemsIndexed(incomeCategories, key = { _, it -> it.id }) { i, cat ->
                Column(Modifier.grouped(i, incomeCategories.size)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { editingId = cat.id }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(cat)
                        Spacer(Modifier.width(16.dp))
                        Text(cat.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Edit, contentDescription = tr("Editar ${cat.name}", "Edit ${cat.name}"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GroupDivider(i, incomeCategories.size)
                }
            }
            item {
                TextButton(onClick = { creatingIncome = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(tr("+ Nueva categoría de ingresos", "+ New income category"))
                }
            }
        }
        AppFab(
            text = tr("Nueva categoría", "New category"),
            action = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { cat ->
        CategoryDialog(
            category = cat,
            onDismiss = { editingId = null },
            onSave = { vm.saveCategory(it) { ok -> if (ok) editingId = null else duplicateName(context) } },
            onDelete = { to -> vm.deleteCategory(cat, to, snackbar); editingId = null },
            suggestionCents = suggestions[cat.id],
            moveTargets = categories + incomeCategories,
        )
    }
    if (creating) {
        CategoryDialog(
            category = null,
            onDismiss = { creating = false },
            onSave = { vm.saveCategory(it) { ok -> if (ok) creating = false else duplicateName(context) } },
            onDelete = null,
        )
    }
    if (creatingIncome) {
        CategoryDialog(
            category = null,
            onDismiss = { creatingIncome = false },
            onSave = { vm.saveCategory(it) { ok -> if (ok) creatingIncome = false else duplicateName(context) } },
            onDelete = null,
            isIncome = true,
        )
    }
    if (editingBudget) {
        BudgetDialog(
            current = monthlyBudget,
            suggestion = suggestions.values.sum().takeIf { it > 0 }?.let { Budgets.roundSuggestion(it) },
            onDismiss = { editingBudget = false },
            onSave = { vm.prefs.monthlyBudgetCents = it; editingBudget = false },
        )
    }
}


private fun duplicateName(context: Context) =
    Toast.makeText(context, tr("Ya hay una categoría con ese nombre", "A category with that name already exists"), Toast.LENGTH_SHORT).show()

@Composable
private fun BudgetDialog(current: Long, suggestion: Long?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current > 0) centsToInput(current) else "") }
    val amount = parseUserAmount(text)
    EditorSheet(
        title = tr("Presupuesto del mes", "Monthly budget"),
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = text.isBlank() || amount != null,
        onPrimary = { onSave(if (text.isBlank()) 0L else amount!!) },
    ) {
        Column {
            Text(
                tr("Lo máximo que quieres gastar en total cada mes. Te avisaremos al acercarte y al pasarte.", "The most you want to spend in total each month. We'll warn you when you get close and when you go over."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AmountField(value = text, onValueChange = { text = it }, isError = text.isNotBlank() && amount == null)
            if (suggestion != null) {
                TextButton(onClick = { text = centsToInput(suggestion) }) {
                    Text(tr("Usar lo que sueles gastar: ${formatCents(suggestion)}", "Use what you usually spend: ${formatCents(suggestion)}"))
                }
            }
            if (current > 0) {
                TextButton(onClick = { onSave(0L); onDismiss() }) {
                    Text(tr("Quitar el presupuesto", "Remove the budget"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).asHeading(),
    )
}

@Composable
private fun LimitRow(
    category: Category,
    spentCents: Long,
    compliance: Pair<Int, Int>?,
    suggestion: Long?,
    carried: Long = 0L,
    onClick: () -> Unit,
) {
    val limit = category.monthlyLimitCents?.takeIf { it > 0 }?.let { it + carried }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    limit?.let { formatCents(it) } ?: tr("Sin límite", "No limit"),
                    fontWeight = if (limit != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (limit != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (limit != null) {
                val ratio = spentCents.toFloat() / limit
                Spacer(Modifier.height(6.dp))
                RankBar(ratio, limitColor(ratio))
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(tr("Gastado ${formatCents(spentCents)}", "Spent ${formatCents(spentCents)}"))
                        if (spentCents > limit) append(tr(" · te has pasado ${formatCents(spentCents - limit)}", " · over by ${formatCents(spentCents - limit)}"))
                        else append(tr(" · quedan ${formatCents(limit - spentCents)}", " · ${formatCents(limit - spentCents)} left"))
                        if (carried > 0) append(tr(" · +${formatCents(carried)} del mes pasado", " · +${formatCents(carried)} from last month"))
                        if (compliance != null && compliance.second > 0) {
                            append(tr(" · cumplido ${compliance.first} de ${compliance.second} meses", " · met in ${compliance.first} of ${compliance.second} months"))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    buildString {
                        append(tr("Gastado: ${formatCents(spentCents)}", "Spent: ${formatCents(spentCents)}"))
                        if (suggestion != null) append(tr(" · sueles gastar ${formatCents(suggestion)}", " · you usually spend ${formatCents(suggestion)}"))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
