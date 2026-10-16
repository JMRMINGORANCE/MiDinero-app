package com.midinero.control.watch

import androidx.core.content.edit
import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.midinero.control.MiDineroApp
import com.midinero.control.domain.PaymentParser
import com.midinero.control.link.WatchEntry
import com.midinero.control.link.WatchLink
import com.midinero.control.link.WatchNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Puente con la app del reloj. El reloj deja apuntes y avisos de pago como DataItems; aquí se guardan
 * (una sola vez cada uno, aunque lleguen repetidos) y se publica el resumen que muestra el reloj.
 * En móviles sin Google Play Services no hace nada y el resto de la app funciona igual.
 */
object WatchBridge {
    private const val GOOGLE_PLAY_SERVICES = "com.google.android.gms"
    private const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
    private const val WORK = "watch-sync"
    private const val ACKED = "watch_acked"

    private fun available(context: Context) = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /** Sincroniza en segundo plano (recoge apuntes del reloj y publica el resumen). */
    fun publishSoon(context: Context) {
        if (!available(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<WatchSyncWorker>().build(),
        )
    }

    /** Si la última sincronización vio un reloj con MiDinero (los avisos de límite se mandan entonces al reloj). */
    fun hasWatchApp(context: Context): Boolean =
        context.getSharedPreferences(ACKED, Context.MODE_PRIVATE).getBoolean("watch_app", false)

    /**
     * Manda un aviso de límite al reloj, que vibra y lo muestra con su estilo. El aviso del móvil se marca como
     * «solo en el móvil» para no recibirlo dos veces en el reloj.
     */
    fun sendAlert(context: Context, title: String, text: String) {
        if (!available(context)) return
        val app = context.applicationContext
        alertScope.launch {
            runCatching {
                val nodes = Wearable.getCapabilityClient(app)
                    .getCapability(WatchLink.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                val body = org.json.JSONObject().put("title", title).put("text", text).toString().toByteArray()
                nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, WatchLink.ALERT, body).await() }
            }
        }
    }

    private val alertScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Lo que el reloj cuenta de sí mismo: si tiene acceso a notificaciones (null si no se sabe). */
    suspend fun watchListenerEnabled(context: Context): Boolean? {
        if (!available(context)) return null
        return runCatching {
            val items = Wearable.getDataClient(context).dataItems.await()
            try {
                items.filter { it.uri.path == WatchLink.WATCH_INFO }
                    .mapNotNull { json(it)?.let { j -> org.json.JSONObject(j) } }
                    .maxByOrNull { it.optLong("updated") }
                    ?.optBoolean("listener")
            } finally {
                items.release()
            }
        }.getOrNull()
    }

    /** Nombre del reloj con MiDinero instalado que está conectado ahora, o null. */
    suspend fun connectedWatch(context: Context): String? {
        if (!available(context)) return null
        return runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(WatchLink.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
                .nodes.firstOrNull()?.displayName
        }.getOrNull()
    }

    private fun json(item: com.google.android.gms.wearable.DataItem): String? =
        runCatching { DataMapItem.fromDataItem(item).dataMap.getString(WatchLink.KEY) }.getOrNull()

