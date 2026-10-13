package com.midinero.control.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Account
import com.midinero.control.data.Transfer
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.shortLabel
import com.midinero.control.domain.tr
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.BudgetColors
import com.midinero.control.ui.theme.MoneyStyle

/** Tus cuentas (efectivo, banco, Revolut…) con su saldo y los traspasos entre ellas. */
@Composable
fun AccountsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val balances by vm.accountBalances.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val prefsVersion by vm.prefs.changes.collectAsStateWithLifecycle()
    val defaultId = remember(prefsVersion) { vm.prefs.defaultAccountId }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf(false) }
    val accounts = balances.map { it.account }
    val active = balances.filter { !it.account.archived }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    tr("Cuentas", "Accounts"),
                    subtitle = tr("Dónde tienes el dinero. Elige en qué cuenta va cada gasto y pasa dinero de una a otra sin que cuente como gasto.", "Where your money is. Pick the account for each expense and move money between them without it counting as spending."),
                    onBack = onBack,
                )
            }
            if (balances.isEmpty()) item {
                Text(
                    tr("Crea tu primera cuenta (por ejemplo «Banco» o «Efectivo») con el saldo que tienes hoy. Desde entonces, lo que apuntes en ella mueve su saldo.", "Create your first account (for example “Bank” or “Cash”) with today's balance. From then on, what you log in it moves its balance."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("En total", "In total"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val total = active.sumOf { it.balanceCents }
                        Text(formatCents(total), style = MaterialTheme.typography.headlineMedium.merge(MoneyStyle), color = if (total < 0) BudgetColors.over else MaterialTheme.colorScheme.onSurface)
                        if (active.size >= 2) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { moving = true }) { Text(tr("Pasar dinero entre cuentas", "Move money between accounts")) }
                        }
                    }
                }
            }
            items(balances, key = { it.account.id }) { b ->
                AppCard(onClick = { editingId = b.account.id }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.account.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.account.name + if (b.account.archived) tr(" (archivada)", " (archived)") else "", fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    tr("Por defecto", "Default").takeIf { b.account.id == defaultId },
                                    tr("${formatCents(b.spentThisMonthCents)} gastado este mes", "${formatCents(b.spentThisMonthCents)} spent this month"),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            formatCents(b.balanceCents),
                            style = MaterialTheme.typography.titleMedium.merge(MoneyStyle),
                            color = if (b.balanceCents < 0) BudgetColors.over else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (transfers.isNotEmpty()) {
                item { SectionHeader(tr("Traspasos", "Transfers")) }
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            transfers.take(30).forEachIndexed { i, t ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                TransferRow(t, accounts, onDelete = { vm.deleteTransfer(t) })
                            }
                        }
                    }
                }
            }
        }
        AppFab(
            text = tr("Nueva cuenta", "New account"),
            action = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    val editing = editingId?.let { id -> balances.firstOrNull { it.account.id == id } }
    if (creating || editing != null) {
        AccountDialog(
            account = editing?.account,
            currentBalance = editing?.balanceCents,
            isDefault = editing != null && editing.account.id == defaultId,
            onDismiss = { creating = false; editingId = null },
            onSave = { account, makeDefault ->
                vm.saveAccount(account, makeDefault)
                creating = false; editingId = null
            },
            onDelete = editing?.let { e -> { vm.deleteAccount(e.account); editingId = null } },
        )
    }
    if (moving) TransferDialog(active.map { it.account }, onDismiss = { moving = false }) { vm.addTransfer(it); moving = false }
}

