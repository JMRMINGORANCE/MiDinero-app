package com.midinero.control.domain

import java.time.LocalDate

/** Lo que dice el Asistente de Google tras «apunta…» («4 euros de café», «un gasto de 30 en gasolina»). */
object AssistantPhrase {
    private val filler = Regex("""^(?:(?:apunta(?:r|me)?|anota(?:r|me)?|añade|añadir|registra(?:r)?|he\s+gastado|gast[ée]|log|add)\s+)?(?:(?:un|una)\s+)?(?:(?:gasto|pago|compra|expense)\s+(?:de\s+|of\s+)?)?""", RegexOption.IGNORE_CASE)

    fun toEntry(text: String, today: LocalDate = LocalDate.now()): QuickEntry? {
        val stripped = filler.replace(text.trim(), "").replace(Regex("""(?i)\s+en\s+midinero\s*$"""), "").trim()
        if (stripped.isEmpty()) return null
        return QuickEntryParser.parse(SpokenEntry.normalize(stripped), today)
    }
}
