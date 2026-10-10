package com.midinero.control.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptsTest {
    @Test
    fun `reduce las fotos grandes a potencias de 2`() {
        assertEquals(1, Receipts.sampleSize(1200, 900))
        assertEquals(2, Receipts.sampleSize(4000, 3000))
        assertEquals(4, Receipts.sampleSize(3000, 8000))
    }

    @Test
    fun `tamano final mantiene la proporcion`() {
        assertEquals(1600 to 1200, Receipts.targetSize(4000, 3000))
        assertEquals(600 to 1600, Receipts.targetSize(3000, 8000))
        assertEquals(800 to 600, Receipts.targetSize(800, 600))
    }

    @Test
    fun `detecta ficheros huerfanos`() {
        assertEquals(listOf("b.jpg"), Receipts.orphans(listOf("a.jpg", "b.jpg"), setOf("a.jpg")))
    }
}
