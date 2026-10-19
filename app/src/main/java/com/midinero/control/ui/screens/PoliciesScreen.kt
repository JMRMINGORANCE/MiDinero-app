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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.midinero.control.data.Policy
import com.midinero.control.data.PolicyPrice
import com.midinero.control.data.Vehicle
import com.midinero.control.domain.Garage
import com.midinero.control.domain.Lang
import com.midinero.control.domain.ReminderKind
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.formatQuantity
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.tr
import com.midinero.control.ui.HouseholdViewModel
import com.midinero.control.ui.theme.BudgetColors
import java.time.LocalDate

/** Tipos de seguro que se ofrecen. */
private enum class PolicyType(val emoji: String, val es: String, val en: String) {
    CAR("🚗", "Coche", "Car"), HOME("🏠", "Hogar", "Home"), LIFE("❤️", "Vida", "Life"),
    HEALTH("🩺", "Salud", "Health"), OTHER("🛡️", "Otro", "Other");

    val label: String get() = tr(es, en)

    companion object {
        fun of(v: String) = entries.firstOrNull { it.name == v } ?: OTHER
    }
}

private val longDate get() = Lang.dateFormat("d MMM yyyy")

/** Seguros con su precio, cuánto han subido y el aviso para negociar antes de renovar. */
@Composable
fun PoliciesScreen(onBack: () -> Unit, hvm: HouseholdViewModel = viewModel()) {
    val policies by hvm.policies.collectAsStateWithLifecycle()
    val prices by hvm.prices.collectAsStateWithLifecycle()
    val vehicles by hvm.vehicles.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var renewingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = LocalDate.now()
    val reminders = remember(policies) { Garage.reminders(emptyList(), emptyList(), policies).associateBy { it.refId } }
    val yearly = policies.filter { !it.archived }.sumOf { p -> Garage.priceHistory(p.id, prices).lastOrNull()?.amountCents ?: 0L }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    tr("🛡️ Seguros", "🛡️ Insurance"),
                    subtitle = if (yearly > 0) tr("${formatCents(yearly)} al año entre todos", "${formatCents(yearly)} a year in total")
                    else tr("Te avisamos antes de que renueven para que pidas precio y negocies", "We remind you before they renew so you can ask for the price and haggle"),
                    onBack = onBack,
                )
            }
            if (policies.isEmpty()) item {
                EmptyState(
                    "🛡️", tr("Apunta tus seguros", "Add your insurance"),
                    tr(
                        "Con la fecha de vencimiento te avisamos 45 días antes para que pidas el precio de renovación y negocies, antes del plazo de un mes para darte de baja.",
                        "With the renewal date we remind you 45 days before so you can ask for the renewal price and haggle, before the one-month notice to cancel.",
                    ),
                    action = tr("Añadir seguro", "Add insurance"), onAction = { creating = true },
                )
            }
            items(policies, key = { it.id }) { p ->
                val history = Garage.priceHistory(p.id, prices)
                val current = history.lastOrNull()
                val change = Garage.lastChange(history)
                val reminder = reminders[p.id]
                val type = PolicyType.of(p.type)
                val car = vehicles.firstOrNull { it.id == p.vehicleId }
                AppCard(Modifier.fillMaxWidth(), onClick = { editingId = p.id }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(type.emoji, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(p.company.ifBlank { null }, car?.let { "${it.emoji} ${it.name}" }, if (p.archived) tr("archivado", "archived") else null).joinToString(" · ").ifBlank { type.label },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(current?.let { formatCents(it.amountCents) } ?: "—", fontWeight = FontWeight.SemiBold)
                                change?.let { c ->
                                    Text(
                                        (if (c >= 0) "▲ " else "▼ ") + formatQuantity(kotlin.math.abs(c) * 100, 1) + " %",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (c > 0) BudgetColors.over else BudgetColors.ok,
                                    )
                                }
                            }
                        }
                        if (p.policyNumber.isNotBlank() || p.phone.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            PolicyContact(p)
                        }
                        if (reminder != null && !p.archived) {
                            Spacer(Modifier.height(8.dp))
                            val active = reminder.isActive(today)
                            Text(
                                (if (active) "🔔 " else "📅 ") + reminder.message(today),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (active) {
                                Spacer(Modifier.height(6.dp))
                                Button(onClick = { renewingId = p.id }, shape = CircleShape) { Text(tr("Apuntar renovación", "Log renewal")) }
                            }
                        }
                    }
                }
            }
        }
        if (policies.isNotEmpty()) AppFab(tr("Nuevo seguro", "New policy"), action = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (creating) PolicyEditor(null, emptyList(), vehicles, hvm, onDismiss = { creating = false })
    policies.firstOrNull { it.id == editingId }?.let { p ->
        PolicyEditor(p, Garage.priceHistory(p.id, prices), vehicles, hvm, onDismiss = { editingId = null }, onRenew = { editingId = null; renewingId = p.id })
    }
    policies.firstOrNull { it.id == renewingId }?.let { p ->
        RenewDialog(p, Garage.priceHistory(p.id, prices).lastOrNull(), onDismiss = { renewingId = null }) { cents, note ->
            hvm.renewPolicy(p, cents, note)
            renewingId = null
        }
    }
}

