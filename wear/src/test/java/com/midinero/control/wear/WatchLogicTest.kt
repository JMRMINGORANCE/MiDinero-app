package com.midinero.control.wear

import com.midinero.control.link.WatchCategory
import com.midinero.control.link.WatchEntry
import com.midinero.control.link.WatchMovement
import com.midinero.control.link.WatchNotice
import com.midinero.control.link.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchLogicTest {

    @Test
    fun teclado() {
        var t = ""
        "12".forEach { t = typeKey(t, it) }
        t = typeKey(t, '.')
        t = typeKey(t, '5')
        assertEquals("12.5", t)
        assertEquals(1250L, typedToCents(t))
        t = typeKey(t, '0'); t = typeKey(t, '9') // solo dos decimales
        assertEquals("12.50", t)
        assertEquals("0.", typeKey("", '.'))
        assertEquals("7", typeKey("0", '7'))
        assertEquals("123456", typeKey("123456", '7'))
        assertEquals(0L, typedToCents(""))
        assertEquals(5L, typedToCents("0.05"))
    }

    @Test
    fun importes() {
        assertEquals("1.234,50 €", formatMoney(123450, english = false))
        assertEquals("€1,234.50", formatMoney(123450, english = true))
        assertEquals("12 €", formatMoney(1200, english = false))
        assertEquals("1.234,5 €", typedLabel("1234.5", english = false))
        assertEquals("0 €", typedLabel("", english = false))
        assertEquals("1.234 €", compactMoney("1.234,50 €"))
        assertEquals("€1,234", compactMoney("€1,234.50"))
        assertEquals("24 €", compactMoney("24 €"))
    }

    @Test
    fun resumenIdaYVuelta() {
        val state = WatchState(
            updated = 42, english = true, monthLabel = "Octubre", spent = "640 €", budget = "1.000 €", budgetRatio = 0.64f, pace = 0.5f,
            todayLabel = "Hoy puedes gastar", todayValue = "24 €", todayNote = "Llevas 6 € hoy", versus = "12 % menos", curve = listOf(0, 100, 250),
            recent = listOf(WatchMovement("Mercadona", "23,40 €", "🛒", 0xFF22AA55.toInt(), "13:05", false)),
            categories = listOf(WatchCategory(3, "Comida", "🍽️", 0xFFFF8800.toInt(), "120 €", "200 €", 0.6f)),
            quick = listOf(WatchCategory(3, "Comida", "🍽️", 0xFFFF8800.toInt())), acked = listOf("abc"), detection = true,
        )
        assertEquals(state, WatchState.parse(state.toJson()))
        assertTrue(WatchState.parse("no es json").isEmpty)
        assertTrue(WatchState.parse(null).isEmpty)
    }

    @Test
    fun apuntesValidos() {
        val e = WatchEntry("0f8fad5b-d9cb-469f-a165-70867728950e", 1250, 3, "", System.currentTimeMillis())
        assertEquals(e, WatchEntry.parse(e.toJson()))
        assertTrue(e.isValid)
        assertNull(WatchEntry.parse(e.toJson())!!.copy(categoryId = null).categoryId)
        assertFalse(e.copy(amountCents = 0).isValid)
        assertFalse(e.copy(id = "../../x").isValid)
        assertFalse(e.copy(time = System.currentTimeMillis() + 3_600_000).isValid)
        val n = WatchNotice("a".repeat(40), "com.google.android.apps.walletnfcrel", "Mercadona", "12,50 €", 1)
        assertEquals(n, WatchNotice.parse(n.toJson()))
        assertNull(WatchNotice.parse(n.copy(id = "x y").toJson()))
    }
}