    /** Recoge todo lo pendiente que haya dejado el reloj y publica el resumen. */
    internal suspend fun sync(app: MiDineroApp) {
        val client = Wearable.getDataClient(app)
        val acked = ArrayDeque(app.getSharedPreferences(ACKED, Context.MODE_PRIVATE).getString("ids", "")!!
            .split(',').filter { it.isNotBlank() })
        val items = client.dataItems.await()
        val done = mutableListOf<Uri>()
        try {
            for (item in items) {
                val path = item.uri.path.orEmpty()
                when {
                    path.startsWith(WatchLink.ADD) -> {
                        // Un reloj con la hora adelantada no pierde el apunte: se guarda con la hora del móvil
                        val entry = WatchEntry.parse(json(item))?.let { it.copy(time = minOf(it.time, System.currentTimeMillis())) }
                        if (entry != null && entry.isValid) {
                            app.repository.addFromWatch("watch:${entry.id}", entry.amountCents, entry.categoryId, entry.note, entry.time)
                        }
                        // Se confirma también lo que no vale, para que no se quede «Enviando…» para siempre en el reloj
                        if (entry != null) acked.addLast(entry.id)
                        done += item.uri
                    }
                    path.startsWith(WatchLink.NOTICE) -> {
                        WatchNotice.parse(json(item))?.let { ingestNotice(app, it) }
                        done += item.uri
                    }
                }
            }
        } finally {
            items.release()
        }
        while (acked.size > 60) acked.removeFirst()
        app.getSharedPreferences(ACKED, Context.MODE_PRIVATE).edit { putString("ids", acked.joinToString(",")) }
        // Lo ya guardado se quita de la capa de datos (si no se pudiera, el reloj lo borra al ver la confirmación).
        done.forEach { uri -> runCatching { client.deleteDataItems(uri).await() } }
        publish(app, client, acked.toList())
        // ¿Hay un reloj con la app? (para decidir dónde mostrar los avisos de límite)
        runCatching {
            val nodes = Wearable.getCapabilityClient(app).getCapability(WatchLink.WATCH_CAPABILITY, CapabilityClient.FILTER_ALL).await().nodes
            app.getSharedPreferences(ACKED, Context.MODE_PRIVATE).edit { putBoolean("watch_app", nodes.isNotEmpty()) }
        }
    }

    private suspend fun ingestNotice(app: MiDineroApp, notice: WatchNotice) {
        val enabled = app.prefs.enabledPaymentApps
        // Samsung Wallet del reloj sigue a la opción de Samsung Wallet del móvil; los pagos de Google Wallet del reloj
        // los avisa Play Services, que cuenta si está activada cualquiera de las dos opciones de Google.
        val accepted = notice.packageName in enabled ||
            (notice.packageName == "com.samsung.android.samsungpay.gear" && "com.samsung.android.spay" in enabled) ||
            (notice.packageName == GOOGLE_PLAY_SERVICES && GOOGLE_WALLET in enabled)
        if (!accepted) return
        val appName = if (notice.packageName == GOOGLE_PLAY_SERVICES) "Google Wallet" else com.midinero.control.domain.PaymentApps.nameOf(notice.packageName)
        val payment = PaymentParser.parse(notice.title, notice.body)
        if (payment == null) {
            // Como en el móvil: los avisos con importe que no se entienden se guardan para revisarlos y apuntarlos a mano
            if (com.midinero.control.domain.PaymentApps.looksLikeMoney("${notice.title} ${notice.body}")) {
                app.prefs.addUnrecognized(com.midinero.control.data.UnrecognizedNotification("$appName ⌚", notice.title, notice.body, notice.time))
            }
            return
        }
        val raw = listOf("$appName ⌚", notice.title, notice.body)
            .joinToString(" | ")
        if (app.repository.addDetectedPayment(
                payment, raw, notice.time, "watch:${notice.id}",
                // Los pagos del reloj van con el método de la app del móvil (Google Wallet, Samsung Wallet…)
                sourcePackage = when (notice.packageName) {
                    GOOGLE_PLAY_SERVICES -> GOOGLE_WALLET
                    "com.samsung.android.samsungpay.gear" -> "com.samsung.android.spay"
                    else -> notice.packageName
                },
            ) != null) {
            app.prefs.lastDetectionAt = System.currentTimeMillis()
        }
    }

    private suspend fun publish(app: MiDineroApp, client: DataClient, acked: List<String>) {
        val state = WatchSnapshot.build(app, acked)
        val request = PutDataMapRequest.create(WatchLink.STATE).apply {
            dataMap.putString(WatchLink.KEY, state.toJson())
        }.asPutDataRequest().setUrgent()
        client.putDataItem(request).await()
    }
}

class WatchSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        WatchBridge.sync(applicationContext as MiDineroApp)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}

/** Recibe lo que manda el reloj aunque la app esté cerrada. */
class PhoneWatchListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val fromWatch = events.any {
            it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path.orEmpty().let { p ->
                p.startsWith(WatchLink.ADD) || p.startsWith(WatchLink.NOTICE)
            }
        }
        if (fromWatch) WatchBridge.publishSoon(this)
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WatchLink.REFRESH) WatchBridge.publishSoon(this)
    }
}
