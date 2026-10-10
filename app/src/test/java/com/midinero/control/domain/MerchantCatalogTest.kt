package com.midinero.control.domain

import com.midinero.control.data.DefaultCategories as C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantCatalogTest {

    private fun name(m: String) = MerchantCatalog.find(m)?.name

    @Test
    fun `reconoce la marca con el texto de la notificacion`() {
        assertEquals("Mercadona", name("MERCADONA S.A. 4432 VALENCIA"))
        assertEquals("Amazon", name("AMZN Mktp ES*2K4L1"))
        assertEquals("Netflix", name("NETFLIX.COM"))
        assertEquals("El Corte Inglés", name("EL CORTE INGLES CASTELLANA"))
        assertEquals("McDonald's", name("McDonalds Gran Via"))
        assertEquals("Repsol", name("Repsol E.S. 1234"))
    }

    @Test
    fun `gana la marca mas especifica`() {
        assertEquals("Uber Eats", name("UBER EATS"))
        assertEquals("Uber", name("Uber BV"))
        assertEquals("Prime Video", name("Amazon Prime"))
        assertEquals("Apple Store", name("Apple Store Sol"))
        assertEquals("Movistar Plus+", name("Movistar Plus"))
    }

    @Test
    fun `pasarelas de pago delante del comercio`() {
        assertEquals("Netflix", name("PAYPAL *NETFLIX"))
        assertEquals("Spotify", name("PayPal *Spotify AB"))
    }

    @Test
    fun `la marca en medio no cuenta como el comercio`() {
        assertNull(name("Bar Orange"))
        assertNull(name("Farmacia Dia"))
        assertNull(name("Cafetería Pepe"))
        assertNull(name(""))
    }

    @Test
    fun `la categoria sale del catalogo y de las palabras clave`() {
        assertEquals(C.SUPERMERCADO, Categorizer.suggestCategoryName("Ahorramas"))
        assertEquals(C.RESTAURANTES, Categorizer.suggestCategoryName("Five Guys Madrid"))
        assertEquals(C.OCIO, Categorizer.suggestCategoryName("Basic-Fit Spain"))
        assertEquals(C.SALUD, Categorizer.suggestCategoryName("Farmacia Dia"))
        assertEquals(C.TRANSPORTE, Categorizer.suggestCategoryName("RYANAIR DAC"))
    }

    @Test
    fun `catalogo bien formado`() {
        val categories = setOf(
            C.SUPERMERCADO, C.COMIDA_DOMICILIO, C.RESTAURANTES, C.GASOLINA, C.SUSCRIPCIONES,
            C.TRANSPORTE, C.COMPRAS, C.HOGAR, C.SALUD, C.OCIO,
        )
        assertTrue(MerchantCatalog.all.size >= 200)
        MerchantCatalog.all.forEach { m ->
            assertTrue(m.name, m.category in categories)
            assertTrue(m.name, Regex("#[0-9A-F]{6}").matches(m.color) && Regex("#[0-9A-F]{6}").matches(m.onColor))
            assertTrue(m.name, m.mark.length in 1..4)
            assertTrue(m.name, m.aliases.isNotEmpty())
        }
        val names = MerchantCatalog.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        // Cada marca se reconoce por su propio nombre
        MerchantCatalog.all.forEach { assertEquals(it.name, it.name, name(it.name)) }
    }
}
