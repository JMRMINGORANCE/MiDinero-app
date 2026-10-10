package com.midinero.control.domain

data class ParsedPayment(
    /** En céntimos de [currency]. */
    val amountCents: Long,
    val merchant: String,
    val currency: String = Currencies.EUR,
    /** Devolución de una compra (el importe es lo devuelto, en positivo). */
    val refund: Boolean = false,
)

/**
 * Extrae importe y comercio del texto de una notificación de pago de Google Pay / Google Wallet.
 *
 * Google no publica el formato exacto y cambia según versión e idioma, así que el parser es
 * tolerante: busca un importe en euros en el título o en el texto y deduce el comercio
 * del título (formato habitual: título = comercio, texto = importe y tarjeta) o de frases
 * como "Has pagado 12,50 € en MERCADONA".
 */
object PaymentParser {

    // Miles con punto, espacio, espacio duro o coma («1.234,56», «1 234,56», «1,234.56» en inglés)
    private const val NUM = """\d{1,3}(?:[.,\s\u00A0]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?"""

    /** «€», «EUR», «euros». */
    private const val EUR = """(?:€|\bEUR(?:OS?)?\b)"""

    private val amountAfter = Regex("""($NUM)\s?$EUR""", RegexOption.IGNORE_CASE)
    // Sin cortar un número a medias («€1,234.56» no es 1,23 €)
    private val amountBefore = Regex("""$EUR\s?($NUM)(?![.,]?\d)""", RegexOption.IGNORE_CASE)

    /** "... en COMERCIO", "... at MERCHANT", "... a COMERCIO" */
    // Un punto solo corta el comercio si acaba la frase («… en MERCADONA. Tu saldo…»), no en «AMAZON.ES» ni «E.S. 1234».
    private const val END = """(?:\s+con\s+|\s+with\s+|\s+usando\s+|\s+mediante\s+|\s+el\s+\d|\.(?=\s+\p{Lu}\p{Ll}|\s*$)|[·•|]|$)"""

    private val merchantAfterPreposition = Regex(
        """(?:\ben|\bat|\bto|\ba)\s+(.+?)$END""",
        RegexOption.IGNORE_CASE,
    )

    /** Recibos domiciliados: «recibo de 45,20 € de IBERDROLA». Solo se usa si no hay «en/a/at…». */
    private val merchantAfterDe = Regex("""^\s*de\s+(.+?)$END""", RegexOption.IGNORE_CASE)

