package com.midinero.control.ui.screens

import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.ui.GoalsViewModel
import com.midinero.control.ui.DebtsViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.BuildConfig
import com.midinero.control.domain.MerchantCatalog
import com.midinero.control.domain.Recurrence
import com.midinero.control.domain.Split
import com.midinero.control.domain.TripStats
import com.midinero.control.domain.TripStatus
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.tr
import com.midinero.control.ui.MainViewModel
import java.time.LocalDate

/** Colores de cada sección, los mismos en Más, Inicio y sus pantallas. */
object SectionColors {
    val recurring = Color(0xFF7C5CFA)
    val limits = Color(0xFFF08C00)
    val goals = Color(0xFF1FA971)
    val trips = Color(0xFF2B7BC4)
    val debts = Color(0xFFE64980)
    val rules = Color(0xFF0E8A6D)
    val import = Color(0xFF5C7CFA)
    val merchants = Color(0xFFD9480F)
    val settings = Color(0xFF5E6A70)
    val help = Color(0xFF1098AD)
    val categories = Color(0xFFAE3EC9)
}

/** Todo lo que no cabe en la barra de abajo, en tarjetas con su estado al día. */
@Composable
fun MoreScreen(
    vm: MainViewModel,
    onOpenRecurring: () -> Unit,
    onOpenLimits: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMerchants: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    goalsVm: GoalsViewModel = viewModel(),
    debtsVm: DebtsViewModel = viewModel(),
) {
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    val goals by goalsVm.goals.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val debts by debtsVm.debts.collectAsStateWithLifecycle()
    val rules by vm.customRules.collectAsStateWithLifecycle()
    val prefsVersion by vm.prefs.changes.collectAsStateWithLifecycle()
    val budget = remember(prefsVersion) { vm.prefs.monthlyBudgetCents }
    var showMerchants by rememberSaveable { mutableStateOf(false) }
    val entrance = rememberEntrance()

    val activeRecurring = recurring.filter { it.active && !it.isIncome }
    val limits = categories.count { (it.monthlyLimitCents ?: 0L) > 0 }
    val activeGoals = goals.filter { !it.goal.archived }
    val today = LocalDate.now()
    val liveTrips = trips.count { TripStats.status(it, today) != TripStatus.FINISHED }
    val balances = remember(debts) { Split.balances(debts) }
    val owed = balances.filter { it.balanceCents > 0 }.sumOf { it.balanceCents }
    val owe = -balances.filter { it.balanceCents < 0 }.sumOf { it.balanceCents }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    ScrollToTopOnReselect("more", listState)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(tr("Más", "More"), subtitle = tr("Planifica, organiza y ajusta MiDinero", "Plan, organise and tune MiDinero")) }

        item {
            // Buscador de acceso rápido
            Row(
                Modifier
                    .entrance(entrance, 0)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                    .clickable(onClick = onOpenSearch)
                    .semantics { role = Role.Button }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    tr("Buscar en todos tus movimientos", "Search all your transactions"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item { SectionTitle(tr("Planifica", "Plan"), Modifier.entrance(entrance, 1)) }
        item {
            Row(Modifier.entrance(entrance, 2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubTile(
                    Icons.Filled.Repeat, SectionColors.recurring, tr("Cargos fijos", "Recurring"),
                    if (activeRecurring.isEmpty()) tr("Alquiler, suscripciones…", "Rent, subscriptions…")
                    else "${activeRecurring.size} · " + formatCents(activeRecurring.sumOf { Recurrence.monthlyEquivalentCents(it) }) + tr("/mes", "/month"),
                    onOpenRecurring, Modifier.weight(1f),
                )
                HubTile(
                    Icons.Filled.Speed, SectionColors.limits, tr("Límites", "Limits"),
                    when {
                        budget > 0 -> tr("Presupuesto ${formatCents(budget)}", "Budget ${formatCents(budget)}")
                        limits > 0 -> if (limits == 1) tr("1 categoría", "1 category") else tr("$limits categorías", "$limits categories")
                        else -> tr("Pon un tope al mes", "Set a monthly cap")
                    },
                    onOpenLimits, Modifier.weight(1f),
                )
            }
        }
        item {
            Row(Modifier.entrance(entrance, 3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubTile(
                    Icons.Filled.EmojiEvents, SectionColors.goals, tr("Metas de ahorro", "Savings goals"),
                    if (activeGoals.isEmpty()) tr("Aparta para algo", "Save for something")
                    else tr("${formatCents(activeGoals.sumOf { it.progress.savedCents })} ahorrado", "${formatCents(activeGoals.sumOf { it.progress.savedCents })} saved"),
                    onOpenGoals, Modifier.weight(1f),
                )
                HubTile(
                    Icons.Filled.FlightTakeoff, SectionColors.trips, tr("Viajes y eventos", "Trips and events"),
                    if (liveTrips == 0) tr("Con su presupuesto", "With their own budget")
                    else if (liveTrips == 1) tr("1 en marcha", "1 active") else tr("$liveTrips en marcha", "$liveTrips active"),
                    onOpenTrips, Modifier.weight(1f),
                )
            }
        }
        item {
            MenuGroup(null, Modifier.entrance(entrance, 4)) {
                MenuRow(
                    Icons.Filled.Groups, SectionColors.debts, tr("Cuentas con amigos", "Split with friends"),
                    when {
                        owed > 0 && owe > 0 -> tr("Te deben ${formatCents(owed)} · debes ${formatCents(owe)}", "You're owed ${formatCents(owed)} · you owe ${formatCents(owe)}")
                        owed > 0 -> tr("Te deben ${formatCents(owed)}", "You're owed ${formatCents(owed)}")
                        owe > 0 -> tr("Debes ${formatCents(owe)}", "You owe ${formatCents(owe)}")
                        else -> tr("Gastos compartidos y quién debe qué", "Shared expenses and who owes what")
                    },
                    onClick = onOpenDebts,
                )
            }
        }

        item { SectionTitle(tr("Organiza", "Organise"), Modifier.entrance(entrance, 5)) }
        item {
            MenuGroup(null, Modifier.entrance(entrance, 6)) {
                MenuRow(
                    Icons.Filled.Category, SectionColors.categories, tr("Categorías", "Categories"),
                    tr("Crea las tuyas, ordénalas u oculta las que no uses", "Create your own, reorder them or hide the ones you don't use"),
                    onClick = onOpenCategories,
                )
                MenuRow(
                    Icons.AutoMirrored.Filled.Rule, SectionColors.rules, tr("Tus reglas", "Your rules"),
                    if (rules.isEmpty()) tr("«Si contiene… ponle esta categoría»", "“If it contains… use this category”")
                    else if (rules.size == 1) tr("1 regla", "1 rule") else tr("${rules.size} reglas", "${rules.size} rules"),
                    onClick = onOpenRules, divider = true,
                )
                MenuRow(
                    Icons.Filled.Leaderboard, SectionColors.merchants, tr("Tus comercios", "Your merchants"),
                    tr("Dónde gastas más, cuántas veces y el ticket medio", "Where you spend most, how often and the average ticket"),
                    onClick = onOpenMerchants, divider = true,
                )
                MenuRow(
                    Icons.Filled.Storefront, SectionColors.merchants, tr("Comercios que ya conoce", "Merchants it already knows"),
                    tr("${MerchantCatalog.all.size} con su categoría · añade los tuyos con foto", "${MerchantCatalog.all.size} with their category · add yours with a photo"),
                    onClick = { showMerchants = true }, divider = true,
                )
                MenuRow(
                    Icons.Filled.UploadFile, SectionColors.import, tr("Importar del banco", "Import from your bank"),
                    tr("Movimientos desde un CSV", "Transactions from a CSV"),
                    onClick = onOpenImport, divider = true,
                )
            }
        }

        item { SectionTitle(tr("MiDinero", "MiDinero"), Modifier.entrance(entrance, 7)) }
        item {
            MenuGroup(null, Modifier.entrance(entrance, 8)) {
                MenuRow(
                    Icons.Filled.Settings, SectionColors.settings, tr("Ajustes", "Settings"),
                    tr("Detección, avisos, privacidad y copias", "Detection, alerts, privacy and backups"),
                    onClick = onOpenSettings,
                )
                MenuRow(
                    Icons.AutoMirrored.Filled.HelpOutline, SectionColors.help, tr("Ayuda", "Help"),
                    tr("Preguntas frecuentes", "Frequently asked questions"),
                    onClick = onOpenHelp, divider = true,
                )
            }
        }
        item {
            Text(
                tr("MiDinero ${BuildConfig.VERSION_NAME} · tus datos solo en tu móvil", "MiDinero ${BuildConfig.VERSION_NAME} · your data only on your phone"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (showMerchants) KnownMerchantsSheet(vm, onDismiss = { showMerchants = false })
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(start = 4.dp, top = 8.dp).asHeading(),
    )
}

/** Tarjeta cuadrada del hub: icono de color, título y su estado. */
@Composable
private fun HubTile(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    AppCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            IconBadge(icon, color, size = 44.dp, solid = true)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
