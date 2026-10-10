package com.midinero.control.domain

import java.text.NumberFormat
import java.util.Currency
import kotlin.math.roundToLong

/** Moneda admitida para gastos de viaje. [approxEurRate]: cuántos euros vale 1 unidad (aproximado, editable). */
data class CurrencyInfo(val code: String, private val es: String, val symbols: List<String>, val approxEurRate: Double) {
    val name: String get() = tr(es, englishNames[code] ?: es)
}

private val englishNames = mapOf(
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

    val all = listOf(
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

    fun byCode(code: String?): CurrencyInfo? = all.firstOrNull { it.code == code }

    /** Convierte un importe en otra moneda a céntimos de euro. */
    fun toEurCents(originalCents: Long, rate: Double): Long = (originalCents * rate).roundToLong()

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

    private val symbolPattern: Regex by lazy {
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
