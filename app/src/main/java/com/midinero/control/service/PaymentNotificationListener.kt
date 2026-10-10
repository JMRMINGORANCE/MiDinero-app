package com.midinero.control.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.midinero.control.MiDineroApp
import com.midinero.control.data.UnrecognizedNotification
import com.midinero.control.domain.PaymentApps
import com.midinero.control.domain.PaymentParser
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Escucha las notificaciones de las apps de pago elegidas (Google Wallet por defecto) y registra los pagos.
 * El usuario tiene que concederle "Acceso a notificaciones" desde Ajustes de Android.
 * Solo se procesan notificaciones de esas apps; el resto se ignora sin leerse.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        // Un fallo leyendo una notificación rara nunca debe tumbar el servicio.
        runCatching { handle(sbn) }
    }

    private fun handle(sbn: StatusBarNotification) {
        val app = applicationContext as MiDineroApp
        if (sbn.packageName !in app.prefs.enabledPaymentApps) return
        val notification = sbn.notification ?: return
        // Los resúmenes de grupo repiten el texto de otra notificación, y las persistentes son "pago en curso".
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val body = listOfNotNull(text, sub).joinToString("\n")
        val postedAt = eventTime(sbn)

        val payment = PaymentParser.parse(title, body)
        if (payment == null) {
            // Guardamos los avisos con importes que no entendemos, para poder revisarlos en Ajustes.
            val all = "${title.orEmpty()} $body"
            if (PaymentApps.looksLikeMoney(all)) {
                app.prefs.addUnrecognized(
                    UnrecognizedNotification(PaymentApps.nameOf(sbn.packageName), title.orEmpty(), body, postedAt)
                )
            }
            return
        }
        // Una notificación que se actualiza (mismo key y mismo importe) es el mismo pago.
        val fingerprint = notificationId(sbn, title.orEmpty(), body)
        synchronized(recentKeys) {
            if (!recentKeys.add(fingerprint)) return
            if (recentKeys.size > 50) recentKeys.remove(recentKeys.first())
        }
        val raw = listOfNotNull(PaymentApps.nameOf(sbn.packageName), title, body).joinToString(" | ")
        app.appScope.launch {
            try {
                if (app.repository.addDetectedPayment(payment, raw, postedAt, "phone:$fingerprint") != null) {
                    app.prefs.lastDetectionAt = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                // Si no se pudo guardar, que el mismo aviso pueda volver a intentarlo
                synchronized(recentKeys) { recentKeys.remove(fingerprint) }
                throw e
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Algunos fabricantes desconectan el servicio; pedimos a Android que lo vuelva a conectar.
        NotificationListenerService.requestRebind(ComponentName(this, PaymentNotificationListener::class.java))
    }

    companion object {
        private val recentKeys = LinkedHashSet<String>()

        /** Hora del pago: la que indica el aviso si es razonable, si no la de publicación. */
        fun eventTime(sbn: StatusBarNotification): Long {
            val posted = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
            return sbn.notification?.`when`?.takeIf { it > 0 && it <= posted + 60_000 } ?: posted
        }

        /** Identificador estable de un aviso concreto, para no apuntarlo dos veces si llega repetido. */
        fun notificationId(sbn: StatusBarNotification, title: String, body: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("${sbn.packageName}|${sbn.key}|${eventTime(sbn)}|$title|$body".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val me = ComponentName(context, PaymentNotificationListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }

        /** Reconecta el servicio si Android lo ha parado (p. ej. tras actualizar la app). */
        fun ensureConnected(context: Context) {
            if (!isEnabled(context)) return
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, PaymentNotificationListener::class.java)) }
        }
    }
}

