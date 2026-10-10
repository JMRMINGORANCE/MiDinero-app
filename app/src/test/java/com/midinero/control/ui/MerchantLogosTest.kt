package com.midinero.control.ui

import com.midinero.control.domain.MerchantCatalog
import com.midinero.control.ui.screens.MerchantLogos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MerchantLogosTest {

    @Test
    fun claves() {
        assertEquals("el_corte_ingles", MerchantLogos.key("El Corte Inglés"))
        assertEquals("mcdonald_s", MerchantLogos.key("McDonald's"))
        assertEquals("pull_bear", MerchantLogos.key("Pull&Bear"))
        assertEquals("disney", MerchantLogos.key("Disney+"))
    }

    @Test
    fun cadaLogoEsDeUnComercioDelCatalogo() {
        val logos = File("src/main/assets/logos").listFiles().orEmpty().map { it.nameWithoutExtension }
        assertTrue("Faltan los logos", logos.size > 300)
        val keys = MerchantCatalog.all.map { MerchantLogos.key(it.name) }.toSet()
        val orphans = logos.filterNot { it in keys }
        assertTrue("Logos sin comercio: $orphans", orphans.isEmpty())
    }
}