    /**
     * Notificaciones que no son un gasto. Se buscan como palabras (o raíces al principio de palabra),
     * para no descartar comercios como «Café Saldoni» o «Gasolinera Abonos».
     */
    private val ignoreWords = Regex(
        """\b(?:reembols|devoluci[oó]n|devuelt|rechazad|denegad|no se ha podido|fallid|refund|declined|failed|""" +
            """cancelad|has recibido|te han enviado|received|ingreso\b|ingresad|abono\b|anulad|not completed|""" +
            """no completad|promoci|oferta\b|cashback|recompensa|gana\b|consigue|transferencia recibida|""" +
            """n[oó]mina\b|l[ií]mite\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** Devoluciones de una compra: se apuntan restando, salvo que el aviso diga que algo ha fallado. */
    private val refundWords = Regex("""\b(?:reembols|devoluci[oó]n|devuelt|refund)""", RegexOption.IGNORE_CASE)

    /** Lo que, junto a una devolución, sigue sin ser un movimiento (fallos, promociones, nóminas). */
    private val refundIgnoreWords = Regex(
        """\b(?:rechazad|denegad|no se ha podido|fallid|declined|failed|cancelad|anulad|not completed|no completad|""" +
            """promoci|oferta\b|cashback|recompensa|gana\b|consigue|n[oó]mina\b|l[ií]mite\b|solicitud|solicitad|requested|en tr[aá]mite|pendiente|pending)""",
        RegexOption.IGNORE_CASE,
    )

    /** Primer importe en euros que aparezca en el texto, para proponerlo al apuntar a mano un aviso no reconocido. */
    fun guessAmount(text: String): Long? =
        (amountAfter.find(text) ?: amountBefore.find(text))?.groupValues?.get(1)?.let { parseAmountToCents(it) }?.takeIf { it > 0 }

    /** ¿El texto (de un aviso o de una fila del banco) es una devolución que sí se ha hecho? */
    fun isRefundText(text: String): Boolean = refundWords.containsMatchIn(text) && !refundIgnoreWords.containsMatchIn(text)

    /** Frases de saldo o balance: se quitan y, si no queda nada más, el aviso no es un gasto. */
    private val balanceSentence = Regex("""\b(?:saldo|balance)\b""", RegexOption.IGNORE_CASE)

    /** Quita las frases que hablan del saldo («… en AMAZON. Tu saldo es 1.234,00 EUR»). */
    private fun withoutBalance(text: String): String {
        if (!balanceSentence.containsMatchIn(text)) return text
        return text.split(Regex("""\.\s+|\n|\s[·|]\s""")).filterNot { balanceSentence.containsMatchIn(it) }.joinToString("\n")
    }

    /** Títulos genéricos que no son el nombre de un comercio. */
    private val genericTitles = listOf(
        "google pay", "google wallet", "wallet", "pago", "pagado", "compra", "payment",
        "transacción", "transaccion", "tarjeta", "visa", "mastercard",
        "devolución", "devolucion", "reembolso", "refund", "has recibido",
    )

    private val leadingVerb = Regex(
        """(?i)^\s*(has\s+)?(pagado|pago|compra|compraste|cargo|paid|payment|purchase)(\s+(realizad[oa]|de))?\s+(en|a|at|to)\s+""",
    )

    /** «Devolución de AMAZON», «Reembolso en Zara», «Refund from Uber» → solo el comercio. */
    private val leadingRefund = Regex("""(?i)^\s*(devoluci[oó]n|reembolso|refund)\s+(de|en|from|at)\s+""")

    private val notMerchant = Regex(
        """(?i)^(las?|el|hoy|ayer)\s+\d.*|^(tu|su)\s+(tarjeta|cuenta|card)\b.*""",
    )

    /**
     * Recargas de la cuenta de Revolut pagadas con la tarjeta de otro banco («REVOLUT**1234», «Revolut Ltd»):
     * es mover dinero entre cuentas propias, no un gasto.
     */
    fun isTopUp(merchant: String): Boolean = merchant.lowercase(SPANISH).contains("revolut")

    fun parse(title: String?, text: String?): ParsedPayment? {
        val payment = parseRaw(title, text) ?: return null
        if (isTopUp(payment.merchant)) return null
        // Sin comercio reconocible pero el aviso habla de Revolut (p. ej. «Recarga Revolut · 50 €»)
        if (payment.merchant == fallbackMerchant() && "$title $text".lowercase(SPANISH).contains("revolut")) return null
        return payment
    }

    private fun fallbackMerchant() = tr("Pago con Google Pay", "Google Pay payment")

    private fun parseRaw(title: String?, text: String?): ParsedPayment? {
        val t = withoutBalance(title.orEmpty().trim()).trim()
        val body = withoutBalance(text.orEmpty().trim()).trim()
        val all = "$t\n$body"
        if (all.isBlank()) return null
        val refund = refundWords.containsMatchIn(all)
        if (refund) {
            if (refundIgnoreWords.containsMatchIn(all)) return null
        } else if (ignoreWords.containsMatchIn(all)) return null

        val amountMatch = amountAfter.find(body) ?: amountBefore.find(body)
            ?: amountAfter.find(t) ?: amountBefore.find(t) ?: return parseForeign(t, body)
        val amount = parseAmountToCents(amountMatch.groupValues[1]) ?: return null
        if (amount <= 0) return null

        val merchant = findMerchant(t, body) ?: if (refund) tr("Devolución", "Refund") else fallbackMerchant()
        return ParsedPayment(amount, merchant, refund = refund)
    }

    /** Pago en otra moneda ("\$12.50 at Starbucks"): se devuelve en esa moneda para convertirlo luego. */
    private fun parseForeign(title: String, body: String): ParsedPayment? {
        val (code, cents) = Currencies.findForeignAmount(body) ?: Currencies.findForeignAmount(title) ?: return null
        val refund = refundWords.containsMatchIn("$title\n$body")
        val merchant = merchantAfterPreposition.find(body)?.groupValues?.get(1)?.let { clean(it) }
            ?.takeIf { Currencies.findForeignAmount(it) == null }
            ?: title.takeIf { t -> t.isNotBlank() && genericTitles.none { t.lowercase(SPANISH).startsWith(it) } && Currencies.findForeignAmount(t) == null }
                ?.let { clean(it) }
            ?: tr("Pago en $code", "Payment in $code")
        return ParsedPayment(cents, merchant, code, refund = refund)
    }

    private fun findMerchant(title: String, body: String): String? {
        // 1) "Has pagado 12,50 € en MERCADONA"
        for (source in listOf(body, title)) {
            val afterAmount = amountAfter.find(source)?.let { source.substring(it.range.last + 1) }
                ?: amountBefore.find(source)?.let { source.substring(it.range.last + 1) }
            if (afterAmount != null) {
                merchantAfterPreposition.find(afterAmount)?.groupValues?.get(1)?.let { candidate ->
                    clean(candidate)?.let { return it }
                }
                merchantAfterDe.find(afterAmount)?.groupValues?.get(1)?.let { candidate ->
                    clean(candidate)?.let { return it }
                }
            }
        }
        // 2) Título = comercio (formato habitual de Google Wallet)
        if (title.isNotBlank() && amountAfter.find(title) == null && amountBefore.find(title) == null) {
            val lt = title.lowercase(SPANISH)
            if (genericTitles.none { lt == it || lt.startsWith("$it ") && lt.length < it.length + 12 }) {
                clean(title)?.let { return it }
            }
        }
        // 3) Primera línea del texto que no sea el importe ni la tarjeta
        body.lines().map { it.trim() }.firstOrNull { line ->
            line.isNotEmpty() && amountAfter.find(line) == null && amountBefore.find(line) == null &&
                !line.contains("••") && !line.contains("**")
        }?.let { clean(it)?.let { m -> return m } }
        return null
    }

    private fun clean(raw: String): String? {
        val s = raw
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)\s*(con|with)\s+(visa|mastercard|tarjeta|card).*$"""), "")
            .replace(Regex("""[•*]{2,}\s*\d{2,4}"""), "")
            // "Pagado en MERCADONA", "Compra en Zara", "Payment at Starbucks" -> solo el comercio
            .replace(leadingVerb, "")
            .replace(leadingRefund, "")
            .trim()
            .trim('.', ',', ':', ';', '-', '·', '•', '|', ' ')
        if (s.length < 2) return null
        if (s.none { it.isLetter() }) return null
        // "a las 13:05", "el 12/05": no son comercios
        if (notMerchant.matches(s)) return null
        return s.take(60)
    }
}
