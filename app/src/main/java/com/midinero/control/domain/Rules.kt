package com.midinero.control.domain

import com.midinero.control.data.CustomRule
import com.midinero.control.data.RuleMatch

/**
 * Reglas escritas por el usuario: "si el comercio contiene X -> categoría Y (y llamarlo Z)".
 * Tienen prioridad sobre lo aprendido y sobre las palabras clave.
 */
object RuleMatcher {

    fun matches(rule: CustomRule, merchant: String): Boolean {
        val m = Categorizer.normalize(merchant)
        val p = Categorizer.normalize(rule.pattern)
        if (p.isEmpty()) return false
        return when (rule.match) {
            RuleMatch.CONTAINS -> Regex("""\b${Regex.escape(p)}\b""").containsMatchIn(m) || m.contains(p) && p.length >= 4
            RuleMatch.STARTS_WITH -> m.startsWith(p)
            RuleMatch.EXACT -> Categorizer.merchantKey(merchant) == Categorizer.merchantKey(rule.pattern)
        }
    }

    /** Primera regla que coincide, por prioridad (mayor primero) y luego la más específica. */
    fun find(rules: List<CustomRule>, merchant: String): CustomRule? =
        rules.filter { matches(it, merchant) }
            .sortedWith(compareByDescending<CustomRule> { it.priority }.thenByDescending { it.pattern.length })
            .firstOrNull()

    /** Nombre a mostrar: el alias de la regla si lo tiene. */
    fun displayName(rules: List<CustomRule>, merchant: String): String =
        find(rules, merchant)?.renameTo?.takeIf { it.isNotBlank() } ?: merchant
}
