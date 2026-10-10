package com.midinero.control.ui.screens

import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.graphics.Color
import com.midinero.control.domain.MerchantCatalog
import com.midinero.control.domain.tr
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.BuildConfig
import com.midinero.control.data.BackupData
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.midinero.control.domain.PaymentApps
import com.midinero.control.domain.formatCents
import com.midinero.control.service.PaymentNotificationListener
import com.midinero.control.ui.AppLock
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.Language
import androidx.fragment.app.FragmentActivity
import com.midinero.control.ui.theme.BudgetColors

// Piezas de Ajustes: filas con interruptor, diálogos y títulos de sección.

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var day by rememberSaveable { mutableIntStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("¿Qué día empieza tu mes?", "Which day does your month start?")) },
        text = {
            Column {
                Text(
                    tr("Si cobras a final de mes, elige ese día y tus meses irán de nómina a nómina. ", "If you're paid at the end of the month, choose that day and your months will run from payday to payday. ") +
                        tr("Por ejemplo, con el día 25, «septiembre» va del 25 de agosto al 24 de septiembre.", "For example, with day 25, “September” runs from 25 August to 24 September."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..28).forEach { d ->
                        FilterChip(selected = d == day, onClick = { day = d }, label = { Text("$d") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(day) }) { Text(tr("Guardar", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

@Composable
internal fun LearnedRulesDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val byId = categories.associateBy { it.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Comercios aprendidos", "Learned merchants")) },
        text = {
            if (rules.isEmpty()) {
                Text(tr("No hay comercios aprendidos.", "No learned merchants."))
            } else {
                LazyColumn {
                    items(rules, key = { it.merchantKey }) { rule ->
                        val cat = byId[rule.categoryId]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.merchantKey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    cat?.let { "${it.emoji} ${it.name}" } ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { vm.deleteRule(rule) }) {
                                Icon(Icons.Filled.Delete, contentDescription = tr("Olvidar ${rule.merchantKey}", "Forget ${rule.merchantKey}"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cerrar", "Close")) } },
    )
}

@Composable
internal fun SettingCard(
    title: String,
    ok: Boolean,
    okText: String,
    koText: String,
    description: String,
    actions: @Composable () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Bolt, Color(0xFF0E8A6D), size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (ok) BudgetColors.ok else BudgetColors.warning,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (ok) okText else koText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ok) BudgetColors.ok else BudgetColors.warning,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            actions()
        }
    }
}

/** Título de un grupo de ajustes, con su icono de color. */
@Composable
internal fun SettingsTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
    }
}
