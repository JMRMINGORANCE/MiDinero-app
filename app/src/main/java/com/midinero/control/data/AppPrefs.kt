package com.midinero.control.data

import android.content.Context
import com.midinero.control.domain.PaymentApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Aviso de una app de pago que MiDinero no ha sabido interpretar. */
data class UnrecognizedNotification(val app: String, val title: String, val text: String, val time: Long)

/**
 * Preferencias sencillas de la app (SharedPreferences).
 * [changes] cambia con cada escritura para que la interfaz se refresque.
 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("midinero_prefs", Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _changes.value++
    }

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = edit { putBoolean("onboarding_done", value) }

    /** Lo que sobra de cada límite un mes se suma al del mes siguiente. */
    var rolloverEnabled: Boolean
        get() = prefs.getBoolean("rollover_enabled", false)
        set(value) = edit { putBoolean("rollover_enabled", value) }

    /** Gastos marcados como «no es un duplicado». */
    var notDuplicates: Set<Long>
        get() = prefs.getStringSet("not_duplicates", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        set(value) = edit { putStringSet("not_duplicates", value.map { it.toString() }.toSet()) }

    /** Versión del catálogo de comercios ya aplicada a los gastos sin categoría (0 = nunca). */
    /** Ya se borraron las recargas de Revolut apuntadas solas por versiones anteriores. */
    var topUpsCleaned: Boolean
        get() = prefs.getBoolean("top_ups_cleaned", false)
        set(value) = edit { putBoolean("top_ups_cleaned", value) }

    var merchantCatalogApplied: Int
        get() = prefs.getInt("merchant_catalog_applied", 0)
        set(value) = edit { putInt("merchant_catalog_applied", value) }

    var lastDetectionAt: Long
        get() = prefs.getLong("last_detection_at", 0L)
        set(value) = edit { putLong("last_detection_at", value) }

    /** Presupuesto total del mes (todas las categorías). 0 = sin presupuesto. */
    var monthlyBudgetCents: Long
        get() = prefs.getLong("monthly_budget", 0L)
        set(value) = edit { putLong("monthly_budget", value) }

    /** % del límite al que avisar antes de pasarse (0 = solo al superarlo). */
    var alertThreshold: Int
        get() = prefs.getInt("alert_threshold", 80)
        set(value) = edit { putInt("alert_threshold", value) }

    var weeklySummary: Boolean
        get() = prefs.getBoolean("weekly_summary", true)
        set(value) = edit { putBoolean("weekly_summary", value) }

    /** Semana (lunes, epochDay) del último resumen enviado. */
    var lastWeeklySummary: Long
        get() = prefs.getLong("last_weekly_summary", 0L)
        set(value) = edit { putLong("last_weekly_summary", value) }

    /** Día en que empieza el mes de gastos (1..28). */
    var periodStartDay: Int
        get() = prefs.getInt("period_start_day", 1)
        set(value) = edit { putInt("period_start_day", value.coerceIn(1, 28)) }

    /** Idioma: "system" (el del móvil), "es" o "en". */
    var language: String
        get() = prefs.getString("language", "system") ?: "system"
        set(value) = edit { putString("language", value) }

    /** "system", "light" o "dark". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = edit { putString("theme_mode", value) }

    var hideAmounts: Boolean
        get() = prefs.getBoolean("hide_amounts", false)
        set(value) = edit { putBoolean("hide_amounts", value) }

    /** Pedir huella / PIN del móvil al abrir la app. */
    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(value) = edit { putBoolean("app_lock", value) }

    /** Ocultar el contenido en apps recientes y bloquear capturas. */
    var secureScreen: Boolean
        get() = prefs.getBoolean("secure_screen", false)
        set(value) = edit { putBoolean("secure_screen", value) }

    /** Avisar de cada pago detectado (si no, se apuntan en silencio salvo que falte la categoría). */
    var notifyDetected: Boolean
        get() = prefs.getBoolean("notify_detected", true)
        set(value) = edit { putBoolean("notify_detected", value) }

    /** Suscripciones sugeridas que el usuario ha descartado (clave de comercio). */
    var ignoredSubscriptions: Set<String>
        get() = prefs.getStringSet("ignored_subscriptions", emptySet())?.toSet() ?: emptySet()
        set(value) = edit { putStringSet("ignored_subscriptions", value) }

    /** Carpeta elegida para las copias automáticas (URI de árbol del selector de Android). */
    var autoBackupUri: String?
        get() = prefs.getString("auto_backup_uri", null)
        set(value) = edit { putString("auto_backup_uri", value) }

    /** Cada cuántos días hacer la copia automática (0 = desactivada). */
    var autoBackupDays: Int
        get() = prefs.getInt("auto_backup_days", 0)
        set(value) = edit { putInt("auto_backup_days", value) }

    /** Última copia guardada (manual o automática). */
    var lastBackupAt: Long
        get() = prefs.getLong("last_backup_at", 0L)
        set(value) = edit { putLong("last_backup_at", value) }

    /** Contraseña con la que se cifran las copias (manuales y automáticas). null = copias sin cifrar. */
    var backupPassword: String?
        get() = prefs.getString("backup_password", null)?.takeIf { it.isNotEmpty() }
        set(value) = edit { putString("backup_password", value?.takeIf { it.isNotEmpty() }) }

    var lastAutoBackupError: String?
        get() = prefs.getString("last_auto_backup_error", null)
        set(value) = edit { putString("last_auto_backup_error", value) }

    /** Pistas de uso que el usuario ya ha cerrado. */
    var dismissedHints: Set<String>
        get() = prefs.getStringSet("dismissed_hints", emptySet())?.toSet() ?: emptySet()
        set(value) = edit { putStringSet("dismissed_hints", value) }

    fun dismissHint(id: String) { dismissedHints = dismissedHints + id }

    /** Ids de recurrentes y metas de ejemplo (los gastos de ejemplo se marcan en rawText). */
    var demoIds: Set<String>
        get() = prefs.getStringSet("demo_ids", emptySet())?.toSet() ?: emptySet()
        set(value) = edit { putStringSet("demo_ids", value) }

    /** Último cambio usado por moneda (euros por unidad). */
    /** Últimas búsquedas en el historial (la más reciente primero). */
    var recentSearches: List<String>
        get() = prefs.getString("recent_searches", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        set(value) = edit { putString("recent_searches", value.joinToString("\n")) }

    fun rateFor(code: String): Double? =
        prefs.getString("rate_$code", null)?.toDoubleOrNull()

    fun setRate(code: String, rate: Double) = edit { putString("rate_$code", rate.toString()) }

    var enabledPaymentApps: Set<String>
        get() = prefs.getStringSet("payment_apps", null)?.toSet() ?: PaymentApps.defaultEnabled
        set(value) = edit { putStringSet("payment_apps", value) }

    fun setPaymentAppEnabled(packageName: String, enabled: Boolean) {
        enabledPaymentApps = if (enabled) enabledPaymentApps + packageName else enabledPaymentApps - packageName
    }

    val unrecognized: List<UnrecognizedNotification>
        get() = runCatching {
            val arr = JSONArray(prefs.getString("unrecognized", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UnrecognizedNotification(o.optString("app"), o.optString("title"), o.optString("text"), o.optLong("time"))
            }
        }.getOrDefault(emptyList())

    /** Guarda los últimos 20 avisos no reconocidos (el más reciente primero). */
    fun addUnrecognized(n: UnrecognizedNotification) {
        val list = (listOf(n) + unrecognized).take(20)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("app", it.app).put("title", it.title.take(200)).put("text", it.text.take(400)).put("time", it.time))
        }
        edit { putString("unrecognized", arr.toString()) }
    }

    fun clearUnrecognized() = edit { remove("unrecognized") }

    /** Quita un aviso de la lista (por ejemplo, tras apuntarlo a mano). */
    fun removeUnrecognized(n: UnrecognizedNotification) {
        val list = unrecognized.filterNot { it == n }
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("app", it.app).put("title", it.title).put("text", it.text).put("time", it.time)) }
        edit { putString("unrecognized", arr.toString()) }
    }

    // ---------- Retos de ahorro ----------

    var challenges: List<com.midinero.control.domain.Challenge>
        get() = runCatching { challengesFromJson(prefs.getString("challenges", "[]")!!) }.getOrDefault(emptyList())
        set(value) = edit { putString("challenges", challengesToJson(value)) }

    /** Logros ya avisados (para no repetir el aviso). */
    var notifiedAchievements: Set<String>
        get() = prefs.getStringSet("challenge_notified", emptySet())!!.toSet()
        set(value) = edit { putStringSet("challenge_notified", value.toList().takeLast(200).toSet()) }

    companion object {
        fun challengesToJson(list: List<com.midinero.control.domain.Challenge>): String = JSONArray().apply {
            list.forEach { c ->
                put(JSONObject().put("id", c.id).put("type", c.type.name).put("target", c.target)
                    .put("categoryId", c.categoryId ?: JSONObject.NULL).put("createdAt", c.createdAt))
            }
        }.toString()

        fun challengesFromJson(text: String): List<com.midinero.control.domain.Challenge> {
            val arr = JSONArray(text)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val type = runCatching { com.midinero.control.domain.ChallengeType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
                com.midinero.control.domain.Challenge(
                    id = o.getString("id"), type = type, target = o.getLong("target"),
                    categoryId = if (o.isNull("categoryId")) null else o.optLong("categoryId"),
                    createdAt = o.optLong("createdAt"),
                )
            }
        }
    }
}
