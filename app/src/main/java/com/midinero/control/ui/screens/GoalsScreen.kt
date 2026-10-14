package com.midinero.control.ui.screens

import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.ui.GoalsViewModel
import com.midinero.control.domain.tr
import com.midinero.control.domain.Lang
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.SavingsGoal
import com.midinero.control.domain.Goals
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.GoalWithProgress
import com.midinero.control.ui.theme.BudgetColors
import java.time.LocalDate

private val monthYear get() = Lang.dateFormat("MMMM yyyy", "MMMM yyyy")

@Composable
fun GoalsScreen(onBack: () -> Unit, vm: GoalsViewModel = viewModel()) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var contributingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val active = goals.filter { !it.goal.archived }
    val archived = goals.filter { it.goal.archived }
    val totalSaved = active.sumOf { it.progress.savedCents }
    val totalTarget = active.sumOf { it.goal.targetCents }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    tr("Metas de ahorro", "Savings goals"),
                    subtitle = tr("Aparta dinero para lo que quieras: un viaje, el colchón de emergencias, el coche… ", "Put money aside for anything you like: a trip, an emergency fund, a car… ") +
                        tr("Las aportaciones no cuentan como gasto.", "Contributions don't count as spending."),
                    onBack = onBack,
                )
            }
            if (active.isNotEmpty()) {
                item {
                    StatTile(
                        tr("Ahorrado en tus metas", "Saved in your goals"),
                        formatCents(totalSaved),
                        tr("de ${formatCents(totalTarget)} (${if (totalTarget > 0) totalSaved * 100 / totalTarget else 0} %)", "of ${formatCents(totalTarget)} (${if (totalTarget > 0) totalSaved * 100 / totalTarget else 0} %)"),
                        Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item {
                    EmptyState(
                        emoji = "🎯",
                        title = tr("Aún no tienes metas", "No goals yet"),
                        text = tr("Aparta dinero para un viaje, el coche o un colchón de emergencias. Crea una con «Nueva meta».", "Put money aside for a trip, a car or an emergency fund. Create one with “New goal”."),
                    )
                }
            }
            items(active, key = { it.goal.id }) { g ->
                GoalCard(g, onContribute = { contributingId = g.goal.id }, onEdit = { editingId = g.goal.id })
            }
            if (archived.isNotEmpty()) {
                item {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text(if (showArchived) tr("Ocultar metas archivadas", "Hide archived goals") else tr("Ver metas archivadas (${archived.size})", "Show archived goals (${archived.size})"))
                    }
                }
                if (showArchived) {
                    items(archived, key = { it.goal.id }) { g ->
                        GoalCard(g, onContribute = null, onEdit = { editingId = g.goal.id })
                    }
                }
            }
        }
        AppFab(
            text = tr("Nueva meta", "New goal"),
            action = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        GoalDialog(null, onDismiss = { creating = false }, onSave = { vm.saveGoal(it); creating = false }, onDelete = null, onArchive = null)
    }
    editingId?.let { id ->
        goals.firstOrNull { it.goal.id == id }?.let { g ->
            GoalDialog(
                g.goal,
                onDismiss = { editingId = null },
                onSave = { vm.saveGoal(it); editingId = null },
                onDelete = { vm.deleteGoal(g.goal); editingId = null },
                onArchive = { vm.saveGoal(g.goal.copy(archived = !g.goal.archived)); editingId = null },
            )
        }
    }
    contributingId?.let { id ->
        goals.firstOrNull { it.goal.id == id }?.let { g ->
            ContributionDialog(
                g,
                onDismiss = { contributingId = null },
                onSave = { cents, note -> vm.contribute(id, cents, note); contributingId = null },
                onDeleteContribution = { vm.deleteContribution(it) },
            )
        }
    }
}

