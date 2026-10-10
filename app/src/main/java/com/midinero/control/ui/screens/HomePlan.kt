package com.midinero.control.ui.screens

import androidx.compose.material.icons.filled.AutoAwesome
import com.midinero.control.domain.endMillis
import com.midinero.control.domain.startMillis
import com.midinero.control.ui.theme.Ink
import com.midinero.control.ui.theme.Lime
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Bolt
import kotlin.math.abs
import com.midinero.control.ui.theme.parseHexColor
import com.midinero.control.ui.theme.MoneyStyle
import com.midinero.control.ui.theme.AppBrushes
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.Lang
import java.time.YearMonth
import com.midinero.control.domain.MonthStats
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.midinero.control.domain.tr
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import com.midinero.control.domain.TripStats
import com.midinero.control.domain.TripStatus
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.midinero.control.domain.BudgetPace
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.midinero.control.ui.PrivacyState
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.domain.Recurrence
import com.midinero.control.domain.formatCents
import com.midinero.control.service.PaymentNotificationListener
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.BudgetColors

// Tarjetas de «Tu plan» en Inicio: cargos fijos, metas, viajes y cuentas con amigos.

@Composable
internal fun RecurringPlanCard(activeRecurring: List<com.midinero.control.data.RecurringExpense>, categoryById: Map<Long, Category>, onOpenRecurring: () -> Unit) {
    val upcoming = activeRecurring.sortedBy { it.nextDueEpochDay }.take(3)
    AppCard(onClick = onOpenRecurring, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Repeat, SectionColors.recurring, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Cargos fijos", "Recurring"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    formatCents(activeRecurring.sumOf { Recurrence.monthlyEquivalentCents(it) }) + tr("/mes", "/month"),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (upcoming.isEmpty()) {
                Text(
                    tr("Añade el alquiler, Netflix, Spotify… y se apuntarán solos cada mes.", "Add your rent, Netflix, Spotify… and they'll be logged automatically every month."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    tr("Próximos cargos", "Upcoming charges"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                upcoming.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MerchantBadge(r.name, r.categoryId?.let { categoryById[it] }, size = 34)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Recurrence.nextLabel(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(formatCents(r.amountCents), style = MaterialTheme.typography.titleSmall.merge(MoneyStyle))
                    }
                }
            }
        }
    }

}

@Composable
internal fun GoalsPlanCard(goals: List<com.midinero.control.ui.GoalWithProgress>, onOpenGoals: () -> Unit) {
    val activeGoals = goals.filter { !it.goal.archived }
    AppCard(onClick = onOpenGoals, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.EmojiEvents, SectionColors.goals, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Metas de ahorro", "Savings goals"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (activeGoals.isNotEmpty()) {
                    Text(formatCents(activeGoals.sumOf { it.progress.savedCents }), fontWeight = FontWeight.SemiBold)
                }
            }
            if (activeGoals.isEmpty()) {
                Text(
                    tr("Aparta dinero para un viaje, el coche o un colchón de emergencias.", "Put money aside for a trip, a car or an emergency fund."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                activeGoals.take(3).forEach { g ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${g.goal.emoji} ${g.goal.name}", modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${(g.progress.ratio * 100).toInt().coerceAtMost(100)} %",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RankBar(g.progress.ratio, MaterialTheme.colorScheme.primary, Modifier.padding(top = 4.dp))
                }
            }
        }
    }

}

@Composable
internal fun TripsPlanCard(trips: List<com.midinero.control.data.Trip>, tripTotals: Map<Long, Long>, onOpenTrips: () -> Unit, onOpenTrip: (Long) -> Unit) {
    val today = LocalDate.now()
    val current = trips.filter { TripStats.status(it, today) != TripStatus.FINISHED }
        .sortedBy { it.startDay }
        .take(2)
    AppCard(onClick = onOpenTrips, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.FlightTakeoff, SectionColors.trips, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Viajes y eventos", "Trips and events"), fontWeight = FontWeight.SemiBold)
            }
            if (current.isEmpty()) {
                Text(
                    tr("¿Vacaciones o una boda a la vista? Dales su propio presupuesto.", "Holidays or a wedding coming up? Give them their own budget."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            current.forEach { t ->
                val spent = tripTotals[t.id] ?: 0L
                val budget = t.budgetCents
                Row(
                    Modifier.padding(top = 6.dp).clickable { onOpenTrip(t.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${t.emoji} ${t.name}", maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (TripStats.status(t, today) == TripStatus.UPCOMING) tr("Empieza el ${LocalDate.ofEpochDay(t.startDay).shortLabel()}", "Starts on ${LocalDate.ofEpochDay(t.startDay).shortLabel()}") else tr("En curso", "In progress"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatCents(spent) + (budget?.let { " / ${formatCents(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (budget != null && budget > 0) {
                    val ratio = spent.toFloat() / budget
                    RankBar(ratio, budgetColor(ratio), Modifier.padding(top = 4.dp))
                }
            }
        }
    }

}

@Composable
internal fun DebtsPlanCard(debtBalances: List<com.midinero.control.domain.PersonBalance>, onOpenDebts: () -> Unit) {
    val owed = debtBalances.filter { it.balanceCents > 0 }.sumOf { it.balanceCents }
    val owe = -debtBalances.filter { it.balanceCents < 0 }.sumOf { it.balanceCents }
    AppCard(onClick = onOpenDebts, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Groups, SectionColors.debts, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Cuentas con amigos", "Split with friends"), fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        if (owed > 0) tr("Te deben ${formatCents(owed)}", "You're owed ${formatCents(owed)}") else null,
                        if (owe > 0) tr("debes ${formatCents(owe)}", "you owe ${formatCents(owe)}") else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
