package com.midinero.control.ui.screens

import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lock
import com.midinero.control.domain.MerchantCatalog
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.midinero.control.MiDineroApp
import com.midinero.control.domain.PaymentApps
import com.midinero.control.domain.Period
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.tr
import com.midinero.control.service.PaymentNotificationListener
import com.midinero.control.ui.AppLock
import com.midinero.control.ui.theme.AppBrushes
import com.midinero.control.ui.theme.BudgetColors

/** Pasos de la configuración inicial (después de la presentación). */
private const val STEP_LANDING = 0
private const val STEP_DETECTION = 1
private const val STEP_APPS = 2
private const val STEP_ALERTS = 3
private const val STEP_MONTH = 4
private const val STEP_PRIVACY = 5
private const val STEP_DONE = 6
private const val SETUP_STEPS = 5

private fun batteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

private fun notificationsAllowed(context: Context): Boolean =
    // Incluye el caso de quien los bloqueó en los ajustes de Android (también antes de Android 13)
    androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * Bienvenida: una presentación de la app y una configuración guiada en 5 pasos.
 * Cada paso se puede saltar; todo se puede cambiar después en Ajustes.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, onTryDemo: (() -> Unit)? = null) {
    var step by rememberSaveable { mutableIntStateOf(STEP_LANDING) }
    BackHandler(enabled = step > STEP_LANDING) { step-- }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding",
        ) { current ->
            if (current == STEP_LANDING) {
                Landing(
                    onStart = { step = STEP_DETECTION },
                    onSkip = onFinish,
                    onTryDemo = onTryDemo,
                )
            } else {
                SetupStep(
                    step = current,
                    onBack = { step = current - 1 },
                    onNext = { if (current == STEP_DONE) onFinish() else step = current + 1 },
                    onTryDemo = onTryDemo,
                )
            }
        }
    }
}

