package com.midinero.control.ui.screens

import kotlinx.coroutines.launch
import com.midinero.control.ui.showUndo
import com.midinero.control.domain.centsToInput
import androidx.compose.foundation.clickable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.ui.DebtsViewModel
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Debt
import com.midinero.control.domain.Split
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.theme.BudgetColors

/** Cuentas con amigos: quién te debe, a quién debes y saldar. */
@Composable
fun DebtsScreen(onBack: () -> Unit, vm: DebtsViewModel = viewModel()) {
    val debts by vm.debts.collectAsStateWithLifecycle()
    val balances = Split.balances(debts)
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> debts.firstOrNull { it.id == id } }
    val snackbar = com.midinero.control.ui.LocalSnackbarHostState.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var settling by rememberSaveable { mutableStateOf<String?>(null) }
    val owedToMe = balances.filter { it.balanceCents > 0 }.sumOf { it.balanceCents }
    val iOwe = -balances.filter { it.balanceCents < 0 }.sumOf { it.balanceCents }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ScreenHeader(
                    tr("Cuentas con amigos", "Split with friends"),
                    subtitle = tr("Al compartir un gasto solo cuenta tu parte; aquí ves lo que te deben y lo que debes.", "When you share an expense only your part counts; here you see what you're owed and what you owe."),
                    onBack = onBack,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(tr("Te deben", "You're owed"), formatCents(owedToMe), null, Modifier.weight(1f))
                    StatTile(tr("Debes", "You owe"), formatCents(iOwe), null, Modifier.weight(1f))
                }
            }
            if (balances.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🤝",
                        title = tr("Todo en paz", "All square"),
                        text = tr("Nadie te debe nada y no debes nada. Al compartir un gasto verás aquí quién te debe.", "Nobody owes you anything and you owe nothing. When you share an expense you'll see here who owes you."),
                    )
                }
            }
            items(balances, key = { it.person.lowercase() }) { b ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.person, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (b.balanceCents > 0) tr("Te debe ${formatCents(b.balanceCents)}", "Owes you ${formatCents(b.balanceCents)}") else tr("Le debes ${formatCents(-b.balanceCents)}", "You owe ${formatCents(-b.balanceCents)}"),
                                    color = if (b.balanceCents > 0) BudgetColors.ok else BudgetColors.warning,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            TextButton(onClick = { settling = b.person }) { Text(tr("Saldar", "Settle")) }
                        }
                        // Cada apunte se toca para corregirlo o borrarlo
                        debts.filter { it.settledAt == null && it.person.trim().equals(b.person, ignoreCase = true) }.forEach { d ->
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(onClickLabel = tr("Editar", "Edit")) { editingId = d.id }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(
                                    d.note.ifBlank { tr("Sin concepto", "No description") } + " · " + dateOf(d.createdAt).shortLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text((if (d.amountCents > 0) "+" else "") + formatCents(d.amountCents), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        AppFab(
            text = tr("Apuntar deuda", "Add debt"),
            action = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        DebtDialog(debt = null, onDismiss = { adding = false }, onSave = { vm.saveDebt(it); adding = false }, onDelete = null)
    }
    editing?.let { d ->
        DebtDialog(
            debt = d,
            onDismiss = { editingId = null },
            onSave = { vm.saveDebt(it); editingId = null },
            onDelete = {
                editingId = null
                vm.deleteDebt(d)
                scope.launch { snackbar.showUndo(tr("Apunte borrado: ${d.person}", "Entry deleted: ${d.person}")) { vm.restoreDebt(d) } }
            },
        )
    }
    settling?.let { person ->
        AlertDialog(
            onDismissRequest = { settling = null },
            title = { Text(tr("¿Saldar con $person?", "Settle up with $person?")) },
            text = { Text(tr("Se marcarán como pagadas todas las cuentas pendientes con $person.", "All pending amounts with $person will be marked as paid.")) },
            confirmButton = { TextButton(onClick = { vm.settlePerson(person); settling = null }) { Text(tr("Saldar", "Settle")) } },
            dismissButton = { TextButton(onClick = { settling = null }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtDialog(debt: Debt?, onDismiss: () -> Unit, onSave: (Debt) -> Unit, onDelete: (() -> Unit)?) {
    var person by rememberSaveable { mutableStateOf(debt?.person ?: "") }
    var amountText by rememberSaveable { mutableStateOf(debt?.let { centsToInput(kotlin.math.abs(it.amountCents)) } ?: "") }
    var note by rememberSaveable { mutableStateOf(debt?.note ?: "") }
    var theyOweMe by rememberSaveable { mutableStateOf((debt?.amountCents ?: 1L) > 0) }
    val amount = parseUserAmount(amountText)
    EditorSheet(
        title = if (debt == null) tr("Apuntar deuda", "Add debt") else tr("Editar deuda", "Edit debt"),
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = person.isNotBlank() && amount != null && amount > 0,
        onPrimary = {
            val signed = if (theyOweMe) amount!! else -amount!!
            onSave((debt ?: Debt(person = "", amountCents = 0)).copy(person = person.trim(), amountCents = signed, note = note.trim()))
        },
    ) {
        Column {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(true to tr("Me debe", "Owes me"), false to tr("Le debo", "I owe")).forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = theyOweMe == v,
                        onClick = { theyOweMe = v },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(person, { person = it.take(40) }, label = { Text(tr("Persona", "Person")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                amountText,
                { amountText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) },
                label = { Text(tr("Importe (${com.midinero.control.domain.Currencies.symbol()})", "Amount (${com.midinero.control.domain.Currencies.symbol()})")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(note, { note = it.take(80) }, label = { Text(tr("Concepto (opcional)", "Description (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (onDelete != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete) { Text(tr("Borrar este apunte", "Delete this entry"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Diálogo para compartir un gasto: con quién, y se apunta solo tu parte. */
@Composable
fun ShareExpenseDialog(totalCents: Long, onDismiss: () -> Unit, onShare: (List<String>) -> Unit) {
    var peopleText by rememberSaveable { mutableStateOf("") }
    val people = Split.parsePeople(peopleText)
    EditorSheet(
        title = tr("Compartir gasto", "Share expense"),
        onDismiss = onDismiss,
        primaryText = tr("Compartir", "Share"),
        primaryEnabled = people.isNotEmpty(),
        onPrimary = { onShare(people) },
    ) {
        Column {
            Text(
                tr("Se repartirá ${formatCents(totalCents)} a partes iguales. En tus gastos quedará solo tu parte y ", "${formatCents(totalCents)} will be split equally. Only your part will stay in your expenses and ") +
                    tr("MiDinero apuntará lo que te debe cada uno.", "MiDinero will log what each person owes you."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                peopleText,
                { peopleText = it.take(200) },
                label = { Text(tr("Con quién (ej. Ana, Luis y Marta)", "With whom (e.g. Ana, Luis and Marta)")) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (people.isNotEmpty()) {
                val (mine, others) = Split.equal(totalCents, people)
                Spacer(Modifier.height(8.dp))
                Text(tr("Tu parte: ${formatCents(mine)}", "Your part: ${formatCents(mine)}"), fontWeight = FontWeight.SemiBold)
                others.forEach { (p, c) -> Text(tr("$p te debe ${formatCents(c)}", "$p owes you ${formatCents(c)}"), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
