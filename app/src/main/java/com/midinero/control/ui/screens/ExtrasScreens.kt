package com.midinero.control.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.data.Account
import com.midinero.control.data.Profile
import com.midinero.control.data.Profiles
import com.midinero.control.domain.PaymentApps
import com.midinero.control.domain.PaymentMethods
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.shortLabel
import com.midinero.control.domain.signedAmount
import com.midinero.control.domain.tr
import com.midinero.control.ui.ExtrasViewModel

private val methodColor = Color(0xFF0C8599)
private val profileColor = Color(0xFF7048E8)

/** Ajustes → «Activar más funciones»: métodos de pago y perfiles (apagados al principio). */
@Composable
internal fun ExtraFeaturesSection(onOpenPaymentMethods: () -> Unit, onOpenProfiles: () -> Unit, vm: ExtrasViewModel = viewModel()) {
    val features by vm.features.collectAsStateWithLifecycle()
    var leavingProfiles by rememberSaveable { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            SettingsTitle(Icons.Filled.Extension, Color(0xFF2B7BC4), tr("Activar más funciones", "Turn on more features"), Modifier.padding(horizontal = 16.dp))
            Text(
                tr("Están apagadas para que la app sea sencilla. Al apagarlas no se borra nada.", "They're off to keep the app simple. Turning them off deletes nothing."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SwitchRow(
                tr("Métodos de pago", "Payment methods"),
                tr("Elige con qué pagas cada gasto: «Crédito BBVA», «Efectivo», «Transferencia HSBC»…", "Choose what you paid each expense with: “BBVA credit card”, “Cash”, “HSBC transfer”…"),
                features.paymentMethods,
            ) { vm.setFeatures(features.copy(paymentMethods = it)) }
            if (features.paymentMethods) {
                MenuRow(Icons.Filled.Payments, methodColor, tr("Tus métodos de pago", "Your payment methods"), tr("Crear, cambiar y ver lo gastado con cada uno", "Create, edit and see what you spent with each"), onClick = onOpenPaymentMethods)
            }
            SwitchRow(
                tr("Perfiles", "Profiles"),
                tr("Lleva aparte otras cuentas (tu negocio, la casa…), cada una con sus gastos, categorías y ajustes", "Keep other books apart (your business, the house…), each with its own expenses, categories and settings"),
                features.profiles,
            ) { on ->
                if (!on && Profiles.current != Profiles.MAIN) leavingProfiles = true
                else vm.setFeatures(features.copy(profiles = on))
            }
            if (features.profiles) {
                MenuRow(Icons.Filled.AccountCircle, profileColor, tr("Tus perfiles", "Your profiles"), tr("Crear, cambiar de perfil o borrarlos", "Create, switch or delete profiles"), onClick = onOpenProfiles)
            }
        }
    }
    if (leavingProfiles) {
        AlertDialog(
            onDismissRequest = { leavingProfiles = false },
            title = { Text(tr("Apagar los perfiles", "Turn off profiles")) },
            text = { Text(tr("Volverás a tu perfil principal. Los demás perfiles y sus datos se guardan por si los vuelves a activar.", "You'll go back to your main profile. Other profiles and their data are kept in case you turn them on again.")) },
            confirmButton = {
                TextButton(onClick = {
                    leavingProfiles = false
                    vm.setFeatures(features.copy(profiles = false))
                    vm.switchTo(Profiles.MAIN)
                }) { Text(tr("Apagar y volver", "Turn off and go back")) }
            },
            dismissButton = { TextButton(onClick = { leavingProfiles = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

/** Métodos de pago con lo gastado con cada uno en el mes. */
@Composable
fun PaymentMethodsScreen(onBack: () -> Unit, vm: ExtrasViewModel = viewModel()) {
    val methods by vm.methods.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val expenses by vm.monthExpenses.collectAsStateWithLifecycle()
    val prefsVersion by vm.prefs.changes.collectAsStateWithLifecycle()
    val defaultId = remember(prefsVersion) { vm.prefs.defaultPaymentMethod }
    val byApp = remember(prefsVersion) { vm.prefs.paymentMethodApps }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    val totals = remember(expenses) { PaymentMethods.totals(expenses).associateBy { it.methodId } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader(tr("Métodos de pago", "Payment methods"), subtitle = tr("Con qué pagas cada gasto", "What you pay each expense with"), onBack = onBack) }
            item { MonthSwitcher(month, vm::previousMonth, vm::nextMonth) }
            if (methods.isEmpty()) item {
                EmptyState(
                    "💳", tr("Crea tus métodos de pago", "Create your payment methods"),
                    tr("Por ejemplo «Crédito BBVA», «Débito Santander», «Efectivo» o «Transferencia HSBC». Luego los eliges al apuntar cada gasto.", "For example “BBVA credit card”, “Santander debit card”, “Cash” or “HSBC transfer”. Then pick one when you log each expense."),
                    action = tr("Añadir método", "Add method"), onAction = { creating = true },
                )
            }
            items(methods, key = { it.id }) { m ->
                val t = totals[m.id]
                val apps = byApp.filterValues { it == m.id }.keys.map { PaymentApps.nameOf(it) }
                AppCard(Modifier.fillMaxWidth(), onClick = { editingId = m.id }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.emoji, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val extra = listOfNotNull(
                                    if (m.id == defaultId) tr("Por defecto", "Default") else null,
                                    apps.takeIf { it.isNotEmpty() }?.let { tr("Avisos de ", "Alerts from ") + it.joinToString(", ") },
                                    if (m.archived) tr("Archivado", "Archived") else null,
                                )
                                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatCents(t?.cents ?: 0), fontWeight = FontWeight.SemiBold)
                                Text(
                                    if ((t?.count ?: 0) == 1) tr("1 gasto", "1 expense") else tr("${t?.count ?: 0} gastos", "${t?.count ?: 0} expenses"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if ((t?.count ?: 0) > 0) {
                            TextButton(onClick = { expanded = if (expanded == m.id) null else m.id }) {
                                Text(if (expanded == m.id) tr("Ocultar gastos", "Hide expenses") else tr("Ver gastos", "Show expenses"))
                            }
                            if (expanded == m.id) {
                                expenses.filter { it.accountId == m.id && !it.isIncome }.forEach { e ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("${dateOf(e.timestamp).shortLabel()} · ${e.merchant}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                        Text(signedAmount(e), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            totals[null]?.takeIf { methods.isNotEmpty() }?.let { none ->
                item {
                    Text(
                        tr("Sin método: ${formatCents(none.cents)} en ${none.count} gastos", "No method: ${formatCents(none.cents)} in ${none.count} expenses"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        if (methods.isNotEmpty()) AppFab(tr("Nuevo método", "New method"), action = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (creating) MethodEditor(null, false, emptySet(), vm, onDismiss = { creating = false })
    methods.firstOrNull { it.id == editingId }?.let { m ->
        MethodEditor(m, m.id == defaultId, byApp.filterValues { it == m.id }.keys, vm, onDismiss = { editingId = null })
    }
}

private val methodEmojis = listOf("💳", "🏦", "💶", "💵", "📲", "🔁", "🪙", "🧾", "💼", "🎁")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MethodEditor(method: Account?, isDefault: Boolean, apps: Set<String>, vm: ExtrasViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(method?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(method?.emoji ?: "💳") }
    var asDefault by rememberSaveable { mutableStateOf(isDefault) }
    var chosenApps by rememberSaveable { mutableStateOf(apps.toList()) }
    var archived by rememberSaveable { mutableStateOf(method?.archived ?: false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val enabledApps = remember { vm.prefs.enabledPaymentApps }
    val appChoices = PaymentApps.all.filter { it.packageName in enabledApps || it.packageName in apps }
    EditorSheet(
        title = if (method == null) tr("Nuevo método de pago", "New payment method") else tr("Editar método de pago", "Edit payment method"),
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = name.isNotBlank(),
        onPrimary = { vm.saveMethod((method ?: Account(name = "")).copy(name = name, emoji = emoji, archived = archived), asDefault && !archived, chosenApps.toSet()) },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            methodEmojis.forEach { e ->
                FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) }, shape = CircleShape, colors = sheetChipColors(), border = null)
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) },
            label = { Text(tr("Nombre", "Name")) },
            placeholder = { Text(tr("Ej.: Crédito BBVA, Efectivo…", "E.g. BBVA credit card, Cash…")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Por defecto", "Default"))
                Text(tr("Se elige solo al apuntar un gasto nuevo", "Picked automatically for new expenses"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = asDefault, onCheckedChange = { asDefault = it })
        }
        if (appChoices.isNotEmpty()) {
            FieldLabel(tr("Pagos detectados de…", "Detected payments from…"))
            Text(
                tr("Los pagos que se apunten solos desde estas apps llevarán este método.", "Payments logged automatically from these apps get this method."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                appChoices.forEach { a ->
                    val on = a.packageName in chosenApps
                    FilterChip(
                        selected = on, onClick = { chosenApps = if (on) chosenApps - a.packageName else chosenApps + a.packageName },
                        label = { Text(a.name) }, shape = CircleShape, colors = sheetChipColors(), border = null,
                    )
                }
            }
        }
        if (method != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Archivado", "Archived"))
                    Text(tr("Ya no lo usas: no se ofrece al apuntar, pero sus gastos lo conservan", "You no longer use it: not offered when logging, but its expenses keep it"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = archived, onCheckedChange = { archived = it })
            }
            TextButton(onClick = { confirmDelete = true }) { Text(tr("Eliminar método", "Delete method"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete && method != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar «${method.name}»?", "Delete “${method.name}”?")) },
            text = { Text(tr("Sus gastos no se borran: solo se quedan sin método de pago.", "Its expenses aren't deleted: they just lose their payment method.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteMethod(method); onDismiss() }) { Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

/** Perfiles: cada uno con sus datos aparte. Cambiar de perfil reinicia la app. */
@Composable
fun ProfilesScreen(onBack: () -> Unit, vm: ExtrasViewModel = viewModel()) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var switchingId by rememberSaveable { mutableStateOf<Int?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    tr("Perfiles", "Profiles"),
                    subtitle = tr("Cada perfil lleva sus gastos, categorías, presupuesto, ajustes y copias por separado", "Each profile keeps its own expenses, categories, budget, settings and backups"),
                    onBack = onBack,
                )
            }
            items(profiles, key = { it.id }) { p ->
                val active = p.id == vm.currentProfile
                AppCard(Modifier.fillMaxWidth(), onClick = { if (!active) switchingId = p.id }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(if (p.id == Profiles.MAIN) tr("Principal", "Main") else null, if (active) tr("Abierto ahora", "Open now") else tr("Toca para abrirlo", "Tap to open it")).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { editingId = p.id }) { Text(tr("Editar", "Edit")) }
                    }
                }
            }
            item {
                Text(
                    tr(
                        "Los pagos detectados y los cargos fijos se apuntan en el perfil abierto. En cada perfil eliges sus apps de pago en Ajustes → Detección; las copias automáticas de cada perfil se hacen aunque no esté abierto.",
                        "Detected payments and recurring charges go to the open profile. In each profile choose its payment apps in Settings → Detection; each profile's automatic backups run even when it isn't open.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        AppFab(tr("Nuevo perfil", "New profile"), action = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (creating) ProfileEditor(null, onDismiss = { creating = false }, onSave = { name, emoji ->
        creating = false
        switchingId = vm.createProfile(name, emoji).id
    }, onDelete = null)
    profiles.firstOrNull { it.id == editingId }?.let { p ->
        ProfileEditor(
            p, onDismiss = { editingId = null },
            onSave = { name, emoji -> vm.renameProfile(p.copy(name = name.trim(), emoji = emoji)); editingId = null },
            onDelete = if (p.id == Profiles.MAIN || p.id == vm.currentProfile) null else ({ vm.deleteProfile(p.id); editingId = null }),
        )
    }
    profiles.firstOrNull { it.id == switchingId }?.let { p ->
        AlertDialog(
            onDismissRequest = { switchingId = null },
            title = { Text(tr("¿Abrir «${p.name}»?", "Open “${p.name}”?")) },
            text = { Text(tr("La app se reiniciará con los datos de ese perfil. No se pierde nada del actual.", "The app will restart with that profile's data. Nothing from the current one is lost.")) },
            confirmButton = { TextButton(onClick = { switchingId = null; vm.switchTo(p.id) }) { Text(tr("Abrir", "Open")) } },
            dismissButton = { TextButton(onClick = { switchingId = null }) { Text(tr("Ahora no", "Not now")) } },
        )
    }
}

private val profileEmojis = listOf("👤", "💼", "🏪", "🏠", "👨‍👩‍👧", "🛠️", "🚚", "💇", "🍽️", "🏗️")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(profile: Profile?, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(profile?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(profile?.emoji ?: "💼") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteText by rememberSaveable { mutableStateOf("") }
    EditorSheet(
        title = if (profile == null) tr("Nuevo perfil", "New profile") else tr("Editar perfil", "Edit profile"),
        onDismiss = onDismiss,
        primaryText = if (profile == null) tr("Crear", "Create") else tr("Guardar", "Save"),
        primaryEnabled = name.isNotBlank(),
        onPrimary = { onSave(name, emoji) },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            profileEmojis.forEach { e ->
                FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) }, shape = CircleShape, colors = sheetChipColors(), border = null)
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) },
            label = { Text(tr("Nombre", "Name")) },
            placeholder = { Text(tr("Ej.: Negocio, Casa…", "E.g. Business, Home…")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (profile == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Empieza vacío, con las categorías de serie. Se lleva tu idioma, tema, moneda y bloqueo.", "It starts empty, with the default categories. It keeps your language, theme, currency and lock."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDelete = true }) { Text(tr("Borrar perfil", "Delete profile"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete && onDelete != null && profile != null) {
        val word = tr("BORRAR", "DELETE")
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Borrar «${profile.name}» y todos sus datos?", "Delete “${profile.name}” and all its data?")) },
            text = {
                Column {
                    Text(tr("Se borran sus gastos, categorías, ajustes y tickets de este móvil. No se puede deshacer: si quieres conservarlos, haz antes una copia desde ese perfil. Escribe $word para confirmar.", "Its expenses, categories, settings and receipts are deleted from this phone. This can't be undone: to keep them, make a backup from that profile first. Type $word to confirm."))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = deleteText, onValueChange = { deleteText = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = deleteText.trim().equals(word, ignoreCase = true)) {
                    Text(tr("Borrar", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

/** En Más: el perfil abierto y un toque para cambiar a otro. */
@Composable
fun ProfileSwitcherCard(onManage: () -> Unit, modifier: Modifier = Modifier, vm: ExtrasViewModel = viewModel()) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var choosing by rememberSaveable { mutableStateOf(false) }
    val current = profiles.firstOrNull { it.id == vm.currentProfile } ?: return
    AppCard(modifier.fillMaxWidth(), onClick = { choosing = true }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(current.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Perfil", "Profile"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(tr("Cambiar", "Switch"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(tr("Cambiar de perfil", "Switch profile")) },
            text = {
                Column {
                    profiles.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { choosing = false; vm.switchTo(p.id) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.emoji)
                            Spacer(Modifier.width(12.dp))
                            Text(p.name, Modifier.weight(1f), fontWeight = if (p.id == current.id) FontWeight.Bold else FontWeight.Normal)
                            if (p.id == current.id) Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Text(tr("La app se reinicia con los datos del perfil elegido.", "The app restarts with the chosen profile's data."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false; onManage() }) { Text(tr("Gestionar perfiles", "Manage profiles")) } },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(tr("Cerrar", "Close")) } },
        )
    }
}