@Composable
private fun TransferRow(t: Transfer, accounts: List<Account>, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val from = accounts.firstOrNull { it.id == t.fromAccountId }
    val to = accounts.firstOrNull { it.id == t.toAccountId }
    Row(Modifier.fillMaxWidth().clickable { confirm = true }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${from?.emoji ?: ""} ${from?.name ?: "?"} → ${to?.emoji ?: ""} ${to?.name ?: "?"}", fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(dateOf(t.timestamp).shortLabel(), t.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatCents(t.amountCents), style = MaterialTheme.typography.titleSmall.merge(MoneyStyle))
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(tr("¿Borrar este traspaso?", "Delete this transfer?")) },
        text = { Text(tr("Los saldos de las dos cuentas vuelven a como estaban.", "Both accounts' balances go back to how they were.")) },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(tr("Borrar", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Cancelar", "Cancel")) } },
    )
}

private val accountEmojis = listOf("💳", "🏦", "💶", "👛", "📱", "🪙", "💼", "🐷", "🌍", "₿")

@Composable
private fun AccountDialog(
    account: Account?,
    currentBalance: Long?,
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onSave: (Account, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(account?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(account?.emoji ?: "💳") }
    var balanceText by rememberSaveable { mutableStateOf((currentBalance ?: 0L).let { if (it == 0L && account == null) "" else centsToInput(it) }) }
    var archived by rememberSaveable { mutableStateOf(account?.archived ?: false) }
    var makeDefault by rememberSaveable { mutableStateOf(isDefault) }
    var confirmDelete by remember { mutableStateOf(false) }
    val negative = balanceText.trim().startsWith("-")
    val parsed = parseUserAmount(balanceText.trim().removePrefix("-"))
    val balance = if (balanceText.isBlank()) 0L else parsed?.let { if (negative) -it else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) tr("Nueva cuenta", "New account") else tr("Editar cuenta", "Edit account")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text(tr("Nombre", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    accountEmojis.forEach { e ->
                        TextButton(onClick = { emoji = e }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(30.dp)) {
                            Text(e, fontSize = if (e == emoji) 22.sp else 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == ',' || c == '.' || c == '-' }.take(12) },
                    label = { Text(tr("Saldo de hoy (€)", "Today's balance (€)")) },
                    supportingText = { Text(tr("Lo que tiene ahora. Desde hoy, lo que apuntes en ella lo mueve.", "What it holds now. From today, what you log in it moves it.")) },
                    isError = balance == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Cuenta por defecto para los gastos nuevos y detectados", "Default account for new and detected expenses"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = makeDefault, onCheckedChange = { makeDefault = it })
                }
                if (account != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Archivar (no se ofrece al apuntar)", "Archive (not offered when logging)"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = archived, onCheckedChange = { archived = it })
                }
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(tr("Eliminar cuenta", "Delete account"), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && balance != null,
                onClick = {
                    // Cambiar el saldo «reinicia» la cuenta: el saldo nuevo cuenta desde ahora
                    val changedBalance = account == null || balance != currentBalance
                    val base = account ?: Account(name = "")
                    val saved = base.copy(
                        name = name.trim(), emoji = emoji, archived = archived,
                        initialBalanceCents = if (changedBalance) balance!! else base.initialBalanceCents,
                        balanceSince = if (changedBalance) System.currentTimeMillis() else base.balanceSince,
                    )
                    onSave(saved, makeDefault)
                },
            ) { Text(tr("Guardar", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(tr("¿Eliminar «${account?.name}»?", "Delete “${account?.name}”?")) },
        text = { Text(tr("Sus movimientos no se borran: se quedan sin cuenta. Sus traspasos sí se borran.", "Its transactions aren't deleted: they're left without an account. Its transfers are deleted.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TransferDialog(accounts: List<Account>, onDismiss: () -> Unit, onSave: (Transfer) -> Unit) {
    var from by rememberSaveable { mutableStateOf(accounts.getOrNull(0)?.id) }
    var to by rememberSaveable { mutableStateOf(accounts.getOrNull(1)?.id) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val amount = parseUserAmount(amountText)
    val valid = from != null && to != null && from != to && amount != null && amount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Pasar dinero", "Move money")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FieldLabel(tr("De", "From"))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { a -> FilterChip(selected = from == a.id, onClick = { from = a.id }, label = { Text("${a.emoji} ${a.name}") }) }
                }
                FieldLabel(tr("A", "To"))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { a -> FilterChip(selected = to == a.id, onClick = { to = a.id }, label = { Text("${a.emoji} ${a.name}") }) }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) },
                    label = { Text(tr("Importe (€)", "Amount (€)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(value = note, onValueChange = { note = it.take(80) }, label = { Text(tr("Nota (opcional)", "Note (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text(
                    tr("Ej.: sacar del cajero o recargar Revolut. No cuenta como gasto ni como ingreso.", "E.g. an ATM withdrawal or topping up Revolut. It doesn't count as spending or income."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(Transfer(fromAccountId = from!!, toAccountId = to!!, amountCents = amount!!, timestamp = System.currentTimeMillis(), note = note.trim())) }) {
                Text(tr("Pasar", "Move"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}
