package com.midinero.control.wear

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.midinero.control.link.WatchState

@Composable
fun SettingsScreen(state: WatchState, initiallyEnabled: Boolean, now: Long, onSync: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    var noPermissionScreen by remember { mutableStateOf(false) }
    // Se vuelve a mirar al volver de Ajustes del reloj
    var listenerEnabled by remember { mutableStateOf(initiallyEnabled) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        listenerEnabled = WatchPaymentListener.isEnabled(context)
        WatchSync.publishInfo(context)
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(W.Black),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(tr("Ajustes", "Settings"), style = label(15.sp, W.White, FontWeight.Bold)) }
            item {
                SettingRow(
                    tr("Pagos del reloj", "Watch payments"),
                    if (listenerEnabled) tr("Activado", "On") else tr("Desactivado", "Off"),
                    if (listenerEnabled) W.Lime else W.Faint,
                ) {
                    if (!listenerEnabled) {
                        // Primero la pantalla del permiso de esta app; luego la lista general. Los Galaxy Watch no traen
                        // ninguna de las dos: entonces se explica la alternativa.
                        val component = android.content.ComponentName(context, WatchPaymentListener::class.java).flattenToString()
                        val candidates = listOf(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                        )
                        val opened = candidates.any { intent ->
                            context.packageManager.resolveActivity(intent, 0) != null &&
                                runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                        }
                        if (!opened) noPermissionScreen = true
                    }
                }
            }
            item {
                Text(
                    tr(
                        "Con acceso a notificaciones, los pagos con Google Wallet o Samsung Wallet del reloj se apuntan solos en el móvil, sin duplicarse con los avisos del banco. Las recargas de Revolut no cuentan.",
                        "With notification access, Google Wallet or Samsung Wallet payments made on the watch are added on your phone automatically, without duplicating your bank's alerts. Revolut top-ups are ignored.",
                    ),
                    style = label(11.sp, W.Soft, FontWeight.Normal), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
            }
            if (state.detection.not() && !state.isEmpty) item {
                Text(
                    tr("Activa también la detección de pagos en la app del móvil.", "Also turn on payment detection in the phone app."),
                    style = label(11.sp, W.Amber, FontWeight.Normal), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
            }
            if (noPermissionScreen || !listenerEnabled) item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp)).background(W.Ink).padding(12.dp),
                ) {
                    Text(
                        tr("Este reloj no muestra el permiso", "This watch doesn't show the permission"),
                        style = label(12.sp, W.Amber, FontWeight.SemiBold),
                    )
                    Text(
                        tr(
                            "Algunos relojes, como los Galaxy Watch, no tienen la pantalla de acceso a notificaciones. En ellos, si tu banco te avisa en el móvil de cada pago con tarjeta, MiDinero ya los apunta desde el móvil.",
                            "Some watches, such as Galaxy Watches, have no notification access screen. On them, if your bank notifies you on the phone of each card payment, MiDinero already logs them from the phone.",
                        ),
                        style = label(10.sp, W.Soft, FontWeight.Normal),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item { SettingRow(tr("Sincronizar ahora", "Sync now"), updatedLabel(state.updated, now), W.Faint, onSync) }
            item {
                Text("MiDinero $version", style = label(10.sp, W.Faint), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, dot: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(W.Card).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = label(13.sp, W.White, FontWeight.SemiBold))
            Text(value, style = label(11.sp, W.Soft, FontWeight.Normal))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
    }
}
