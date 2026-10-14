package com.midinero.control.domain

import java.text.NumberFormat
import java.util.Currency
import kotlin.math.roundToLong

/** Moneda admitida para gastos de viaje. [approxEurRate]: cuántos euros vale 1 unidad (aproximado, editable). */
data class CurrencyInfo(val code: String, private val es: String, val symbols: List<String>, val approxEurRate: Double) {
    val name: String get() = tr(es, englishNames[code] ?: es)
}

private val englishNames = mapOf(
    "EUR" to "Euro",
    "USD" to "US dollar", "GBP" to "Pound sterling", "CHF" to "Swiss franc", "MXN" to "Mexican peso", "JPY" to "Yen",
    "MAD" to "Moroccan dirham", "SEK" to "Swedish krona", "DKK" to "Danish krone", "NOK" to "Norwegian krone",
    "PLN" to "Polish złoty", "CZK" to "Czech koruna", "TRY" to "Turkish lira",
)

/**
 * Gastos en otras monedas. La app no usa Internet, así que el cambio lo pone el usuario
 * (se recuerda el último usado); de partida se propone un cambio aproximado.
 */
object Currencies {
    const val EUR = "EUR"
    const val USD = "USD"
    const val MXN = "MXN"

    /** Monedas que se pueden elegir como principal en Ajustes (euro por defecto). */
    val mainChoices = listOf(EUR, USD, MXN)

    /**
     * Moneda principal: en la que se guardan y muestran todos los importes. La fija MiDineroApp al arrancar
     * (y Ajustes al cambiarla). Cambiarla no convierte lo ya apuntado: solo cambia cómo se muestra.
     */
    @Volatile
    var base: String = EUR

    private val euro = CurrencyInfo("EUR", "Euro", listOf("€", "EUR"), 1.0)

    private val everything = listOf(
        CurrencyInfo("USD", "Dólar estadounidense", listOf("US$", "$", "USD"), 0.92),
        CurrencyInfo("GBP", "Libra esterlina", listOf("£", "GBP"), 1.17),
        CurrencyInfo("CHF", "Franco suizo", listOf("CHF"), 1.05),
        CurrencyInfo("MXN", "Peso mexicano", listOf("MX$", "MXN"), 0.05),
        CurrencyInfo("JPY", "Yen", listOf("¥", "JPY"), 0.0062),
        CurrencyInfo("MAD", "Dírham marroquí", listOf("MAD", "DH"), 0.092),
        CurrencyInfo("SEK", "Corona sueca", listOf("SEK"), 0.088),
        CurrencyInfo("DKK", "Corona danesa", listOf("DKK"), 0.134),
        CurrencyInfo("NOK", "Corona noruega", listOf("NOK"), 0.086),
        CurrencyInfo("PLN", "Esloti", listOf("PLN", "zł"), 0.23),
        CurrencyInfo("CZK", "Corona checa", listOf("CZK", "Kč"), 0.040),
        CurrencyInfo("TRY", "Lira turca", listOf("TRY", "₺"), 0.027),
    )

    /** Las otras monedas (todas menos la principal), para gastos de viaje y avisos en otra moneda. */
    val all: List<CurrencyInfo>
        get() = (listOf(euro) + everything).filter { it.code != base }.map { c ->
            // Con pesos como moneda principal, «$» son pesos: el dólar se reconoce por «US$» o «USD»
            if (base == MXN && c.code == USD) c.copy(symbols = c.symbols - "$") else c
        }

    fun byCode(code: String?): CurrencyInfo? = (listOf(euro) + everything).firstOrNull { it.code == code }

    /** Nombre de la moneda principal («Euro», «Dólar estadounidense», «Peso mexicano»). */
    fun mainName(code: String = base): String = byCode(code)?.name ?: code

    /** Símbolo corto de la moneda principal: «€», «$». */
    fun symbol(code: String = base): String = when (code) {
        EUR -> "€"
        USD, MXN -> "$"
        else -> code
    }

    /** Cuántas unidades de la moneda principal vale 1 [code], aproximadamente (editable al apuntar). */
    fun approxRate(code: String): Double {
        val from = byCode(code)?.approxEurRate ?: return 1.0
        val to = byCode(base)?.approxEurRate ?: 1.0
        return from / to
    }

    /** Convierte un importe en otra moneda a céntimos de la moneda principal. */
    fun toBaseCents(originalCents: Long, rate: Double): Long = (originalCents * rate).roundToLong()

    @Deprecated("La moneda principal ya no es siempre el euro", ReplaceWith("toBaseCents(originalCents, rate)"))
    fun toEurCents(originalCents: Long, rate: Double): Long = toBaseCents(originalCents, rate)

    /** Formato local de un importe en otra moneda: "12,00 US$". */
    fun format(originalCents: Long, code: String): String {
        val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
        val f = NumberFormat.getCurrencyInstance(Lang.locale).apply {
            currency = Currency.getInstance(code)
            maximumFractionDigits = digits
            minimumFractionDigits = digits
        }
        return f.format(originalCents / 100.0)
    }

    private val symbolPatterns = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private val symbolPattern: Regex
        get() = symbolPatterns.getOrPut(base) {
            val symbols = all.flatMap { c -> c.symbols.map { it to c.code } }.sortedByDescending { it.first.length }
            val alt = symbols.joinToString("|") { Regex.escape(it.first) }
            Regex("""(?:($alt)\s?(\d[\d.,\s]*\d|\d))|(?:(\d[\d.,\s]*\d|\d)\s?($alt))""")
        }

    /** Busca un importe en otra moneda ("$12.50", "12,00 USD", "£3"). Devuelve (código, céntimos) o null. */
    fun findForeignAmount(text: String): Pair<String, Long>? {
        val symbols = all.flatMap { c -> c.symbols.map { it to c.code } }.toMap()
        for (m in symbolPattern.findAll(text)) {
            val sym = m.groupValues[1].ifEmpty { m.groupValues[4] }
            val num = m.groupValues[2].ifEmpty { m.groupValues[3] }
            // "US$" también contiene "$": vale cualquiera de los dos
            val code = symbols[sym] ?: continue
            val cents = parseAmountToCents(num.trim()) ?: continue
            if (cents > 0) return code to cents
        }
        return null
    }
}
