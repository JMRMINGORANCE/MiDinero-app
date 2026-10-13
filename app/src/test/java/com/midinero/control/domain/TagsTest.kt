package com.midinero.control.domain

import com.midinero.control.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsTest {
    private val cena = Expense(id = 1, amountCents = 4500, merchant = "Goiko", note = "Cena con Ana #Cumpleaños #amigos", timestamp = 2)
    private val regalo = Expense(id = 2, amountCents = 3000, merchant = "Fnac", note = "Regalo #cumpleaños", timestamp = 1)
    private val gasolina = Expense(id = 3, amountCents = 6000, merchant = "Repsol", note = "#coche", timestamp = 3)

    @Test
    fun leeLasEtiquetas() {
        assertEquals(setOf("cumpleanos", "amigos"), Tags.of(cena.note))
        assertEquals(emptySet<String>(), Tags.of("sin etiquetas, 10 # euros"))
    }

    @Test
    fun totalesPorEtiqueta() {
        val t = Tags.totals(listOf(cena, regalo, gasolina))
        assertEquals(listOf("cumpleanos", "coche", "amigos"), t.map { it.key })
        assertEquals(7500L, t.first().totalCents)
        // Se muestra como se escribió la primera vez
        assertEquals("cumpleaños", t.first().label)
    }

    @Test
    fun buscarPorEtiqueta() {
        val q = SearchQuery.parse("#cumpleaños")
        assertTrue(q.matches(cena, null))
        assertTrue(q.matches(regalo, null))
        assertFalse(q.matches(gasolina, null))
        // Las búsquedas de fecha siguen funcionando
        assertTrue(SearchQuery.parse("esta semana").tags.isEmpty())
    }

    @Test
    fun añadirEtiquetaALaNota() {
        assertEquals("Cena #amigos", Tags.append("Cena", "amigos"))
        assertEquals("Cena #amigos", Tags.append("Cena #amigos", "Amigos"))
        assertEquals("#coche", Tags.append("", "#coche"))
    }
}
