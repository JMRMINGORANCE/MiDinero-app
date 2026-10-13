package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Marcas añadidas en la 4.13. */
class MerchantCatalogMoreTest {
    @Test
    fun reconoceLasNuevas() {
        assertEquals("Flixbus", MerchantCatalog.find("FLIXBUS 7781 MADRID")?.name)
        assertEquals("Wizz Air", MerchantCatalog.find("WIZZ AIR HU1234")?.name)
        assertEquals("Bet365", MerchantCatalog.find("BET365")?.name)
        assertEquals("Esso", MerchantCatalog.find("ESSO EXPRESS VALLECAS")?.name)
        assertEquals("Etsy", MerchantCatalog.find("PAYPAL *ETSY")?.name)
        assertEquals("Ben & Jerry's", MerchantCatalog.find("Ben & Jerry's Gran Vía")?.name)
        assertEquals("bonÀrea", MerchantCatalog.find("BONAREA GUISSONA")?.name)
    }

    @Test
    fun noHayNombresRepetidos() {
        val names = MerchantCatalog.all.map { Categorizer.normalize(it.name) }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun noConfundeComerciosLocales() {
        assertNull(MerchantCatalog.find("Bar Paco"))
        assertNull(MerchantCatalog.find("Familia Pérez"))
    }
}
