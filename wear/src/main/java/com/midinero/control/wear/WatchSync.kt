package com.midinero.control.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.midinero.control.link.WatchEntry
import com.midinero.control.link.WatchLink
import com.midinero.control.link.WatchNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Comunicación con el móvil por la capa de datos de Wear OS. Nada sale del reloj y del móvil emparejado. */
object WatchSync {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Lee el último resumen que haya y pide al móvil uno nuevo. Devuelve false si no hay móvil conectado. */
    suspend fun refresh(context: Context): Boolean = runCatching {
        publishInfo(context)
        val client = Wearable.getDataClient(context)
        val items = client.dataItems.await()
        try {
            items.filter { it.uri.path == WatchLink.STATE }
                .mapNotNull { runCatching { DataMapItem.fromDataItem(it).dataMap.getString(WatchLink.KEY) }.getOrNull() }
                .forEach { onState(context, it) }
        } finally {
            items.release()
        }
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.forEach { node ->
            runCatching { Wearable.getMessageClient(context).sendMessage(node.id, WatchLink.REFRESH, ByteArray(0)).await() }
        }
        nodes.isNotEmpty()
    }.getOrDefault(false)

    /** Guarda el apunte en la cola local y lo deja en la capa de datos: llega al móvil en cuanto haya conexión. */
    fun add(context: Context, amountCents: Long, categoryId: Long?, emoji: String, name: String, color: Int, note: String = "") {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        WatchStore.addPending(context, PendingEntry(id, amountCents, categoryId, emoji, note.ifBlank { name }, color, now))
        val entry = WatchEntry(id, amountCents, categoryId, note.take(60), now)
        scope.launch {
            runCatching {
                val request = PutDataMapRequest.create(WatchLink.ADD + id).apply {
                    dataMap.putString(WatchLink.KEY, entry.toJson())
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(request).await()
            }
        }
    }

    /** Cuenta al móvil cómo está el reloj (si tiene acceso a notificaciones y su versión). */
    fun publishInfo(context: Context) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val json = org.json.JSONObject()
            .put("listener", WatchPaymentListener.isEnabled(context))
            .put("version", version)
            .put("updated", System.currentTimeMillis())
            .toString()
        scope.launch {
            runCatching {
                val request = PutDataMapRequest.create(WatchLink.WATCH_INFO).apply {
                    dataMap.putString(WatchLink.KEY, json)
                }.asPutDataRequest()
                Wearable.getDataClient(context).putDataItem(request).await()
            }
        }
    }

    /** Reenvía al móvil un aviso de pago visto en el reloj. */
    fun sendNotice(context: Context, notice: WatchNotice) {
        scope.launch {
            runCatching {
                val request = PutDataMapRequest.create(WatchLink.NOTICE + notice.id).apply {
                    dataMap.putString(WatchLink.KEY, notice.toJson())
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(request).await()
            }
        }
    }

    /** Llega un resumen: se guarda, se refrescan tarjeta y complicación y se limpia lo ya confirmado. */
    fun onState(context: Context, json: String) {
        val before = WatchStore.state.value.updated
        val state = WatchStore.applyState(context, json)
        if (state.updated == before) return
        refreshSurfaces(context)
        if (state.acked.isEmpty()) return
        scope.launch {
            runCatching {
                val client = Wearable.getDataClient(context)
                val items = client.dataItems.await()
                val done = try {
                    items.filter { item ->
                        val path = item.uri.path.orEmpty()
                        path.startsWith(WatchLink.ADD) && path.removePrefix(WatchLink.ADD) in state.acked
                    }.map { it.uri }
                } finally {
                    items.release()
                }
                done.forEach { runCatching { client.deleteDataItems(it).await() } }
            }
        }
    }

    fun refreshSurfaces(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(SpendTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, TodayComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}

/** Recibe el resumen del móvil aunque la app del reloj esté cerrada. */
class WatchListener : WearableListenerService() {
    override fun onMessageReceived(event: com.google.android.gms.wearable.MessageEvent) {
        if (event.path != WatchLink.ALERT) return
        runCatching {
            val o = org.json.JSONObject(String(event.data))
            WatchAlerts.show(this, o.optString("title"), o.optString("text"))
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchLink.STATE }
            .mapNotNull { runCatching { DataMapItem.fromDataItem(it.dataItem).dataMap.getString(WatchLink.KEY) }.getOrNull() }
            .forEach { WatchSync.onState(this, it) }
    }
}
