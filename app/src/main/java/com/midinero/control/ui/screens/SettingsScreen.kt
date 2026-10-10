package com.midinero.control.ui.screens

import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.ui.DataViewModel
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.ui.graphics.Color
import com.midinero.control.domain.tr
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
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
import com.midinero.control.service.PaymentNotificationListener
import com.midinero.control.ui.AppLock
import com.midinero.control.ui.MainViewModel

/** Política de privacidad pública: la misma URL que se pone en Play Console. */
private const val PRIVACY_URL = "https://jmrmingorance.github.io/midinero-legal/"

private fun notificationsAllowed(context: Context): Boolean =
    // Incluye el caso de quien los bloqueó en los ajustes de Android (también antes de Android 13)
    androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun ignoresBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenHelp: () -> Unit = {},
    dataVm: DataViewModel = viewModel(),
) {
    val context = LocalContext.current
    val learned by vm.learnedRules.collectAsStateWithLifecycle()
    // Se relee cada vez que cambia una preferencia.
    val prefsVersion by vm.prefs.changes.collectAsStateWithLifecycle()

    var detectionOn by remember { mutableStateOf(PaymentNotificationListener.isEnabled(context)) }
    var notifOn by remember { mutableStateOf(notificationsAllowed(context)) }
    var batteryOk by remember { mutableStateOf(ignoresBatteryOptimizations(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        detectionOn = PaymentNotificationListener.isEnabled(context)
        notifOn = notificationsAllowed(context)
        batteryOk = ignoresBatteryOptimizations(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = it
        onNotificationPermissionResult(context, it)
    }


    val enabledApps = remember(prefsVersion) { vm.prefs.enabledPaymentApps }
    val lastDetection = remember(prefsVersion) { vm.prefs.lastDetectionAt }
    val unrecognized = remember(prefsVersion) { vm.prefs.unrecognized }
    // Aviso no reconocido que se está apuntando a mano
    var loggingNotice by remember { mutableStateOf<com.midinero.control.data.UnrecognizedNotification?>(null) }
    val alertThreshold = remember(prefsVersion) { vm.prefs.alertThreshold }
    val appLock = remember(prefsVersion) { vm.prefs.appLock }
    val autoBackupUri = remember(prefsVersion) { vm.prefs.autoBackupUri }
    val autoBackupDays = remember(prefsVersion) { vm.prefs.autoBackupDays }
    val autoBackupError = remember(prefsVersion) { vm.prefs.lastAutoBackupError }
    val lastBackup = remember(prefsVersion) { vm.prefs.lastBackupAt }
    val notifyDetected = remember(prefsVersion) { vm.prefs.notifyDetected }
    val hideAmounts = remember(prefsVersion) { vm.prefs.hideAmounts }
    val secureScreen = remember(prefsVersion) { vm.prefs.secureScreen }
    val themeMode = remember(prefsVersion) { vm.prefs.themeMode }
    val periodStartDay = remember(prefsVersion) { vm.prefs.periodStartDay }
    val lockAvailable = remember { AppLock.isAvailable(context) }
    var showPeriodDialog by rememberSaveable { mutableStateOf(false) }
    val weeklySummary = remember(prefsVersion) { vm.prefs.weeklySummary }
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
    // Copia cifrada que espera su contraseña (y si la última que se probó no valía)
    var lockedBackup by remember { mutableStateOf<android.net.Uri?>(null) }
    var lockedWrong by remember { mutableStateOf(false) }
    var editingBackupPassword by rememberSaveable { mutableStateOf(false) }
    val backupPasswordSet = remember(prefsVersion) { vm.prefs.backupPassword != null }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    fun say(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) dataVm.exportBackup(uri, ::say)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) dataVm.exportCsv(uri, ::say)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            dataVm.setAutoBackupFolder(uri)
            dataVm.backupNow(::say)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dataVm.readBackup(uri) { result ->
            result.fold(
                { pendingRestore = it },
                {
                    if (it is com.midinero.control.data.BackupPasswordException) {
                        lockedWrong = false
                        lockedBackup = uri
                    } else say(it.message ?: tr("No se ha podido leer la copia", "Couldn't read the backup"))
                },
            )
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(tr("Ajustes", "Settings"), onBack = onBack) {
            androidx.compose.material3.FilledTonalButton(onClick = onOpenHelp) { Text(tr("Ayuda", "Help")) }
        }

        AppearanceSection(vm, onPickPeriod = { showPeriodDialog = true })

        SettingCard(
            title = tr("Detección automática de pagos", "Automatic payment detection"),
            ok = detectionOn,
            okText = tr("Activada", "On"),
            koText = tr("Desactivada", "Off"),
            description = tr("MiDinero lee solo las notificaciones de las apps de pago que marques abajo para apuntar ", "MiDinero only reads notifications from the payment apps you tick below to log ") +
                tr("el importe y el comercio de cada pago. Todo se guarda en tu móvil y no se envía a ningún sitio.", "the amount and merchant of each payment. Everything is stored on your phone and sent nowhere."),
        ) {
            Text(
                if (lastDetection > 0) {
                    tr("Último pago detectado: ", "Last payment detected: ") + DateUtils.getRelativeTimeSpanString(
                        lastDetection, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    )
                } else tr("Todavía no se ha detectado ningún pago.", "No payment has been detected yet."),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                // Algunos móviles (Android Go, perfiles de trabajo) no traen esta pantalla
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    android.widget.Toast.makeText(context, tr("Abre Ajustes → Notificaciones → Acceso a notificaciones", "Open Settings → Notifications → Notification access"), android.widget.Toast.LENGTH_LONG).show()
                }
            }) { Text(if (detectionOn) tr("Revisar permiso", "Check permission") else tr("Dar acceso a notificaciones", "Grant notification access")) }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 12.dp)) {
                SettingsTitle(Icons.Filled.Contactless, Color(0xFF2B7BC4), tr("Apps de pago", "Payment apps"), Modifier.padding(horizontal = 16.dp))
                Text(
                    tr("Elige de qué apps se leen los pagos. Las de bancos dependen del texto de cada banco; ", "Choose which apps payments are read from. Bank apps depend on each bank's wording; ") +
                        tr("si alguna no se reconoce, aparecerá en «Avisos no reconocidos».", "if one isn't recognised, it will appear under “Unrecognised notifications”."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                val apps = if (showAllApps) PaymentApps.all else PaymentApps.all.filter { it.enabledByDefault || it.packageName in enabledApps }
                apps.forEach { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.prefs.setPaymentAppEnabled(app.packageName, app.packageName !in enabledApps) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(app.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = app.packageName in enabledApps,
                            onCheckedChange = { vm.prefs.setPaymentAppEnabled(app.packageName, it) },
                        )
                    }
                }
                TextButton(onClick = { showAllApps = !showAllApps }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showAllApps) tr("Mostrar menos", "Show less") else tr("Añadir bancos y otras apps", "Add banks and other apps"))
                }
            }
        }

        SettingCard(
            title = tr("Avisos de MiDinero", "MiDinero notifications"),
            ok = notifOn,
            okText = tr("Permitidos", "Allowed"),
            koText = tr("Bloqueados", "Blocked"),
            description = tr("Para avisarte de cada gasto detectado, de los cargos fijos y cuando te acerques o superes un límite.", "To tell you about each detected expense, recurring charges and when you get close to or go over a limit."),
        ) {
            if (!notifOn) {
                Button(onClick = { requestNotifications(context, permissionLauncher) }) {
                    Text(tr("Permitir avisos", "Allow notifications"))
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { vm.prefs.notifyDetected = !notifyDetected },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Avisar de cada pago detectado", "Notify every detected payment"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        tr("Si lo quitas, solo avisaremos cuando no sepamos la categoría", "If off, we'll only notify when we don't know the category"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = notifyDetected, onCheckedChange = { vm.prefs.notifyDetected = it })
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SettingsTitle(Icons.Filled.NotificationsActive, Color(0xFFE64980), tr("Avisos de límites", "Limit alerts"), Modifier)
                Text(
                    tr("Además de al pasarte, MiDinero puede avisarte antes, al llegar a este porcentaje del límite:", "Besides when you go over, MiDinero can warn you earlier, when you reach this percentage of the limit:"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to tr("Solo al pasarme", "Only when I go over"), 50 to "50 %", 80 to "80 %", 90 to "90 %").forEach { (value, label) ->
                        FilterChip(
                            selected = alertThreshold == value,
                            onClick = { vm.prefs.alertThreshold = value },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { vm.prefs.weeklySummary = !weeklySummary }.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Resumen semanal", "Weekly summary"), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            tr("Cada lunes, cuánto gastaste la semana anterior", "Every Monday, how much you spent the previous week"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = weeklySummary, onCheckedChange = { vm.prefs.weeklySummary = it })
                }
            }
        }

        SettingCard(
            title = tr("Funcionar en segundo plano", "Run in the background"),
            ok = batteryOk,
            okText = tr("Sin restricciones", "Unrestricted"),
            koText = tr("Restringido", "Restricted"),
            description = tr("Algunos móviles cierran las apps para ahorrar batería y los pagos dejan de detectarse. ", "Some phones close apps to save battery and payments stop being detected. ") +
                tr("Si te pasa, quita a MiDinero de la optimización de batería.", "If that happens, remove MiDinero from battery optimisation."),
        ) {
            if (!batteryOk) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text(tr("Abrir ajustes de batería", "Open battery settings")) }
            }
        }

        if (unrecognized.isNotEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsTitle(Icons.Filled.NotificationImportant, Color(0xFFF08C00), tr("Avisos no reconocidos", "Unrecognised notifications"), Modifier)
                    Text(
                        tr("Notificaciones de apps de pago con importes que MiDinero no ha sabido leer. ", "Payment app notifications with amounts MiDinero couldn't read. ") +
                            tr("Si alguna era un gasto, pulsa «Apuntar» y revisa el importe y el comercio.", "If any was an expense, tap “Log it” and check the amount and merchant."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    unrecognized.take(5).forEach { n ->
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "${n.app} · " + DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(n.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(n.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { loggingNotice = n }) { Text(tr("Apuntar", "Log it")) }
                        }
                    }
                    TextButton(onClick = { vm.prefs.clearUnrecognized() }) { Text(tr("Borrar lista", "Clear list")) }
                }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SettingsTitle(Icons.Filled.AutoAwesome, Color(0xFF0E8A6D), tr("Categorización aprendida", "Learned categories"), Modifier)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (learned == 1) tr("MiDinero recuerda la categoría de 1 comercio.", "MiDinero remembers the category of 1 merchant.")
                    else tr("MiDinero recuerda la categoría de $learned comercios.", "MiDinero remembers the category of $learned merchants."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    tr("Cuando cambias la categoría de un gasto, la app la usa para los siguientes pagos en ese comercio.", "When you change an expense's category, the app uses it for future payments at that merchant."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    tr("Tus reglas y los comercios que ya conoce están en Más.", "Your rules and the merchants it already knows are in More."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (learned > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showRules = true }) { Text(tr("Ver comercios", "View merchants")) }
                        TextButton(onClick = vm::forgetLearnedRules) { Text(tr("Olvidar todo", "Forget all")) }
                    }
                }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SettingsTitle(Icons.Filled.Storage, Color(0xFF5C7CFA), tr("Tus datos", "Your data"), Modifier)
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("Tus datos solo están en este móvil. Guarda una copia de vez en cuando (por ejemplo en Google Drive) ", "Your data is only on this phone. Save a backup now and then (for example to Google Drive) ") +
                        tr("para no perderlos si cambias de móvil.", "so you don't lose it if you change phones."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val receiptBytes by produceState(0L) { value = dataVm.receiptBytes() }
                if (receiptBytes > 0) {
                    Text(
                        tr("Las fotos de tickets (%.1f MB) no van en la copia en fichero; sí pasan al móvil nuevo con la transferencia de Android.", "Receipt photos (%.1f MB) aren't included in the backup file; they do move to a new phone with Android's transfer.")
                            .format(receiptBytes / 1_048_576.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        // Si el móvil no abre el selector de archivos, se guarda directamente en Descargas/MiDinero
                        runCatching { exportLauncher.launch(tr("MiDinero-copia-${LocalDate.now()}.json", "MiDinero-backup-${LocalDate.now()}.json")) }
                            .onFailure { dataVm.exportToDownloads(::say) }
                    }) { Text(tr("Guardar copia", "Save backup")) }
                    OutlinedButton(onClick = { runCatching { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }.onFailure { say(tr("Este móvil no deja abrir archivos desde aquí", "This phone can't open files from here")) } }) {
                        Text(tr("Restaurar", "Restore"))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { csvLauncher.launch(tr("MiDinero-movimientos-${LocalDate.now()}.csv", "MiDinero-transactions-${LocalDate.now()}.csv")) }
                            .onFailure { say(tr("Este móvil no deja elegir dónde guardar el archivo", "This phone can't open the file picker")) }
                    }) {
                        Text(tr("Exportar CSV", "Export CSV"))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Copias con contraseña", "Password-protected backups"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            if (backupPasswordSet) tr("Las copias (también las automáticas) se guardan cifradas. Para restaurarlas en otro móvil hará falta la contraseña.", "Backups (automatic ones too) are saved encrypted. You'll need the password to restore them on another phone.")
                            else tr("Las copias se guardan sin cifrar: quien tenga el fichero puede leer tus movimientos.", "Backups are saved unencrypted: anyone with the file can read your transactions."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editingBackupPassword = true }) {
                        Text(if (backupPasswordSet) tr("Cambiar", "Change") else tr("Poner", "Set"))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(tr("Copia automática", "Automatic backup"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        autoBackupError != null -> autoBackupError!!
                        autoBackupUri == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> tr("MiDinero guarda sola una copia en la carpeta que elijas (por ejemplo de Google Drive). Se conservan las 5 últimas.", "MiDinero saves a backup by itself to a folder you choose (for example in Google Drive). The last 5 are kept.")
                        autoBackupUri == null -> tr("MiDinero guarda sola una copia en Descargas/MiDinero o en la carpeta que elijas (por ejemplo de Google Drive). Se conservan las 5 últimas.", "MiDinero saves a backup by itself to Downloads/MiDinero or to a folder you choose (for example in Google Drive). The last 5 are kept.")
                        autoBackupDays == 0 -> tr("Desactivada", "Off")
                        else -> (if (autoBackupUri == com.midinero.control.service.BackupWorker.DOWNLOADS) tr("En Descargas/MiDinero · ", "In Downloads/MiDinero · ") else "") +
                            (if (autoBackupDays == 1) tr("Cada día", "Every day") else tr("Cada semana", "Every week")) +
                            tr(" · última copia: ", " · last backup: ") + (if (lastBackup > 0) DateUtils.getRelativeTimeSpanString(lastBackup, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else tr("todavía ninguna", "none yet"))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (autoBackupError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (autoBackupUri != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to tr("Desactivada", "Off"), 1 to tr("Diaria", "Daily"), 7 to tr("Semanal", "Weekly")).forEach { (d, label) ->
                            FilterChip(selected = autoBackupDays == d, onClick = { vm.prefs.autoBackupDays = d }, label = { Text(label) })
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Descargas/MiDinero sin pedir permisos solo existe desde Android 10
                    if (autoBackupUri != com.midinero.control.service.BackupWorker.DOWNLOADS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        OutlinedButton(onClick = { dataVm.setAutoBackupDownloads(::say) }) { Text(tr("Guardar en Descargas", "Save to Downloads")) }
                    }
                    OutlinedButton(onClick = {
                        // Algunos móviles no traen el selector de carpetas: entonces se usa Descargas/MiDinero
                        runCatching { folderLauncher.launch(null) }
                            .onFailure {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    say(tr("Este móvil no deja elegir una carpeta: se guardará en Descargas/MiDinero", "This phone can't pick a folder: backups go to Downloads/MiDinero"))
                                    dataVm.setAutoBackupDownloads { }
                                } else {
                                    say(tr("Este móvil no deja elegir una carpeta para las copias automáticas", "This phone can't pick a folder for automatic backups"))
                                }
                            }
                    }) {
                        Text(if (autoBackupUri == null || autoBackupUri == com.midinero.control.service.BackupWorker.DOWNLOADS) tr("Elegir carpeta", "Choose folder") else tr("Cambiar carpeta", "Change folder"))
                    }
                    if (autoBackupUri != null) {
                        TextButton(onClick = { dataVm.backupNow(::say) }) { Text(tr("Copiar ahora", "Back up now")) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { confirmWipe = true }) {
                    Text(tr("Borrar todos los datos", "Delete all data"), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        WatchCard()

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SettingsTitle(Icons.Filled.Shield, Color(0xFF1FA971), tr("Privacidad", "Privacy"), Modifier)
                Spacer(Modifier.height(4.dp))
                listOf(
                    tr("MiDinero no tiene servidores, no pide cuenta y no usa Internet: tus datos solo están en este móvil (y un resumen en tu reloj, si usas su app).", "MiDinero has no servers, needs no account and doesn't use the Internet: your data is only on this phone (and a summary on your watch, if you use its app)."),
                    tr("Solo lee notificaciones de las apps de pago que marques, y de ellas solo guarda importe, comercio y hora.", "It only reads notifications from the payment apps you tick, and only saves the amount, merchant and time."),
                    tr("Sin anuncios ni analítica. Las copias de seguridad las guardas tú donde quieras.", "No ads or analytics. You keep backups wherever you like."),
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text(tr("Política de privacidad completa", "Full privacy policy")) }
            }
        }

        Text(
            tr("MiDinero Control de gastos · versión ${BuildConfig.VERSION_NAME}", "MiDinero Expense tracker · version ${BuildConfig.VERSION_NAME}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    loggingNotice?.let { n ->
        val categories by vm.expenseCategories.collectAsStateWithLifecycle()
        val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
        val posted = java.time.Instant.ofEpochMilli(n.time).atZone(com.midinero.control.domain.zone)
        ExpenseEditorDialog(
            expense = null,
            categories = categories,
            incomeCategories = incomeCategories,
            onDismiss = { loggingNotice = null },
            rateFor = vm.prefs::rateFor,
            onCreateCategory = vm::createCategory,
            defaultDate = posted.toLocalDate(),
            initialTime = posted.toLocalTime().withSecond(0).withNano(0),
            initialAmountCents = com.midinero.control.domain.PaymentParser.guessAmount("${n.title}\n${n.text}"),
            initialNote = "${n.app}: ${n.title} ${n.text}".trim().take(300),
            onSave = { d ->
                vm.addExpense(d)
                vm.prefs.removeUnrecognized(n)
                loggingNotice = null
                say(tr("Apuntado", "Logged"))
            },
        )
    }

    lockedBackup?.let { uri ->
        BackupPasswordDialog(
            title = tr("Copia protegida", "Protected backup"),
            message = if (lockedWrong) tr("La contraseña no es correcta. Prueba otra vez.", "Wrong password. Try again.")
            else tr("Esta copia está cifrada. Escribe la contraseña con la que se guardó.", "This backup is encrypted. Enter the password it was saved with."),
            confirm = false,
            onDismiss = { lockedBackup = null },
            onSave = { password ->
                dataVm.readBackup(uri, password) { result ->
                    result.fold(
                        { lockedBackup = null; pendingRestore = it },
                        {
                            if (it is com.midinero.control.data.BackupPasswordException) lockedWrong = true
                            else { lockedBackup = null; say(it.message ?: tr("No se ha podido leer la copia", "Couldn't read the backup")) }
                        },
                    )
                }
            },
        )
    }
    if (editingBackupPassword) {
        BackupPasswordDialog(
            title = tr("Contraseña de las copias", "Backup password"),
            message = tr("Si la olvidas, no podrás restaurar las copias cifradas con ella. Guárdala en un sitio seguro.", "If you forget it, you won't be able to restore backups encrypted with it. Keep it somewhere safe."),
            confirm = true,
            onDismiss = { editingBackupPassword = false },
            onSave = {
                vm.prefs.backupPassword = it
                editingBackupPassword = false
                say(tr("Las próximas copias se guardarán cifradas", "Future backups will be saved encrypted"))
            },
            onRemove = if (backupPasswordSet) {
                {
                    vm.prefs.backupPassword = null
                    editingBackupPassword = false
                    say(tr("Las próximas copias se guardarán sin cifrar", "Future backups will be saved unencrypted"))
                }
            } else null,
        )
    }

    pendingRestore?.let { data ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(tr("¿Restaurar esta copia?", "Restore this backup?")) },
            text = {
                Text(
                    tr("La copia tiene ${data.expenses.size} movimientos, ${data.categories.size} categorías y ", "The backup has ${data.expenses.size} transactions, ${data.categories.size} categories and ") +
                        tr("${data.recurring.size} recurrentes", "${data.recurring.size} recurring charges") +
                        (if (data.exportedAt > 0) tr(", guardada el ${dateOf(data.exportedAt).shortLabel()}", ", saved on ${dateOf(data.exportedAt).shortLabel()}") else "") +
                        tr(".\n\nSe sustituirán todos los datos actuales.", ".\n\nAll current data will be replaced.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    dataVm.restoreBackup(data) { ok ->
                        // Tras restaurar, se vuelve al mes actual
                        if (ok) vm.goToCurrentMonth()
                        say(if (ok) tr("Copia restaurada", "Backup restored") else tr("No se ha podido restaurar la copia; tus datos siguen como estaban", "Couldn't restore the backup; your data is unchanged"))
                    }
                }) { Text(tr("Restaurar", "Restore")) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(tr("¿Borrar todos los datos?", "Delete all data?")) },
            text = { Text(tr("Se eliminarán todos los movimientos, recurrentes, límites y lo aprendido. No se puede deshacer. Si quieres conservarlos, guarda antes una copia.", "All transactions, recurring charges, limits and learned categories will be deleted. This can't be undone. If you want to keep them, save a backup first.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    dataVm.deleteAllData { say(tr("Datos borrados", "Data deleted")) }
                }) { Text(tr("Borrar todo", "Delete everything"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }

    if (showRules) {
        LearnedRulesDialog(vm, onDismiss = { showRules = false })
    }

    if (showPeriodDialog) {
        PeriodDialog(
            current = periodStartDay,
            onDismiss = { showPeriodDialog = false },
            onSave = {
                vm.setPeriodStartDay(it)
                showPeriodDialog = false
            },
        )
    }
}


/** Estado de la app del reloj (Wear OS): si hay un reloj con MiDinero conectado y cómo instalarla desde Google Play. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WatchCard() {
    val context = LocalContext.current
    val watch by androidx.compose.runtime.produceState<String?>(initialValue = null) {
        value = com.midinero.control.watch.WatchBridge.connectedWatch(context.applicationContext)
    }
    val watchListener by androidx.compose.runtime.produceState<Boolean?>(initialValue = null, watch) {
        value = com.midinero.control.watch.WatchBridge.watchListenerEnabled(context.applicationContext)
    }
    var showGrant by rememberSaveable { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SettingsTitle(Icons.Filled.Watch, Color(0xFF7C5CFF), tr("Reloj", "Watch"), Modifier)
            Spacer(Modifier.height(4.dp))
            Text(
                watch?.let { tr("Conectado a $it. El reloj muestra lo que puedes gastar hoy y puedes apuntar gastos desde él.", "Connected to $it. The watch shows what you can spend today and lets you add expenses.") }
                    ?: tr(
                        "Instala MiDinero en tu reloj Wear OS desde Google Play para ver lo que puedes gastar hoy, añadir una tarjeta y una complicación a la esfera y apuntar gastos desde la muñeca.",
                        "Install MiDinero on your Wear OS watch from Google Play to see what you can spend today, add a tile and a watch face complication and log expenses from your wrist.",
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (watch == null) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { openPlayListing(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Instalar en el reloj", "Install on the watch"))
                }
                Text(
                    tr(
                        "En la ficha de MiDinero en Google Play, elige tu reloj en «Disponible en más dispositivos». También puedes abrir Google Play en el reloj y buscar MiDinero.",
                        "On MiDinero's Google Play page, pick your watch under “Available on more devices”. You can also open Google Play on the watch and search for MiDinero.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Pagos hechos con el reloj: necesitan que la app del reloj tenga acceso a notificaciones
            if (watchListener != null || watch != null) {
                Spacer(Modifier.height(8.dp))
                MenuRow(
                    Icons.Filled.Watch, Color(0xFF7C5CFF), tr("Pagos del reloj", "Watch payments"),
                    when (watchListener) {
                        true -> tr("Activados: los pagos de Wallet en el reloj se apuntan solos", "On: Wallet payments on the watch are logged automatically")
                        false -> tr("Sin acceso a notificaciones en el reloj · toca para ver cómo activarlo", "No notification access on the watch · tap to see how to turn it on")
                        null -> tr("Abre MiDinero en el reloj para ver su estado", "Open MiDinero on the watch to see its status")
                    },
                    onClick = { showGrant = !showGrant },
                )
            }
            if (showGrant) {
                Text(
                    tr(
                        "En el reloj, abre MiDinero → ⚙ → «Pagos del reloj» y da el acceso a notificaciones. Algunos relojes, como los Galaxy Watch, no tienen esa pantalla: en ellos, si tu banco te avisa en el móvil de cada pago con tarjeta, MiDinero ya los apunta desde el móvil.",
                        "On the watch, open MiDinero → ⚙ → “Watch payments” and grant notification access. Some watches, such as Galaxy Watches, don't have that screen: on them, if your bank notifies you on the phone of each card payment, MiDinero already logs them from the phone.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (watch != null) {
                TextButton(onClick = {
                    com.midinero.control.watch.WatchBridge.publishSoon(context.applicationContext)
                    android.widget.Toast.makeText(context, tr("Enviando el resumen al reloj", "Sending the summary to the watch"), android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(tr("Sincronizar ahora", "Sync now")) }
            }
        }
    }
}

/** Abre la ficha de MiDinero en Google Play, desde donde se instala también en el reloj. */
private fun openPlayListing(context: Context) {
    val id = context.packageName
    val market = Intent(Intent.ACTION_VIEW, "market://details?id=$id".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$id".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Pide una contraseña para las copias; con [confirm] hay que escribirla dos veces. */
@Composable
private fun BackupPasswordDialog(
    title: String,
    message: String,
    confirm: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = confirm && password.length in 1 until MIN_BACKUP_PASSWORD
    val mismatch = confirm && repeat.isNotEmpty() && repeat != password
    val valid = password.isNotEmpty() && !tooShort && (!confirm || repeat == password)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text(tr("Contraseña", "Password")) },
                    singleLine = true,
                    isError = tooShort,
                    supportingText = if (tooShort) { { Text(tr("Al menos $MIN_BACKUP_PASSWORD caracteres", "At least $MIN_BACKUP_PASSWORD characters")) } } else null,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirm) {
                    androidx.compose.material3.OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it.take(128) },
                        label = { Text(tr("Repite la contraseña", "Repeat the password")) },
                        singleLine = true,
                        isError = mismatch,
                        supportingText = if (mismatch) { { Text(tr("No coinciden", "They don't match")) } } else null,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text(tr("Quitar la contraseña", "Remove the password"), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(password) }, enabled = valid) { Text(if (confirm) tr("Guardar", "Save") else tr("Abrir", "Open")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

private const val MIN_BACKUP_PASSWORD = 6
