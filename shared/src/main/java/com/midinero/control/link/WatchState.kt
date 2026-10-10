package com.midinero.control.link

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lo que comparten el móvil y el reloj por la capa de datos de Wear OS (Bluetooth con el móvil emparejado).
 * El móvil es el único que guarda gastos: el reloj le envía apuntes y recibe un resumen ya calculado y con
 * los importes formateados, así el reloj no necesita saber de monedas, periodos ni presupuestos.
 */
object WatchLink {
    /** Resumen que publica el móvil (un único DataItem que se va reemplazando). */
    const val STATE = "/midinero/v2/state"

    /** Apunte hecho en el reloj: `ADD + id`. Se borra cuando el móvil lo confirma. */
    const val ADD = "/midinero/v2/add/"

    /** Aviso de pago que vio el reloj (Wallet en el reloj): `NOTICE + id`. */
    const val NOTICE = "/midinero/v2/notice/"

    /** Mensaje del móvil al reloj: aviso de límite ({"title","text"} en JSON). */
    const val ALERT = "/midinero/v2/alert"

    /** Estado del reloj para el móvil: {"listener": bool, "version": "…"}. */
    const val WATCH_INFO = "/midinero/v2/watchinfo"

    /** Mensaje del reloj para pedir un resumen nuevo. */
    const val REFRESH = "/midinero/v2/refresh"

    /** Capacidad que anuncia el reloj (res/values/wear.xml) para que el móvil sepa que está instalado. */
    const val WATCH_CAPABILITY = "midinero_watch"

    const val KEY = "json"

    fun t(english: Boolean, es: String, en: String) = if (english) en else es
}

data class WatchMovement(
    val title: String,
    val amount: String,
    val emoji: String,
    val color: Int,
    val time: String,
    val income: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().put("t", title).put("a", amount).put("e", emoji).put("c", color)
        .put("w", time).put("i", income)

    companion object {
        fun from(o: JSONObject) = WatchMovement(
            o.optString("t"), o.optString("a"), o.optString("e"), o.optInt("c"), o.optString("w"), o.optBoolean("i"),
        )
    }
}

data class WatchCategory(
    val id: Long,
    val name: String,
    val emoji: String,
    val color: Int,
    /** Gastado este mes, ya formateado («123,40 €»). */
    val spent: String = "",
    /** Límite formateado o vacío si no tiene. */
    val limit: String = "",
    /** Parte del límite gastada (puede pasar de 1) o -1 si no tiene límite. */
    val ratio: Float = -1f,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("e", emoji).put("c", color)
        .put("s", spent).put("l", limit).put("r", ratio.toDouble())

    companion object {
        fun from(o: JSONObject) = WatchCategory(
            o.optLong("id"), o.optString("n"), o.optString("e"), o.optInt("c"), o.optString("s"), o.optString("l"),
            o.optDouble("r", -1.0).toFloat(),
        )
    }
}

