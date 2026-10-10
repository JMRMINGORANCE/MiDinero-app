package com.midinero.control.wear

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import com.midinero.control.link.WatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Apunte hecho en el reloj que el móvil aún no ha confirmado. */
data class PendingEntry(
    val id: String,
    val amountCents: Long,
    val categoryId: Long?,
    val emoji: String,
    val name: String,
    val color: Int,
    val time: Long,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("a", amountCents).put("c", categoryId ?: -1L)
        .put("e", emoji).put("n", name).put("k", color).put("t", time)

    companion object {
        fun from(o: JSONObject) = PendingEntry(
            o.optString("id"), o.optLong("a"), o.optLong("c", -1L).takeIf { it > 0 }, o.optString("e"),
            o.optString("n"), o.optInt("k"), o.optLong("t"),
        )
    }
}

/**
 * Lo que guarda el reloj: el último resumen del móvil (para verlo sin conexión) y los apuntes pendientes.
 * La interfaz, la tarjeta y la complicación leen de aquí.
 */
object WatchStore {
    private const val FILE = "watch"
    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state
    private val _pending = MutableStateFlow<List<PendingEntry>>(emptyList())
    val pending: StateFlow<List<PendingEntry>> = _pending
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _state.value = WatchState.parse(p.getString("state", null))
        _pending.value = readPending(p)
    }

    fun current(context: Context): WatchState {
        init(context)
        return _state.value
    }

    /** Guarda un resumen nuevo (si es más reciente) y quita de pendientes lo que el móvil ya confirmó. */
    @Synchronized
    fun applyState(context: Context, json: String): WatchState {
        init(context)
        val next = WatchState.parse(json)
        if (next.isEmpty || next.updated < _state.value.updated) return _state.value
        _state.value = next
        prefs?.edit { putString("state", json) }
        if (_pending.value.any { it.id in next.acked }) savePending(_pending.value.filterNot { it.id in next.acked })
        return next
    }

    @Synchronized
    fun addPending(context: Context, entry: PendingEntry) {
        init(context)
        savePending(_pending.value + entry)
    }

    private fun savePending(list: List<PendingEntry>) {
        _pending.value = list
        prefs?.edit { putString("pending", JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()) }
    }

    private fun readPending(p: SharedPreferences): List<PendingEntry> = runCatching {
        val a = JSONArray(p.getString("pending", "[]"))
        (0 until a.length()).map { PendingEntry.from(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    /** Idioma: el que use la app del móvil; antes de la primera sincronización, el del reloj. */
    val english: Boolean
        get() = if (_state.value.isEmpty) Locale.getDefault().language != "es" else _state.value.english
}

/** Textos de la app del reloj en el idioma de la app del móvil. */
fun tr(es: String, en: String) = if (WatchStore.english) en else es

/** Importe con el formato de la app: «1.234,50 €» o «€1,234.50». */
fun formatMoney(cents: Long, english: Boolean = WatchStore.english): String {
    val units = cents / 100
    val decimals = (cents % 100).toInt()
    val group = if (english) ',' else '.'
    val decimal = if (english) '.' else ','
    val whole = units.toString().reversed().chunked(3).joinToString(group.toString()).reversed()
    val number = if (decimals == 0) whole else "$whole$decimal${decimals.toString().padStart(2, '0')}"
    return if (english) "€$number" else "$number €"
}

/** Versión corta para la complicación: sin céntimos («1.234 €»). */
fun compactMoney(text: String): String = text.replace(Regex("""[.,]\d{2}(?=\D*$)"""), "")
