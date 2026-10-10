package com.midinero.control.domain

import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Idioma de la app: español (por defecto) o inglés.
 * Los textos se escriben junto a su traducción con [tr], así también sirven fuera de las pantallas
 * (avisos, widget, CSV, resúmenes). La app lo fija al arrancar según el ajuste o el idioma del móvil.
 */
object Lang {
    @Volatile
    var english: Boolean = false

    /** Formato de números y fechas: España o, en inglés, Irlanda (usa el euro: «€12.50»). */
    val locale: Locale get() = if (english) ENGLISH_EU else SPANISH

    /** "system", "es" o "en" → ¿inglés? Con "system", el español (y lenguas de España) se quedan en español. */
    fun resolve(setting: String, systemLanguage: String): Boolean = when (setting) {
        "es" -> false
        "en" -> true
        else -> systemLanguage !in setOf("es", "ca", "gl", "eu")
    }

    private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

    /** Formateador de fechas en el idioma actual, con un patrón para cada idioma. */
    fun dateFormat(es: String, en: String = es): DateTimeFormatter {
        val pattern = if (english) en else es
        val key = "${english}|$pattern"
        return formatters.getOrPut(key) { DateTimeFormatter.ofPattern(pattern, locale) }
    }
}

val ENGLISH_EU: Locale = Locale("en", "IE")

/** Texto en el idioma de la app: `tr("Guardar", "Save")`. */
fun tr(es: String, en: String): String = if (Lang.english) en else es

/** Singular o plural: `plural(n, "gasto", "gastos", "expense", "expenses")` → «3 gastos». */
fun plural(n: Int, esOne: String, esMany: String, enOne: String, enMany: String): String =
    "$n " + if (n == 1) tr(esOne, enOne) else tr(esMany, enMany)