data class WatchState(
    val updated: Long = 0,
    val english: Boolean = false,
    /** El móvil tiene activado «ocultar importes»: el reloj muestra puntos en lugar de cifras. */
    val hidden: Boolean = false,
    val monthLabel: String = "",
    val spent: String = "",
    val budget: String = "",
    /** Parte del presupuesto gastada (puede pasar de 1) o -1 si no hay presupuesto. */
    val budgetRatio: Float = -1f,
    /** Parte del mes ya pasada (0..1), para marcar en el anillo dónde «tocaría» ir. */
    val pace: Float = 0f,
    /** «Hoy puedes gastar» o «Hoy llevas». */
    val todayLabel: String = "",
    val todayValue: String = "",
    val todayOver: Boolean = false,
    /** Frase corta bajo la cifra de hoy («Quedan 12 días · 340 € libres»). */
    val todayNote: String = "",
    /** Frente al mes anterior a estas alturas («12 % menos que en agosto»), vacío si no hay datos. */
    val versus: String = "",
    val versusUp: Boolean = false,
    val curve: List<Long> = emptyList(),
    val recent: List<WatchMovement> = emptyList(),
    val categories: List<WatchCategory> = emptyList(),
    /** Categorías para apuntar desde el reloj, las más usadas primero. */
    val quick: List<WatchCategory> = emptyList(),
    /** Apuntes del reloj ya guardados en el móvil. */
    val acked: List<String> = emptyList(),
    /** Símbolo de la moneda y si va delante («$12») o detrás («12 €»). */
    val currencySymbol: String = "€",
    val symbolFirst: Boolean = false,
    /** Si el reloj puede mandar avisos de pago vistos en él (la detección está activada en el móvil). */
    val detection: Boolean = false,
) {
    val isEmpty: Boolean get() = updated == 0L

    fun toJson(): String = JSONObject()
        .put("v", 2).put("updated", updated).put("english", english).put("hidden", hidden)
        .put("month", monthLabel).put("spent", spent).put("budget", budget).put("ratio", budgetRatio.toDouble()).put("pace", pace.toDouble())
        .put("todayLabel", todayLabel).put("todayValue", todayValue).put("todayOver", todayOver).put("todayNote", todayNote)
        .put("versus", versus).put("versusUp", versusUp)
        .put("curve", JSONArray().apply { curve.forEach { put(it) } })
        .put("recent", JSONArray().apply { recent.forEach { put(it.toJson()) } })
        .put("categories", JSONArray().apply { categories.forEach { put(it.toJson()) } })
        .put("quick", JSONArray().apply { quick.forEach { put(it.toJson()) } })
        .put("acked", JSONArray().apply { acked.forEach { put(it) } })
        .put("symbol", currencySymbol).put("symbolFirst", symbolFirst).put("detection", detection)
        .toString()

    companion object {
        fun parse(json: String?): WatchState {
            if (json.isNullOrBlank()) return WatchState()
            return runCatching {
                val o = JSONObject(json)
                fun <T> list(key: String, read: (JSONObject) -> T): List<T> {
                    val a = o.optJSONArray(key) ?: return emptyList()
                    return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(read) }
                }
                WatchState(
                    updated = o.optLong("updated"),
                    english = o.optBoolean("english"),
                    hidden = o.optBoolean("hidden"),
                    monthLabel = o.optString("month"),
                    spent = o.optString("spent"),
                    budget = o.optString("budget"),
                    budgetRatio = o.optDouble("ratio", -1.0).toFloat(),
                    pace = o.optDouble("pace", 0.0).toFloat(),
                    todayLabel = o.optString("todayLabel"),
                    todayValue = o.optString("todayValue"),
                    todayOver = o.optBoolean("todayOver"),
                    todayNote = o.optString("todayNote"),
                    versus = o.optString("versus"),
                    versusUp = o.optBoolean("versusUp"),
                    curve = o.optJSONArray("curve")?.let { a -> (0 until a.length()).map { a.optLong(it) } }.orEmpty(),
                    recent = list("recent", WatchMovement::from),
                    categories = list("categories", WatchCategory::from),
                    quick = list("quick", WatchCategory::from),
                    acked = o.optJSONArray("acked")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
                    currencySymbol = o.optString("symbol", "€"),
                    symbolFirst = o.optBoolean("symbolFirst"),
                    detection = o.optBoolean("detection"),
                )
            }.getOrDefault(WatchState())
        }
    }
}

/** Apunte hecho en el reloj. */
data class WatchEntry(
    val id: String,
    val amountCents: Long,
    val categoryId: Long?,
    val note: String,
    val time: Long,
) {
    fun toJson(): String = JSONObject().put("v", 2).put("id", id).put("amount", amountCents)
        .put("category", categoryId ?: -1L).put("note", note).put("time", time).toString()

    /** Comprueba que lo recibido tiene sentido antes de guardarlo. */
    val isValid: Boolean
        get() = id.matches(Regex("[A-Za-z0-9-]{8,64}")) && amountCents in 1..100_000_000 &&
            note.length <= 60 && time > 0 && time <= System.currentTimeMillis() + 300_000

    companion object {
        fun parse(json: String?): WatchEntry? = runCatching {
            val o = JSONObject(json ?: return null)
            WatchEntry(
                o.getString("id"), o.getLong("amount"), o.optLong("category", -1L).takeIf { it > 0 },
                o.optString("note"), o.getLong("time"),
            )
        }.getOrNull()
    }
}

/** Aviso de pago que vio el reloj; el móvil lo interpreta con el mismo lector que sus propios avisos. */
data class WatchNotice(
    val id: String,
    val packageName: String,
    val title: String,
    val body: String,
    val time: Long,
) {
    fun toJson(): String = JSONObject().put("v", 2).put("id", id).put("pkg", packageName)
        .put("title", title.take(200)).put("body", body.take(1000)).put("time", time).toString()

    companion object {
        fun parse(json: String?): WatchNotice? = runCatching {
            val o = JSONObject(json ?: return null)
            WatchNotice(o.getString("id"), o.getString("pkg"), o.optString("title"), o.optString("body"), o.getLong("time"))
                .takeIf { it.id.matches(Regex("[A-Za-z0-9-]{8,80}")) && it.time > 0 }
        }.getOrNull()
    }
}
