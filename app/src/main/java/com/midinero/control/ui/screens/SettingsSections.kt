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

// Secciones de Ajustes que se pueden pintar por separado.

/** Tarjeta «Privacidad y aspecto»: bloqueo, importes ocultos, pantalla privada, tema, idioma y día de inicio del mes. */
@Composable
internal fun AppearanceSection(vm: MainViewModel, onPickPeriod: () -> Unit) {
    val context = LocalContext.current
    val prefsVersion by vm.prefs.changes.collectAsStateWithLifecycle()
    val appLock = remember(prefsVersion) { vm.prefs.appLock }
    val hideAmounts = remember(prefsVersion) { vm.prefs.hideAmounts }
    val secureScreen = remember(prefsVersion) { vm.prefs.secureScreen }
    val themeMode = remember(prefsVersion) { vm.prefs.themeMode }
    val periodStartDay = remember(prefsVersion) { vm.prefs.periodStartDay }
    val lockAvailable = remember { AppLock.isAvailable(context) }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            SettingsTitle(Icons.Filled.Palette, Color(0xFF7C5CFA), tr("Privacidad y aspecto", "Privacy and appearance"), Modifier.padding(horizontal = 16.dp))
            SwitchRow(
                tr("Bloquear con huella o PIN", "Lock with fingerprint or PIN"),
                if (lockAvailable) tr("Se pide al abrir la app y tras 30 s en segundo plano", "Asked when opening the app and after 30 s in the background")
                else tr("Configura un bloqueo de pantalla en el móvil para usarlo", "Set up a screen lock on your phone to use it"),
                checked = appLock && lockAvailable,
                enabled = lockAvailable,
            ) { on ->
                if (!on) vm.prefs.appLock = false
                else (context as? FragmentActivity)?.let { act ->
                    AppLock.authenticate(act) { ok -> if (ok) vm.prefs.appLock = true }
                }
            }
            SwitchRow(tr("Ocultar importes", "Hide amounts"), tr("Muestra •••• en lugar de las cantidades (también en los avisos)", "Shows •••• instead of amounts (in notifications too)"), hideAmounts) {
                vm.prefs.hideAmounts = it
            }
            SwitchRow(tr("Pantalla privada", "Private screen"), tr("Oculta la app en «recientes» e impide capturas de pantalla", "Hides the app in “recents” and blocks screenshots"), secureScreen) {
                vm.prefs.secureScreen = it
            }
            Text(
                tr("Tema", "Theme"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            val themes = listOf("system" to tr("Sistema", "System"), "light" to tr("Claro", "Light"), "dark" to tr("Oscuro", "Dark"))
            SegmentedTabs(
                themes.map { it.second },
                selected = themes.indexOfFirst { it.first == themeMode }.coerceAtLeast(0),
                onSelect = { vm.prefs.themeMode = themes[it].first },
                modifier = Modifier.padding(horizontal = 16.dp),
                track = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Text(
                tr("Idioma", "Language"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            val language = remember(prefsVersion) { vm.prefs.language }
            val activity = androidx.activity.compose.LocalActivity.current
            val languages = listOf("system" to tr("Automático", "Automatic"), "es" to "Español", "en" to "English")
            SegmentedTabs(
                languages.map { it.second },
                selected = languages.indexOfFirst { it.first == language }.coerceAtLeast(0),
                onSelect = { i ->
                    val value = languages[i].first
                    if (language != value && activity != null) Language.change(activity, value)
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                track = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Text(
                tr("Moneda", "Currency"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            val mainCurrency = remember(prefsVersion) { vm.prefs.mainCurrency }
            var pendingCurrency by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
            val currencies = listOf(
                com.midinero.control.domain.Currencies.EUR to tr("Euros €", "Euros €"),
                com.midinero.control.domain.Currencies.USD to tr("Dólares $", "Dollars $"),
                com.midinero.control.domain.Currencies.MXN to tr("Pesos MX$", "Pesos MX$"),
            )
            SegmentedTabs(
                currencies.map { it.second },
                selected = currencies.indexOfFirst { it.first == mainCurrency }.coerceAtLeast(0),
                onSelect = { i -> currencies[i].first.takeIf { it != mainCurrency }?.let { pendingCurrency = it } },
                modifier = Modifier.padding(horizontal = 16.dp),
                track = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            pendingCurrency?.let { code ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { pendingCurrency = null },
                    title = { Text(tr("¿Usar ${com.midinero.control.domain.Currencies.mainName(code)}?", "Use ${com.midinero.control.domain.Currencies.mainName(code)}?")) },
                    text = {
                        Text(
                            tr(
                                "Todos los importes se mostrarán en esta moneda y los avisos de pago en ella se apuntarán tal cual; los de otras monedas se convierten. Lo que ya tengas apuntado no se convierte: solo cambia cómo se muestra.",
                                "All amounts will be shown in this currency and payment notifications in it will be logged as they are; other currencies get converted. What you've already logged isn't converted: only how it's shown changes.",
                            )
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            vm.prefs.mainCurrency = code
                            pendingCurrency = null
                            // Se redibuja todo con la moneda nueva
                            activity?.recreate()
                        }) { Text(tr("Cambiar", "Change")) }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingCurrency = null }) { Text(tr("Cancelar", "Cancel")) } },
                )
            }
            Row(
                Modifier.fillMaxWidth().clickable { onPickPeriod() }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Mi mes empieza el día", "My month starts on day"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (periodStartDay == 1) tr("Día 1 (mes natural)", "Day 1 (calendar month)") else tr("Día $periodStartDay · útil si cobras a final de mes", "Day $periodStartDay · handy if you're paid at the end of the month"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("$periodStartDay", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
