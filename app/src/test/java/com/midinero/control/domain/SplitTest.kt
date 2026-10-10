package com.midinero.control.domain

import com.midinero.control.data.Debt
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTest {

    @Test
    fun `reparto a partes iguales con redondeo`() {
        val (mine, others) = Split.equal(10000, listOf("Ana", "Luis"))
        assertEquals(3333L, mine)
        assertEquals(listOf("Ana" to 3334L, "Luis" to 3333L), others)
        assertEquals(10000L, mine + others.sumOf { it.second })
        val (m2, o2) = Split.equal(6000, listOf("Ana"))
        assertEquals(3000L, m2)
        assertEquals(3000L, o2.single().second)
    }

    @Test
    fun `saldos por persona`() {
        val debts = listOf(
            Debt(person = "Ana", amountCents = 2000), Debt(person = "ana ", amountCents = 1500),
            Debt(person = "Luis", amountCents = -800), Debt(person = "Luis", amountCents = 500, settledAt = 1),
            Debt(person = "Marta", amountCents = 1000), Debt(person = "Marta", amountCents = -1000),
        )
        val b = Split.balances(debts)
        assertEquals(listOf(PersonBalance("Ana", 3500, 2), PersonBalance("Luis", -800, 1)), b)
    }

    @Test
    fun `nombres`() {
        assertEquals(listOf("Ana", "Luis", "Marta"), Split.parsePeople("Ana, Luis y Marta"))
        assertEquals(listOf("Ana"), Split.parsePeople(" Ana ,ana"))
    }
}
