package com.midinero.control.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Pide permiso para los avisos. Si ya está concedido pero los avisos están bloqueados en Android (o en Android 12
 * y anteriores, donde no hay diálogo), abre los ajustes de notificaciones de MiDinero.
 */
internal fun requestNotifications(context: Context, launcher: ManagedActivityResultLauncher<String, Boolean>) {
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    if (needsPermission) {
        runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }.onFailure { openNotificationSettings(context) }
    } else {
        openNotificationSettings(context)
    }
}

/**
 * Tras una negativa: si Android ya no volverá a mostrar el diálogo (se ha denegado dos veces), lleva a los ajustes,
 * porque si no el botón «Permitir avisos» no haría nada.
 */
internal fun onNotificationPermissionResult(context: Context, granted: Boolean) {
    if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val activity = context.findActivity() ?: return
    if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) openNotificationSettings(context)
}

internal fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
