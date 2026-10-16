package com.midinero.control.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un perfil: datos completamente aparte (gastos, categorías, ajustes, tickets y copias). El 0 es el principal. */
data class Profile(val id: Int, val name: String, val emoji: String)

/** Funciones extra que se activan en Ajustes → Activar más funciones (desactivadas al principio). */
data class Features(val paymentMethods: Boolean = false, val profiles: Boolean = false)

/**
 * Perfiles y funciones extra. Esto se guarda aparte de los ajustes de cada perfil (es común a todos).
 * Cada perfil tiene su base de datos, sus ajustes y su carpeta de tickets; el principal usa los nombres de siempre,
 * así que activar perfiles no mueve ni cambia nada de lo que ya había.
 */
object Profiles {
    private const val STORE = "midinero_profiles"
    const val MAIN = 0

    /** Perfil con el que se ha abierto la app (lo fija MiDineroApp al arrancar). */
    @Volatile
    var current: Int = MAIN

    internal val featuresMutable = MutableStateFlow(Features())
    val features: StateFlow<Features> = featuresMutable

    /** Sufijo de los ficheros de un perfil: nada para el principal. */
    fun suffix(id: Int): String = if (id == MAIN) "" else "_p$id"

    fun databaseName(id: Int): String = if (id == MAIN) AppDatabase.FILE_NAME else "midinero_p$id.db"
    fun prefsName(id: Int): String = "midinero_prefs" + suffix(id)
    fun receiptsDir(id: Int): String = "receipts" + suffix(id)

    /** Comienzo del nombre de las copias de un perfil. No empieza igual que las de otro, para no borrarse entre ellas. */
    fun backupPrefix(id: Int, kind: String): String = if (id == MAIN) "MiDinero-$kind-" else "MiDinero-p$id-$kind-"

    private fun store(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun activeId(context: Context): Int = store(context).getInt("active", MAIN).takeIf { id -> list(context).any { it.id == id } } ?: MAIN

    fun loadFeatures(context: Context): Features = store(context).let {
        Features(paymentMethods = it.getBoolean("feature_payment_methods", false), profiles = it.getBoolean("feature_profiles", false))
    }.also { featuresMutable.value = it }

    fun setFeatures(context: Context, features: Features) {
        store(context).edit {
            putBoolean("feature_payment_methods", features.paymentMethods)
            putBoolean("feature_profiles", features.profiles)
        }
        featuresMutable.value = features
    }

    /** Todos los perfiles, el principal primero. */
    fun list(context: Context): List<Profile> {
        val s = store(context)
        val main = Profile(MAIN, s.getString("main_name", null) ?: com.midinero.control.domain.tr("Personal", "Personal"), s.getString("main_emoji", null) ?: "👤")
        val others = runCatching {
            val arr = JSONArray(s.getString("profiles", "[]"))
            (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Profile(it.getInt("id"), it.getString("name"), it.optString("emoji", "💼")) } }
        }.getOrDefault(emptyList())
        return listOf(main) + others.filter { it.id != MAIN }
    }

    private fun saveOthers(context: Context, profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.filter { it.id != MAIN }.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji)) }
        store(context).edit { putString("profiles", arr.toString()) }
    }

    fun rename(context: Context, profile: Profile) {
        if (profile.id == MAIN) store(context).edit { putString("main_name", profile.name); putString("main_emoji", profile.emoji) }
        else saveOthers(context, list(context).map { if (it.id == profile.id) profile else it })
    }

    /**
     * Crea un perfil vacío. Se lleva el aspecto y la seguridad del perfil actual (idioma, tema, bloqueo, moneda…),
     * y no pasa por la bienvenida.
     */
    fun create(context: Context, name: String, emoji: String): Profile {
        val all = list(context)
        val id = (all.maxOf { it.id } + 1).coerceAtLeast(1)
        val profile = Profile(id, name.trim(), emoji)
        saveOthers(context, all + profile)
        val from = context.getSharedPreferences(prefsName(current), Context.MODE_PRIVATE)
        context.getSharedPreferences(prefsName(id), Context.MODE_PRIVATE).edit {
            clear()
            for (key in COPIED_KEYS) {
                when (val v = from.all[key]) {
                    is Boolean -> putBoolean(key, v)
                    is String -> putString(key, v)
                    is Int -> putInt(key, v)
                    is Long -> putLong(key, v)
                }
            }
            putBoolean("onboarding_done", true)
        }
        return profile
    }

    /** Borra un perfil con todos sus datos (nunca el principal ni el que está abierto). */
    fun delete(context: Context, id: Int): Boolean {
        if (id == MAIN || id == current) return false
        saveOthers(context, list(context).filter { it.id != id })
        context.deleteDatabase(databaseName(id))
        context.deleteSharedPreferences(prefsName(id))
        context.deleteSharedPreferences("budget_alerts" + suffix(id))
        context.deleteSharedPreferences("recurring_reminders" + suffix(id))
        File(context.filesDir, receiptsDir(id)).deleteRecursively()
        if (store(context).getInt("active", MAIN) == id) store(context).edit { putInt("active", MAIN) }
        return true
    }

    /** Elige el perfil con el que se abrirá la app (hay que reiniciarla: ver ProfileRestart). */
    fun setActive(context: Context, id: Int) {
        store(context).edit(commit = true) { putInt("active", id) }
    }

    /** Ajustes que un perfil nuevo copia del actual: solo aspecto, idioma, moneda y seguridad. */
    private val COPIED_KEYS = listOf("language", "theme_mode", "app_lock", "secure_screen", "hide_amounts", "main_currency")
}