/** Presentación: qué es MiDinero y qué hace, antes de pedir nada. */
@Composable
private fun Landing(onStart: () -> Unit, onSkip: () -> Unit, onTryDemo: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text(tr("Saltar", "Skip")) }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(AppBrushes.hero)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // El símbolo de la app (el mismo del icono): anillo del presupuesto con el €
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.midinero.control.R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(132.dp).padding(0.dp),
                )
                Text(
                    tr("Bienvenido a MiDinero", "Welcome to MiDinero"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.asHeading(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "Tus pagos con el móvil se apuntan solos. Tú solo miras cuánto te queda.",
                        "Your phone payments log themselves. You just check how much you have left.",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            Feature(androidx.compose.material.icons.Icons.Rounded.PhoneAndroid, tr("Se apunta solo", "Logs itself"), tr(
                "Detecta los pagos de Google Wallet (y de tu banco, si quieres) y ya reconoce ${MerchantCatalog.all.size} comercios habituales con su categoría.",
                "Detects Google Wallet payments (and your bank's, if you like) and already knows ${MerchantCatalog.all.size} common merchants and their category.",
            ))
            Feature(androidx.compose.material.icons.Icons.Rounded.TrackChanges, tr("Límites que avisan a tiempo", "Limits that warn you in time"), tr(
                "Presupuesto del mes y por categoría, con avisos antes de pasarte y lo que puedes gastar al día.",
                "A monthly budget and per-category limits, with alerts before you go over and what you can spend per day.",
            ))
            Feature(androidx.compose.material.icons.Icons.Rounded.Insights, tr("Entiende en qué se va", "See where it goes"), tr(
                "Comparación con el mes anterior, previsión, calendario de gasto y consejos para ahorrar.",
                "Comparison with last month, forecast, spending calendar and tips to save.",
            ))
            Feature(androidx.compose.material.icons.Icons.Rounded.FlightTakeoff, tr("Viajes, amigos y metas", "Trips, friends and goals"), tr(
                "Presupuesto para cada viaje, otras monedas, gastos compartidos, fotos de tickets y metas de ahorro.",
                "A budget for each trip, other currencies, shared expenses, receipt photos and savings goals.",
            ))
            Feature(androidx.compose.material.icons.Icons.Rounded.Shield, tr("Tus datos, solo en tu móvil", "Your data, only on your phone"), tr(
                "Sin cuentas, sin anuncios y sin permiso de Internet. Nada sale de tu móvil (salvo a tu reloj, si usas su app).",
                "No accounts, no ads and no Internet permission. Nothing leaves your phone (except to your watch, if you use its app).",
            ))
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = onStart,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(tr("Empezar la configuración", "Start setup"), style = MaterialTheme.typography.titleMedium) }
            Text(
                tr("5 pasos cortos, todos opcionales.", "5 short steps, all optional."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            if (onTryDemo != null) {
                TextButton(onClick = onTryDemo, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Probar con datos de ejemplo", "Try with sample data"))
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)).clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Marco común de cada paso: progreso, título, contenido y botones Atrás / Siguiente. */
@Composable
private fun SetupStep(step: Int, onBack: () -> Unit, onNext: () -> Unit, onTryDemo: (() -> Unit)?) {
    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as MiDineroApp).prefs }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (step != STEP_DONE) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Paso $step de $SETUP_STEPS", "Step $step of $SETUP_STEPS"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    TextButton(onClick = onNext) { Text(tr("Omitir paso", "Skip step")) }
                }
                LinearProgressIndicator(
                    progress = { step.toFloat() / SETUP_STEPS },
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            when (step) {
                STEP_DETECTION -> DetectionStep()
                STEP_APPS -> AppsStep(prefs)
                STEP_ALERTS -> AlertsStep(prefs)
                STEP_MONTH -> MonthStep(prefs)
                STEP_PRIVACY -> PrivacyStep(prefs)
                else -> DoneStep(prefs)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != STEP_DONE) {
                OutlinedButton(onClick = onBack, shape = CircleShape, modifier = Modifier.height(54.dp)) {
                    Text(tr("Atrás", "Back"))
                }
            }
            Button(
                onClick = onNext,
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(54.dp),
            ) {
                Text(
                    if (step == STEP_DONE) tr("Entrar en MiDinero", "Open MiDinero") else tr("Siguiente", "Next"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (step == STEP_DONE && onTryDemo != null) {
            TextButton(onClick = onTryDemo, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(tr("Probar con datos de ejemplo", "Try with sample data"))
            }
        }
    }
}

@Composable
private fun StepHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Box(
        Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp)) }
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
    Spacer(Modifier.height(6.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

/** Estado de un permiso: ✓ activado o ✗ pendiente (con texto, no solo color). */
@Composable
private fun StatusRow(ok: Boolean, okText: String, koText: String) {
    val color = if (ok) BudgetColors.ok else BudgetColors.warning
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (ok) "✓" else "!", color = color, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.width(10.dp))
        Text(if (ok) okText else koText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Paso 1: aviso destacado y claro de qué lee MiDinero antes de pedir el acceso a notificaciones. */
@Composable
private fun ColumnScope.DetectionStep() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(PaymentNotificationListener.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = PaymentNotificationListener.isEnabled(context) }
    StepHeader(
        androidx.compose.material.icons.Icons.Rounded.NotificationsActive,
        tr("Detección automática de pagos", "Automatic payment detection"),
        tr(
            "Para apuntar solos tus pagos, MiDinero necesita el permiso «Acceso a notificaciones» de Android.",
            "To log your payments by itself, MiDinero needs Android's “Notification access” permission.",
        ),
    )
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("Qué hace MiDinero con ese permiso", "What MiDinero does with that permission"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Bullet(tr(
                "Solo lee las notificaciones de las apps de pago que elijas en el paso siguiente. Las demás se ignoran sin leerse.",
                "It only reads notifications from the payment apps you choose in the next step. Everything else is ignored unread.",
            ))
            Bullet(tr("De cada pago guarda el importe, el comercio y la hora.", "For each payment it saves the amount, the merchant and the time."))
            Bullet(tr(
                "Todo se queda en tu móvil: MiDinero no tiene servidores ni permiso de Internet.",
                "Everything stays on your phone: MiDinero has no servers and no Internet permission.",
            ))
            Bullet(tr(
                "Puedes quitar el permiso cuando quieras. La app sigue sirviendo para apuntar a mano.",
                "You can remove the permission at any time. The app still works for logging by hand.",
            ))
        }
    }
    Spacer(Modifier.height(16.dp))
    StatusRow(enabled, tr("Detección activada", "Detection is on"), tr("Detección desactivada", "Detection is off"))
    if (!enabled) {
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("Aceptar y dar acceso", "Accept and grant access")) }
        Text(
            tr(
                "Se abrirán los ajustes de Android: busca MiDinero, actívalo y vuelve aquí.",
                "Android settings will open: find MiDinero, turn it on and come back here.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Paso 2: de qué apps se leen los pagos. */
@Composable
private fun ColumnScope.AppsStep(prefs: com.midinero.control.data.AppPrefs) {
    var enabledApps by remember { mutableStateOf(prefs.enabledPaymentApps) }
    StepHeader(
        androidx.compose.material.icons.Icons.Rounded.CreditCard,
        tr("¿Con qué pagas?", "What do you pay with?"),
        tr(
            "Marca las apps cuyos avisos de pago quieres que lea MiDinero. Google Wallet viene marcada.",
            "Tick the apps whose payment notifications MiDinero should read. Google Wallet is ticked already.",
        ),
    )
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            PaymentApps.all.forEach { app ->
                val checked = app.packageName in enabledApps
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            prefs.setPaymentAppEnabled(app.packageName, it)
                            enabledApps = prefs.enabledPaymentApps
                        },
                    )
                    Text(app.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    Text(
        tr(
            "Los avisos de bancos cambian según cada banco: si alguno no se entiende, lo verás en Ajustes → «Avisos no reconocidos».",
            "Bank notifications vary from bank to bank: if one isn't understood, you'll see it in Settings → “Unrecognised notifications”.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/** Paso 3: permiso de avisos, qué avisar y que Android no cierre la app. */
@Composable
private fun ColumnScope.AlertsStep(prefs: com.midinero.control.data.AppPrefs) {
    val context = LocalContext.current
    var notifOn by remember { mutableStateOf(notificationsAllowed(context)) }
    var batteryOk by remember { mutableStateOf(batteryUnrestricted(context)) }
    var notifyEach by remember { mutableStateOf(prefs.notifyDetected) }
    var weekly by remember { mutableStateOf(prefs.weeklySummary) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifOn = notificationsAllowed(context)
        batteryOk = batteryUnrestricted(context)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = it
        onNotificationPermissionResult(context, it)
    }
    StepHeader(
        androidx.compose.material.icons.Icons.Rounded.Campaign,
        tr("Avisos", "Notifications"),
        tr(
            "MiDinero te avisa cuando apunta un pago, cuando se cobra un cargo fijo y antes de que te pases de un límite.",
            "MiDinero lets you know when it logs a payment, when a recurring charge is made and before you go over a limit.",
        ),
    )
    StatusRow(notifOn, tr("Avisos permitidos", "Notifications allowed"), tr("Avisos no permitidos", "Notifications not allowed"))
    if (!notifOn) {
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { requestNotifications(context, launcher) },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("Permitir avisos", "Allow notifications")) }
    }
    Spacer(Modifier.height(12.dp))
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            ToggleRow(
                tr("Avisar de cada pago detectado", "Notify every detected payment"),
                tr("Si lo quitas, solo avisaremos cuando no sepamos la categoría", "If off, we'll only notify when we don't know the category"),
                notifyEach,
            ) { notifyEach = it; prefs.notifyDetected = it }
            ToggleRow(
                tr("Resumen semanal", "Weekly summary"),
                tr("Cada lunes, cuánto gastaste la semana anterior", "Every Monday, how much you spent the previous week"),
                weekly,
            ) { weekly = it; prefs.weeklySummary = it }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(tr("Funcionar en segundo plano", "Run in the background"), style = MaterialTheme.typography.titleSmall)
    Text(
        tr(
            "Algunos móviles cierran las apps para ahorrar batería y entonces los pagos no se detectan.",
            "Some phones close apps to save battery and then payments aren't detected.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    StatusRow(batteryOk, tr("Sin restricciones de batería", "No battery restrictions"), tr("Con restricciones de batería", "Battery restricted"))
    if (!batteryOk) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("Abrir ajustes de batería", "Open battery settings")) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Paso 4: cuándo empieza tu mes y cuánto quieres gastar como mucho. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.MonthStep(prefs: com.midinero.control.data.AppPrefs) {
    var startDay by remember { mutableIntStateOf(prefs.periodStartDay) }
    var budgetText by remember { mutableStateOf(prefs.monthlyBudgetCents.takeIf { it > 0 }?.let { centsToInput(it) } ?: "") }
    val budget = parseUserAmount(budgetText)
    StepHeader(
        androidx.compose.material.icons.Icons.Rounded.CalendarMonth,
        tr("Tu mes y tu presupuesto", "Your month and your budget"),
        tr(
            "Si cobras a final de mes, tus meses pueden ir de nómina a nómina. Y un presupuesto te avisa antes de pasarte.",
            "If you're paid at the end of the month, your months can run from payday to payday. And a budget warns you before you overspend.",
        ),
    )
    FieldLabel(tr("Mi mes empieza el día", "My month starts on day"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 15, 20, 25, 26, 27, 28).forEach { d ->
            FilterChip(
                selected = startDay == d,
                onClick = {
                    startDay = d
                    prefs.periodStartDay = d
                    Period.startDay = d
                },
                label = { Text(if (d == 1) tr("Día 1 (mes natural)", "Day 1 (calendar month)") else tr("Día $d", "Day $d")) },
                shape = CircleShape,
            )
        }
    }
    FieldLabel(tr("Presupuesto del mes (opcional)", "Monthly budget (optional)"))
    AmountField(
        value = budgetText,
        onValueChange = {
            budgetText = it
            val cents = parseUserAmount(it)
            prefs.monthlyBudgetCents = if (it.isBlank()) 0 else cents ?: prefs.monthlyBudgetCents
        },
        isError = budgetText.isNotBlank() && budget == null,
        label = tr("Lo máximo que quieres gastar al mes", "The most you want to spend a month"),
    )
    Text(
        tr(
            "Luego podrás poner límites por categoría (restaurantes, ocio…) en la pestaña Límites.",
            "Later you can set per-category limits (restaurants, leisure…) in the Limits tab.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Paso 5: bloqueo, importes ocultos y copias de seguridad. */
@Composable
private fun ColumnScope.PrivacyStep(prefs: com.midinero.control.data.AppPrefs) {
    val context = LocalContext.current
    val lockAvailable = remember { AppLock.isAvailable(context) }
    var lock by remember { mutableStateOf(prefs.appLock) }
    var hide by remember { mutableStateOf(prefs.hideAmounts) }
    StepHeader(
        androidx.compose.material.icons.Icons.Rounded.Lock,
        tr("Privacidad", "Privacy"),
        tr(
            "Tus datos solo están en este móvil. Protégelos como quieras.",
            "Your data is only on this phone. Protect it however you like.",
        ),
    )
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (lockAvailable) {
                ToggleRow(
                    tr("Bloquear con huella o PIN", "Lock with fingerprint or PIN"),
                    tr("Se pide al abrir la app y tras 30 s en segundo plano", "Asked when opening the app and after 30 s in the background"),
                    lock,
                ) { lock = it; prefs.appLock = it }
            }
            ToggleRow(
                tr("Ocultar importes", "Hide amounts"),
                tr("Muestra •••• en lugar de las cantidades", "Shows •••• instead of amounts"),
                hide,
            ) { hide = it; prefs.hideAmounts = it }
        }
    }
    Spacer(Modifier.height(12.dp))
    AppCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(16.dp)) {
            Text("💾", fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(12.dp))
            Text(
                tr(
                    "Consejo: en Ajustes → Tus datos puedes activar una copia automática en una carpeta de Google Drive para no perder nada si cambias de móvil.",
                    "Tip: in Settings → Your data you can turn on an automatic backup to a Google Drive folder so you don't lose anything if you change phones.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Final: resumen de lo configurado. */
@Composable
private fun ColumnScope.DoneStep(prefs: com.midinero.control.data.AppPrefs) {
    val context = LocalContext.current
    val detection = remember { PaymentNotificationListener.isEnabled(context) }
    val notifications = remember { notificationsAllowed(context) }
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) { Text("🎉", fontSize = 46.sp) }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        tr("¡Todo listo!", "All set!"),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().asHeading(),
    )
    Text(
        if (detection) tr("A partir de ahora, lo que pagues con el móvil aparecerá solo.", "From now on, what you pay with your phone will appear by itself.")
        else tr("Puedes apuntar a mano, o activar la detección más tarde en Ajustes.", "You can log by hand, or turn on detection later in Settings."),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 20.dp),
    )
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusRow(detection, tr("Detección de pagos activada", "Payment detection on"), tr("Detección de pagos pendiente", "Payment detection pending"))
            StatusRow(notifications, tr("Avisos permitidos", "Notifications allowed"), tr("Avisos no permitidos", "Notifications not allowed"))
            val budget = prefs.monthlyBudgetCents
            StatusRow(
                budget > 0,
                tr("Presupuesto del mes: ", "Monthly budget: ") + com.midinero.control.domain.formatCents(budget),
                tr("Sin presupuesto del mes (opcional)", "No monthly budget (optional)"),
            )
        }
    }
}

