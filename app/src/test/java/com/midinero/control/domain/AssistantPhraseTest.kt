package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Frases que llegan del Asistente de Google («apunta 4 euros de café en MiDinero»). */
class AssistantPhraseTest {
    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun frasesHabladas() {
        val a = AssistantPhrase.toEntry("4 euros de café", today)!!
        assertEquals(400L, a.amountCents)
        assertEquals("café", a.merchant.lowercase())
        val b = AssistantPhrase.toEntry("12,50 en Mercadona ayer", today)!!
        assertEquals(1250L, b.amountCents)
        assertEquals(today.minusDays(1), b.date)
        val c = AssistantPhrase.toEntry("un gasto de 30 euros en gasolina", today)!!
        assertEquals(3000L, c.amountCents)
        assertEquals("gasolina", c.merchant.lowercase())
        assertEquals(2000L, AssistantPhrase.toEntry("veinte euros en el cine", today)?.amountCents)
        assertEquals(1250L, AssistantPhrase.toEntry("apunta doce con cincuenta en café en MiDinero", today)?.amountCents)
        assertNull(AssistantPhrase.toEntry("hola", today))
    }
}
