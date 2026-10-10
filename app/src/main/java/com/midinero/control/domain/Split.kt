package com.midinero.control.domain

import com.midinero.control.data.Debt

/** Saldo pendiente con una persona. > 0: te debe; < 0: le debes. */
data class PersonBalance(val person: String, val balanceCents: Long, val pending: Int)

object Split {

    /**
     * Reparte [totalCents] a partes iguales entre tú y [people] personas.
     * Los céntimos que sobran se reparten de uno en uno empezando por los demás (tú pagas el redondeo a tu favor).
     * Devuelve tu parte y la de cada persona, en el mismo orden.
     */
    fun equal(totalCents: Long, people: List<String>): Pair<Long, List<Pair<String, Long>>> {
        require(totalCents > 0 && people.isNotEmpty())
        val n = people.size + 1
        val base = totalCents / n
        var remainder = (totalCents % n).toInt()
        val others = people.map { p ->
            val extra = if (remainder > 0) 1 else 0
            remainder -= extra
            p to base + extra
        }
        return base to others
    }

    /** Saldo por persona de las deudas pendientes, de mayor a menor en valor absoluto. */
    fun balances(debts: List<Debt>): List<PersonBalance> =
        debts.filter { it.settledAt == null }
            .groupBy { it.person.trim().lowercase(SPANISH) }
            .map { (_, list) -> PersonBalance(list.first().person.trim(), list.sumOf { it.amountCents }, list.size) }
            .filter { it.balanceCents != 0L }
            .sortedByDescending { kotlin.math.abs(it.balanceCents) }

    /** Separa "Ana, Luis y Marta" en nombres. */
    fun parsePeople(text: String): List<String> =
        text.split(',', ';', '\n').flatMap { it.split(Regex("""\s+(?:y|and|&)\s+""", RegexOption.IGNORE_CASE)) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(SPANISH) }
}