@Composable
private fun GoalCard(g: GoalWithProgress, onContribute: (() -> Unit)?, onEdit: () -> Unit) {
    val p = g.progress
    val deadline = g.goal.deadlineEpochDay?.let { LocalDate.ofEpochDay(it) }
    val onTrack = Goals.onTrack(p, deadline)
    AppCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.goal.emoji, fontSize = 28.sp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.goal.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("${formatCents(p.savedCents)} de ${formatCents(p.targetCents)}", "${formatCents(p.savedCents)} of ${formatCents(p.targetCents)}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${(p.ratio * 100).toInt().coerceAtMost(999)} %", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            RankBar(p.ratio, if (p.completed) BudgetColors.ok else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    p.completed -> tr("🎉 ¡Meta conseguida!", "🎉 Goal reached!")
                    p.monthlyNeededCents != null && deadline != null ->
                        tr("Aparta ${formatCents(p.monthlyNeededCents)} al mes para llegar el ${deadline.shortLabel()}", "Put aside ${formatCents(p.monthlyNeededCents)} a month to get there by ${deadline.shortLabel()}")
                    p.estimatedDate != null -> tr("A este ritmo lo consigues en ${p.estimatedDate.format(monthYear)}", "At this pace you'll get there in ${p.estimatedDate.format(monthYear)}")
                    else -> tr("Te faltan ${formatCents(p.remainingCents)}", "${formatCents(p.remainingCents)} to go")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!p.completed && onTrack != null) {
                Text(
                    if (onTrack) tr("✅ Vas a buen ritmo", "✅ You're on track") else tr("⚠️ Al ritmo actual no llegas a tiempo", "⚠️ At the current pace you won't make it in time"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (onTrack) BudgetColors.ok else BudgetColors.warning,
                )
            }
            if (onContribute != null && !p.completed) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onContribute) { Text(tr("Aportar", "Contribute")) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
    goal: SavingsGoal?,
    onDismiss: () -> Unit,
    onSave: (SavingsGoal) -> Unit,
    onDelete: (() -> Unit)?,
    onArchive: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(goal?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(goal?.emoji ?: "🎯") }
    var targetText by rememberSaveable { mutableStateOf(goal?.let { centsToInput(it.targetCents) } ?: "") }
    var deadline by rememberSaveable { mutableStateOf(goal?.deadlineEpochDay?.let { LocalDate.ofEpochDay(it) }) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val target = parseUserAmount(targetText)

    EditorSheet(
        title = if (goal == null) tr("Nueva meta", "New goal") else tr("Editar meta", "Edit goal"),
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = name.isNotBlank() && target != null,
        onPrimary = {
            val base = goal ?: SavingsGoal(name = "", targetCents = 0)
            onSave(base.copy(name = name.trim(), emoji = emoji, targetCents = target!!, deadlineEpochDay = deadline?.toEpochDay()))
        },
    ) {
        FlowRow {
            listOf("🎯", "✈️", "🏖️", "🚗", "🏠", "💍", "🎓", "🛟", "💻", "📱", "🎁", "👶").forEach { e ->
                TextButton(onClick = { emoji = e }, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(e, fontSize = if (e == emoji) 24.sp else 18.sp)
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text(tr("Nombre (ej. Viaje a Japón)", "Name (e.g. Trip to Japan)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = targetText,
            onValueChange = { targetText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) },
            label = { Text(tr("Objetivo (${com.midinero.control.domain.Currencies.symbol()})", "Target (${com.midinero.control.domain.Currencies.symbol()})")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.DateRange, contentDescription = tr("Fecha límite", "Deadline"))
                Spacer(Modifier.size(8.dp))
                Text(deadline?.shortLabel() ?: tr("Sin fecha límite", "No deadline"))
            }
            if (deadline != null) TextButton(onClick = { deadline = null }) { Text(tr("Quitar", "Remove")) }
        }
        if (onArchive != null) {
            TextButton(onClick = onArchive) { Text(if (goal?.archived == true) tr("Desarchivar", "Unarchive") else tr("Archivar", "Archive")) }
        }
        if (onDelete != null) {
            TextButton(onClick = { confirmDelete = true }) { Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (pickDate) {
        DatePickerModal(
            initial = deadline ?: LocalDate.now().plusMonths(6),
            onDismiss = { pickDate = false },
            onPicked = { deadline = it; pickDate = false },
        )
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar «${goal?.name}»?", "Delete “${goal?.name}”?")) },
            text = { Text(tr("Se borrarán también sus aportaciones.", "Its contributions will also be deleted.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributionDialog(
    g: GoalWithProgress,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit,
    onDeleteContribution: (com.midinero.control.data.GoalContribution) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var withdraw by rememberSaveable { mutableStateOf(false) }
    val amount = parseUserAmount(text)
    EditorSheet(
        title = "${g.goal.emoji} ${g.goal.name}",
        onDismiss = onDismiss,
        primaryText = if (withdraw) tr("Retirar", "Withdraw") else tr("Aportar", "Contribute"),
        primaryEnabled = amount != null,
        onPrimary = { onSave(if (withdraw) -amount!! else amount!!, note.trim()) },
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to tr("Aportar", "Contribute"), true to tr("Retirar", "Withdraw")).forEachIndexed { i, (w, label) ->
                SegmentedButton(
                    selected = withdraw == w,
                    onClick = { withdraw = w },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) },
            label = { Text(tr("Importe (${com.midinero.control.domain.Currencies.symbol()})", "Amount (${com.midinero.control.domain.Currencies.symbol()})")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        g.progress.monthlyNeededCents?.let { needed ->
            TextButton(onClick = { text = centsToInput(needed); withdraw = false }) {
                Text(tr("Lo de este mes: ${formatCents(needed)}", "This month's amount: ${formatCents(needed)}"))
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(100) },
            label = { Text(tr("Nota (opcional)", "Note (optional)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (g.contributions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(tr("Historial", "History"), style = MaterialTheme.typography.labelLarge)
            g.contributions.take(10).forEach { c ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (c.amountCents >= 0) "+" else "") + formatCents(c.amountCents), fontWeight = FontWeight.SemiBold)
                        Text(
                            dateOf(c.timestamp).shortLabel() + if (c.note.isNotBlank()) " · ${c.note}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onDeleteContribution(c) }) { Text(tr("Borrar", "Delete")) }
                }
            }
        }
    }
}
