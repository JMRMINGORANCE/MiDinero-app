package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `interpreta lo que escribe el usuario`() {
        assertEquals(1250L, parseUserAmount("12,5"))
        assertEquals(1250L, parseUserAmount("12.50"))
        assertEquals(123456L, parseUserAmount("1.234,56"))
        assertEquals(1234L * 100, parseUserAmount("1.234"))
        assertEquals(500L, parseUserAmount(" 5 € "))
    }

    @Test
    fun `rechaza vacio y cero`() {
        assertNull(parseUserAmount(""))
        assertNull(parseUserAmount("0"))
        assertNull(parseUserAmount("abc"))
    }

    @Test
    fun `texto para editar`() {
        assertEquals("12", centsToInput(1200))
        assertEquals("12,05", centsToInput(1205))
    }

    @Test
    fun `formato en euros`() {
        val s = formatCents(123456)
        assertTrue(s, s.contains("234,56"))
        assertTrue(s, s.contains("€"))
    }
}
