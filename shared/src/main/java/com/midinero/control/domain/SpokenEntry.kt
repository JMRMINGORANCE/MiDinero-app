package com.midinero.control.domain

// Compartido con la app del reloj (está en shared/ para que la compilen los dos módulos).

/** Texto dictado a la app, preparado para el apuntado rápido («doce con cincuenta euros en café»). */
object SpokenEntry {
    private val numbers = mapOf(
        "cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
        "trece" to 13, "catorce" to 14, "quince" to 15, "dieciséis" to 16, "dieciseis" to 16, "diecisiete" to 17,
        "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
        "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90, "cien" to 100,
        "veintiuno" to 21, "veintiún" to 21, "veintidós" to 22, "veintidos" to 22, "veintitrés" to 23, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiséis" to 26, "veintiseis" to 26, "veintisiete" to 27,
        "veintiocho" to 28, "veintinueve" to 29,
        "ciento" to 100, "doscientos" to 200, "doscientas" to 200, "trescientos" to 300, "trescientas" to 300,
        "cuatrocientos" to 400, "cuatrocientas" to 400, "quinientos" to 500, "quinientas" to 500,
        "seiscientos" to 600, "seiscientas" to 600, "setecientos" to 700, "setecientas" to 700,
        "ochocientos" to 800, "ochocientas" to 800, "novecientos" to 900, "novecientas" to 900,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    /** «mil», «thousand»: multiplican lo dicho antes («dos mil») o valen 1000 solos («mil euros»). */
    private val thousands = setOf("mil", "thousand")

    fun normalize(spoken: String): String {
        var s = spoken.trim().lowercase(java.util.Locale("es", "ES")).replace("€", " euros ")
        if (s.none { it.isDigit() }) s = wordsToAmount(s)
        // «12 con 50», «12 euros con 50», «12 coma 5»
        s = Regex("""(\d+)\s*(?:euros?)?\s+(con|coma|and|point)\s+(\d{1,2})\b(?:\s*(?:céntimos|centimos|cents?))?""").replace(s) { m ->
            val decimals = m.groupValues[3]
            val cents = if (decimals.length == 1 && m.groupValues[2] in setOf("coma", "point")) decimals + "0" else decimals.padStart(2, '0')
            "${m.groupValues[1]},$cents"
        }
        return s.replace(Regex("""\beuros?\b"""), " ").replace(Regex("""^\s*(\S+)\s+(?:en|de)\s+"""), "$1 ")
            .replace(Regex("""\s+"""), " ").trim()
    }

    /** Convierte a cifras solo el importe dicho en palabras (al principio o justo antes de «euros»). */
    private fun wordsToAmount(s: String): String {
        val tokens = s.split(' ').filter { it.isNotBlank() }
        var i = 0
        while (i < tokens.size) {
            if (tokens[i] !in numbers && tokens[i] !in thousands) { i++; continue }
            val parsed = amountAt(tokens, i)
            if (parsed != null && (i == 0 || parsed.second)) {
                val (value, _, end) = parsed
                return (tokens.subList(0, i) + value + tokens.subList(end, tokens.size)).joinToString(" ")
            }
            i++
        }
        return s
    }

    /**
     * Número en palabras desde [start]: «doce», «veinte y cinco», «ciento veinte», «dos mil quinientos»,
     * «one hundred twenty». Devuelve (valor, fin).
     */
    private fun numberAt(tokens: List<String>, start: Int): Pair<Int, Int>? {
        var i = start
        var total = 0
        var current = 0
        var any = false
        // Solo se suma lo que encaja detrás («ciento» + «veinte» + «cinco»), no dos números seguidos («doce cinco»)
        fun fits(v: Int) = current == 0 || (current % 100 == 0 && v < 100) || (current % 10 == 0 && v < 10)
        while (i < tokens.size) {
            val t = tokens[i]
            val v = numbers[t]
            when {
                v != null && fits(v) -> { current += v; any = true; i++ }
                (t == "y" || t == "and") && any -> {
                    val next = numbers[tokens.getOrNull(i + 1) ?: ""]
                    if (next != null && fits(next) && next < 10) { current += next; i += 2 } else break
                }
                t == "hundred" && any && current in 1..9 -> { current *= 100; i++ }
                t in thousands && total == 0 && current < 1000 -> { total = maxOf(current, 1) * 1000; current = 0; any = true; i++ }
                else -> break
            }
        }
        return if (any) total + current to i else null
    }

    /**
     * Importe completo desde [start]: número, «euros» opcional y «con/coma» + céntimos opcionales.
     * Devuelve (texto «12,50», si llevaba «euros/con/coma», fin).
     */
    private fun amountAt(tokens: List<String>, start: Int): Triple<String, Boolean, Int>? {
        val (euros, afterNumber) = numberAt(tokens, start) ?: return null
        var i = afterNumber
        var money = false
        if (tokens.getOrNull(i)?.startsWith("euro") == true) { money = true; i++ }
        if (tokens.getOrNull(i) in setOf("con", "coma", "point")) {
            val decimals = numberAt(tokens, i + 1)
            if (decimals != null && decimals.first in 0..99) {
                var end = decimals.second
                if (tokens.getOrNull(end) in setOf("céntimos", "centimos")) end++
                // «doce coma cinco» es 12,50 (como «12 coma 5»); «ocho con cinco» es 8,05
                val tenths = tokens[i] != "con" && decimals.first < 10 && end == i + 2
                val cents = if (tenths) "${decimals.first}0" else decimals.first.toString().padStart(2, '0')
                return Triple("$euros,$cents", true, end)
            }
        }
        return Triple(euros.toString(), money, i)
    }

    /**
     * Importe y concepto de una frase dictada («doce con cincuenta café» → 1250 y «Café»).
     * Devuelve null si no hay un importe claro. Lo usa el reloj, que no tiene el resto del lector del móvil.
     */
    fun amountAndNote(spoken: String): Pair<Long, String>? {
        val text = normalize(spoken)
        // «15.000 coche» o «1,200 sofa»: el separador de miles no se toma como decimales
        val m = Regex("""(?<![\d.,])(\d{1,3}(?:[.,]\d{3})+|\d{1,6})(?:[.,](\d{1,2}))?(?![\d])""").find(text) ?: return null
        val euros = m.groupValues[1].filter { it.isDigit() }.toLongOrNull() ?: return null
        val cents = m.groupValues[2].let { if (it.isEmpty()) 0 else it.padEnd(2, '0').toLong() }
        val total = euros * 100 + cents
        if (total <= 0) return null
        val note = text.removeRange(m.range)
            .replace(Regex("""(?i)\b(en|de|del|el|la|un|una|me|he|gastado|pagado|for|on|at)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim(' ', ',', '.', '-')
            .replaceFirstChar { it.uppercase() }
        return total to note.take(60)
    }
}
