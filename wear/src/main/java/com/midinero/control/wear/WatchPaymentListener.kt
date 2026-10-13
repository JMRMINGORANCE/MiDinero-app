package com.midinero.control.wear

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.midinero.control.link.WatchNotice
import java.security.MessageDigest

/**
 * Lee los avisos de pago de las carteras del reloj (Google Wallet, Samsung Wallet) y se los pasa al móvil,
 * que decide si es un pago, si esa app está activada y si ya lo tenía (el mismo pago avisado en el móvil).
 * Solo funciona si se le da acceso a notificaciones en el reloj.
 */
class WatchPaymentListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        runCatching { handle(sbn) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Los Galaxy Watch paran servicios para ahorrar batería: se pide volver a conectarlo
        runCatching { requestRebind(ComponentName(this, WatchPaymentListener::class.java)) }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName !in WALLETS && sbn.packageName != PLAY_SERVICES) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0 || (n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        val body = listOfNotNull(
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            lines,
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        ).filter { it.isNotBlank() }.joinToString("\n")
        // Solo avisos con un importe. De Play Services, que publica muchas otras cosas, solo los que tienen dinero.
        val all = "$title $body"
        if (!Regex("""\d""").containsMatchIn(all)) return
        if (sbn.packageName == PLAY_SERVICES && !MONEY.containsMatchIn(all)) return
        val posted = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val time = n.`when`.takeIf { it > 0 && it <= posted + 60_000 } ?: posted
        val id = MessageDigest.getInstance("SHA-256")
            .digest("${sbn.packageName}|${sbn.key}|$time|$title|$body".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(40)
        synchronized(recent) {
            if (!recent.add(id)) return
            if (recent.size > 30) recent.remove(recent.first())
        }
        WatchSync.sendNotice(this, WatchNotice(id, sbn.packageName, title, body, time))
    }

    companion object {
        private val recent = LinkedHashSet<String>()

        /**
         * En Wear OS el pago contactless de Google Wallet lo hace Play Services, que es quien publica el aviso
         * «Pago realizado» (en los Galaxy Watch, también). Igual que en el móvil (PaymentApps).
         */
        const val PLAY_SERVICES = "com.google.android.gms"

        private val MONEY = Regex("""\d\s?(€|EUR|\$|USD|£|GBP)|(€|EUR|\$|USD|£|GBP)\s?\d""", RegexOption.IGNORE_CASE)

        val WALLETS = setOf(
            "com.google.android.apps.walletnfcrel",
            "com.samsung.android.samsungpay.gear",
            "com.samsung.android.spay",
        )

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, WatchPaymentListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
