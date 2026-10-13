package com.midinero.control.domain

import com.midinero.control.data.CustomMerchant
import com.midinero.control.data.CustomRule
import com.midinero.control.data.RuleMatch

/** Tus comercios: cuál corresponde a un pago por su nombre o sus otros textos («BAR PACO SL 1234» → «Bar Paco»). */
object CustomMerchants {

    /** Textos con los que se reconoce: su nombre y sus alias. */
    fun patterns(merchant: CustomMerchant): List<String> =
        (listOf(merchant.name) + merchant.aliases.split(',')).map { it.trim() }.filter { Categorizer.normalize(it).length >= 2 }

    /** El comercio tuyo que corresponde a [merchant], el de texto más largo si varios coinciden. */
    fun find(list: List<CustomMerchant>, merchant: String): CustomMerchant? {
        if (list.isEmpty() || merchant.isBlank()) return null
        return list.mapNotNull { m ->
            patterns(m).filter { RuleMatcher.matches(CustomRule(pattern = it, match = RuleMatch.CONTAINS), merchant) }
                .maxOfOrNull { it.length }?.let { m to it }
        }.maxByOrNull { it.second }?.first
    }

    /** «bar paco sl,  , PACO TAPAS» → «bar paco sl, PACO TAPAS», sin repetidos. */
    fun cleanAliases(text: String): String =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { Categorizer.normalize(it) }.joinToString(", ")

    /**
     * Nombre para guardar un comercio a partir del texto de un pago: hasta el primer código o número y con
     * mayúsculas normales («BAR PACO 1234 MADRID» → «Bar Paco»), para que valga también en sus próximos pagos.
     */
    fun suggestName(merchant: String): String {
        val words = merchant.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val kept = words.takeWhile { w -> w.none { it.isDigit() } && w != "*" }.ifEmpty { words.take(1) }
        val text = kept.joinToString(" ").trim('*', '-', ' ', ',', '.')
        val shouting = text.count { it.isUpperCase() } > text.count { it.isLowerCase() }
        return if (!shouting) text else text.lowercase().split(' ').joinToString(" ") { w ->
            if (w.length <= 2 && w in setOf("de", "la", "el", "y", "e", "a", "en", "of")) w else w.replaceFirstChar { it.uppercase() }
        }.replaceFirstChar { it.uppercase() }
    }

    /** Siglas para el distintivo cuando no tiene foto («Bar Paco» → «BP»). */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
        return when {
            words.isEmpty() -> name.take(1).uppercase()
            words.size == 1 -> words[0].take(2).replaceFirstChar { it.uppercase() }
            else -> words.take(2).joinToString("") { it.take(1).uppercase() }
        }
    }
}
