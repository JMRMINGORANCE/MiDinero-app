package com.midinero.control.domain

/**
 * Nombres de las categorías que trae la app. La clave es el nombre en español; en inglés se crean con su
 * traducción. Para encontrar una categoría por defecto se acepta cualquiera de los dos nombres.
 */
object CategoryNames {
    val english: Map<String, String> = mapOf(
        "Comida a domicilio" to "Food delivery",
        "Restaurantes" to "Restaurants",
        "Supermercado" to "Groceries",
        "Gasolina" to "Fuel",
        "Ocio" to "Leisure",
        "Transporte" to "Transport",
        "Compras" to "Shopping",
        "Suscripciones" to "Subscriptions",
        "Salud" to "Health",
        "Hogar" to "Home",
        "Otros" to "Other",
        "Nómina" to "Salary",
        "Otros ingresos" to "Other income",
    )

    /** Nombre con el que se crea una categoría por defecto en el idioma actual. */
    fun localized(spanish: String): String = if (Lang.english) english[spanish] ?: spanish else spanish

    /** ¿[name] es la categoría por defecto [spanish], en cualquiera de los dos idiomas? */
    fun matches(name: String?, spanish: String): Boolean =
        name != null && (name.equals(spanish, ignoreCase = true) || name.equals(english[spanish], ignoreCase = true))
}