@Composable
private fun RenewDialog(policy: Policy, last: PolicyPrice?, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(last?.let { centsToInput(it.amountCents) } ?: "") }
    var note by rememberSaveable { mutableStateOf("") }
    val cents = parseUserAmount(amount)?.takeIf { it > 0 }
    val next = LocalDate.ofEpochDay(policy.renewalEpochDay).plusYears(1)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Renovar «${policy.name}»", "Renew “${policy.name}”")) },
        text = {
            Column {
                Text(
                    tr("Precio del año que empieza el ${LocalDate.ofEpochDay(policy.renewalEpochDay).format(longDate)}. El próximo vencimiento pasará al ${next.format(longDate)}.",
                        "Price for the year starting on ${LocalDate.ofEpochDay(policy.renewalEpochDay).format(longDate)}. The next renewal moves to ${next.format(longDate)}."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(12) },
                    label = { Text(tr("Precio nuevo (al año)", "New price (per year)")) },
                    suffix = { Text(com.midinero.control.domain.Currencies.symbol()) },
                    singleLine = true, isError = amount.isNotBlank() && cents == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                last?.let { l ->
                    cents?.let { c ->
                        val pct = (c - l.amountCents).toDouble() / l.amountCents * 100
                        Text(
                            tr("Antes: ${formatCents(l.amountCents)} (${if (pct >= 0) "+" else ""}${formatQuantity(pct, 1)} %)", "Before: ${formatCents(l.amountCents)} (${if (pct >= 0) "+" else ""}${formatQuantity(pct, 1)} %)"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(120) },
                    label = { Text(tr("Nota (opcional)", "Note (optional)")) },
                    placeholder = { Text(tr("Ej.: negociado, otra compañía…", "E.g. haggled, new company…")) },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(cents!!, note) }, enabled = cents != null) { Text(tr("Guardar", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicyEditor(
    policy: Policy?,
    history: List<PolicyPrice>,
    vehicles: List<Vehicle>,
    hvm: HouseholdViewModel,
    onDismiss: () -> Unit,
    onRenew: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(policy?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(policy?.type ?: PolicyType.CAR.name) }
    var company by rememberSaveable { mutableStateOf(policy?.company ?: "") }
    var vehicleId by rememberSaveable { mutableStateOf(policy?.vehicleId) }
    var renewal by rememberSaveable { mutableStateOf(policy?.renewalEpochDay ?: LocalDate.now().plusMonths(6).toEpochDay()) }
    var notice by rememberSaveable { mutableStateOf((policy?.noticeDays ?: 45).toString()) }
    var cancel by rememberSaveable { mutableStateOf((policy?.cancelDays ?: 30).toString()) }
    var note by rememberSaveable { mutableStateOf(policy?.note ?: "") }
    var policyNumber by rememberSaveable { mutableStateOf(policy?.policyNumber ?: "") }
    var phone by rememberSaveable { mutableStateOf(policy?.phone ?: "") }
    var newAssetName by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(policy?.archived ?: false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deletingPrice by remember { mutableStateOf<PolicyPrice?>(null) }
    val noticeDays = notice.toIntOrNull()?.takeIf { it in 0..365 }
    val cancelDays = cancel.toIntOrNull()?.takeIf { it in 0..365 }
    val priceCents = parseUserAmount(price)?.takeIf { it > 0 }

    EditorSheet(
        title = if (policy == null) tr("Nuevo seguro", "New policy") else tr("Editar seguro", "Edit policy"),
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = name.isNotBlank() && noticeDays != null && cancelDays != null && (price.isBlank() || priceCents != null),
        onPrimary = {
            hvm.savePolicy(
                (policy ?: Policy(name = "", renewalEpochDay = renewal)).copy(
                    name = name, type = type, company = company, vehicleId = if (type == PolicyType.CAR.name || type == PolicyType.HOME.name) vehicleId else null,
                    policyNumber = policyNumber.trim(), phone = phone.trim(),
                    renewalEpochDay = renewal, noticeDays = noticeDays ?: 45, cancelDays = cancelDays ?: 30, note = note.trim(), archived = archived,
                ),
                if (policy == null) priceCents else null,
            )
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PolicyType.entries.forEach { t ->
                FilterChip(selected = type == t.name, onClick = { type = t.name }, label = { Text("${t.emoji} ${t.label}") }, shape = CircleShape, colors = sheetChipColors(), border = null)
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(50) }, label = { Text(tr("Nombre", "Name")) },
            placeholder = { Text(tr("Ej.: Seguro del León, Hogar…", "E.g. León insurance, Home…")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = company, onValueChange = { company = it.take(50) }, label = { Text(tr("Compañía (opcional)", "Company (optional)")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = policyNumber, onValueChange = { policyNumber = it.take(40) }, label = { Text(tr("Nº de póliza", "Policy number")) },
                singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c in "+ ()-" }.take(20) }, label = { Text(tr("Teléfono", "Phone")) },
                placeholder = { Text(tr("Partes y asistencia", "Claims and assistance")) },
                singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }
        // El coche (o la vivienda) del seguro; si aún no está, se puede crear aquí mismo
        val home = type == PolicyType.HOME.name
        if (type == PolicyType.CAR.name || home) {
            FieldLabel(if (home) tr("Vivienda", "Home") else tr("Coche", "Car"))
            val assets = vehicles.filter { it.isHome == home && (!it.archived || it.id == vehicleId) }
            if (assets.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    assets.forEach { v ->
                        FilterChip(selected = vehicleId == v.id, onClick = { vehicleId = if (vehicleId == v.id) null else v.id }, label = { Text("${v.emoji} ${v.name}") }, shape = CircleShape, colors = sheetChipColors(), border = null)
                    }
                }
            }
            if (vehicleId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newAssetName, onValueChange = { newAssetName = it.take(40) },
                        label = { Text(if (home) tr("Añadir vivienda", "Add home") else tr("Añadir coche", "Add car")) },
                        placeholder = { Text(if (home) tr("Ej.: Piso", "E.g. Flat") else tr("Ej.: León", "E.g. León")) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    TextButton(
                        onClick = {
                            val v = if (home) Vehicle(name = newAssetName.trim(), emoji = "🏠", kind = Vehicle.KIND_HOME, oilReminderMonths = null)
                                else Vehicle(name = newAssetName.trim())
                            hvm.saveVehicle(v) { id -> vehicleId = id }
                            newAssetName = ""
                        },
                        enabled = newAssetName.isNotBlank(),
                    ) { Text(tr("Añadir", "Add")) }
                }
            }
        }
        FieldLabel(tr("Vencimiento", "Renewal date"))
        FilterChip(
            selected = false, onClick = { pickingDate = true },
            label = { Text(LocalDate.ofEpochDay(renewal).format(longDate)) },
            leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
            shape = CircleShape, colors = sheetChipColors(), border = null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = notice, onValueChange = { notice = it.filter(Char::isDigit).take(3) },
                label = { Text(tr("Avisar días antes", "Remind days before")) }, isError = noticeDays == null,
                singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = cancel, onValueChange = { cancel = it.filter(Char::isDigit).take(3) },
                label = { Text(tr("Plazo de baja (días)", "Notice to cancel (days)")) }, isError = cancelDays == null,
                singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Text(
            tr("Normalmente hay que avisar a la compañía un mes antes del vencimiento si no quieres renovar.", "You usually have to tell the company a month before the renewal date if you don't want to renew."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (policy == null) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = price, onValueChange = { price = it.take(12) },
                label = { Text(tr("Precio actual al año (opcional)", "Current yearly price (optional)")) },
                suffix = { Text(com.midinero.control.domain.Currencies.symbol()) },
                isError = price.isNotBlank() && priceCents == null, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        } else {
            FieldLabel(tr("Precios", "Prices"))
            if (history.isEmpty()) Text(tr("Aún sin precios.", "No prices yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            history.reversed().forEachIndexed { i, h ->
                val prev = history.getOrNull(history.size - 2 - i)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Desde el ", "From ") + LocalDate.ofEpochDay(h.startEpochDay).format(longDate))
                        val extra = listOfNotNull(
                            prev?.takeIf { it.amountCents > 0 }?.let { p ->
                                val pct = (h.amountCents - p.amountCents).toDouble() / p.amountCents * 100
                                (if (pct >= 0) "+" else "") + formatQuantity(pct, 1) + " %"
                            },
                            h.note.ifBlank { null },
                        )
                        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatCents(h.amountCents), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { deletingPrice = h }) { Text(tr("Quitar", "Remove")) }
                }
            }
            if (onRenew != null) TextButton(onClick = onRenew) { Text(tr("Apuntar renovación", "Log renewal")) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(200) }, label = { Text(tr("Nota (opcional)", "Note (optional)")) },
            placeholder = { Text(tr("Nº de póliza, teléfono…", "Policy number, phone…")) }, modifier = Modifier.fillMaxWidth(),
        )
        if (policy != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Archivado", "Archived"))
                    Text(tr("Ya no lo tienes: no avisa", "You no longer have it: no reminders"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = archived, onCheckedChange = { archived = it })
            }
            TextButton(onClick = { confirmDelete = true }) { Text(tr("Eliminar seguro", "Delete policy"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (pickingDate) {
        DatePickerModal(LocalDate.ofEpochDay(renewal), onDismiss = { pickingDate = false }) { d -> renewal = d.toEpochDay(); pickingDate = false }
    }
    deletingPrice?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingPrice = null },
            title = { Text(tr("¿Quitar el precio de ${formatCents(p.amountCents)}?", "Remove the ${formatCents(p.amountCents)} price?")) },
            confirmButton = { TextButton(onClick = { hvm.deletePolicyPrice(p); deletingPrice = null }) { Text(tr("Quitar", "Remove"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingPrice = null }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
    if (confirmDelete && policy != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar «${policy.name}»?", "Delete “${policy.name}”?")) },
            text = { Text(tr("Se borran también sus precios. Los pagos que apuntaste como gastos no se tocan.", "Its prices are deleted too. Payments you logged as expenses aren't touched.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; hvm.deletePolicy(policy); onDismiss() }) { Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

/**
 * Nº de póliza y teléfono a mano: al tocarlos se copian (para pegarlos al llamar o en un parte),
 * y el teléfono tiene botón para abrir el marcador.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicyContact(p: Policy) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun copy(label: String, value: String) {
        context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
        android.widget.Toast.makeText(context, tr("$label copiado", "$label copied"), android.widget.Toast.LENGTH_SHORT).show()
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (p.policyNumber.isNotBlank()) {
            FilterChip(selected = false, onClick = { copy(tr("Nº de póliza", "Policy number"), p.policyNumber) }, label = { Text("📄 ${p.policyNumber}") }, shape = CircleShape, colors = pillChipColors(), border = null)
        }
        if (p.phone.isNotBlank()) {
            FilterChip(selected = false, onClick = { copy(tr("Teléfono", "Phone"), p.phone) }, label = { Text("📞 ${p.phone}") }, shape = CircleShape, colors = pillChipColors(), border = null)
            FilterChip(
                selected = false,
                onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + p.phone.filter { it.isDigit() || it == '+' }))) }
                },
                label = { Text(tr("Llamar", "Call")) }, shape = CircleShape, colors = pillChipColors(), border = null,
            )
        }
    }
}

/** Para la tarjeta de Más: el seguro que antes renueva. */
fun nextPolicyLabel(policies: List<Policy>, today: LocalDate = LocalDate.now()): String? {
    val r = Garage.reminders(emptyList(), emptyList(), policies).filter { it.kind == ReminderKind.POLICY && it.daysLeft(today) >= 0 }.minByOrNull { it.dueDate } ?: return null
    val days = r.daysLeft(today)
    return if (days == 0L) tr("${r.title} renueva hoy", "${r.title} renews today") else tr("${r.title} renueva en $days días", "${r.title} renews in $days days")
}
