package com.midinero.control.domain

import com.midinero.control.data.CustomMerchant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomMerchantsTest {
    private val paco = CustomMerchant(id = 1, name = "Bar Paco", aliases = "PACO TAPAS SL")
    private val fruteria = CustomMerchant(id = 2, name = "Frutería Lola")

    @Test
    fun reconocePorNombreYAlias() {
        assertEquals(paco, CustomMerchants.find(listOf(paco, fruteria), "BAR PACO 1234 MADRID"))
        assertEquals(paco, CustomMerchants.find(listOf(paco, fruteria), "Pago en PACO TAPAS SL"))
        assertEquals(fruteria, CustomMerchants.find(listOf(paco, fruteria), "FRUTERIA LOLA"))
        assertNull(CustomMerchants.find(listOf(paco, fruteria), "Mercadona"))
    }

    @Test
    fun noConfundeConPalabrasSueltas() {
        assertNull(CustomMerchants.find(listOf(paco), "Bar Manolo"))
        assertNull(CustomMerchants.find(listOf(paco), ""))
    }

    @Test
    fun ganaElTextoMasLargo() {
        val bar = CustomMerchant(id = 3, name = "Paco")
        assertEquals(paco, CustomMerchants.find(listOf(bar, paco), "BAR PACO"))
    }

    @Test
    fun limpiaAliasYSiglas() {
        assertEquals("bar paco sl, PACO TAPAS", CustomMerchants.cleanAliases(" bar paco sl,  , PACO TAPAS, Bar Paco SL"))
        assertEquals("BP", CustomMerchants.initials("Bar Paco"))
        assertEquals("Lo", CustomMerchants.initials("lola"))
    }

    @Test
    fun comparaLaFotoPorContenido() {
        val a = paco.copy(photo = byteArrayOf(1, 2, 3))
        val b = paco.copy(photo = byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != paco)
    }
}
