package com.midinero.control.domain

import com.midinero.control.data.DefaultCategories as C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorizerTest {

    private fun cat(m: String) = Categorizer.suggestCategoryName(m)

    @Test
    fun `comercios habituales`() {
        assertEquals(C.SUPERMERCADO, cat("MERCADONA S.A."))
        assertEquals(C.COMIDA_DOMICILIO, cat("Glovo*Pedido"))
        assertEquals(C.GASOLINA, cat("E.S. Repsol 1234"))
        assertEquals(C.SUSCRIPCIONES, cat("NETFLIX.COM"))
    }

    @Test
    fun `gana la palabra mas especifica`() {
        assertEquals(C.COMIDA_DOMICILIO, cat("Uber Eats"))
        assertEquals(C.TRANSPORTE, cat("Uber BV"))
        assertEquals(C.SUSCRIPCIONES, cat("Amazon Prime"))
        assertEquals(C.COMPRAS, cat("AMZN Mktp ES"))
        assertEquals(C.SALUD, cat("Farmacia Dia"))
        assertEquals(C.SUSCRIPCIONES, cat("Movistar Plus"))
        assertEquals(C.HOGAR, cat("Movistar"))
    }

    @Test
    fun `acentos y mayusculas no importan`() {
        assertEquals(C.RESTAURANTES, cat("CAFETERÍA EL PASO"))
        assertEquals(C.SALUD, cat("Clínica Dental Sonrisas"))
    }

    @Test
    fun `no inventa categoria`() {
        assertNull(cat("Comercio Desconocido XYZ"))
        assertNull(cat(""))
    }

    @Test
    fun `no confunde palabras dentro de otras`() {
        // "bar" no debe coincidir con "Barcelona", ni "dia" con "Media"
        assertNull(cat("Barcelona Tours"))
    }

    @Test
    fun `clave de comercio ignora numeros y simbolos`() {
        assertEquals("mercadona", Categorizer.merchantKey("MERCADONA 1234"))
        assertEquals(Categorizer.merchantKey("Mercadona 5678"), Categorizer.merchantKey("MERCADONA  0001"))
        assertEquals("cafe luna", Categorizer.merchantKey("Café  Luna!"))
    }
}
