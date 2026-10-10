package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ChecksTest {
    private val zone = ZoneId.systemDefault()
    private fun at(date: LocalDate, minute: Int = 0) = date.atTime(12, minute).atZone(zone).toInstant().toEpochMilli()
    private var nextId = 1L
    private fun exp(date: LocalDate, cents: Long, merchant: String = "Tienda", minute: Int = 0, src: ExpenseSource = ExpenseSource.GOOGLE_PAY, cat: Long? = 1) =
        Expense(id = nextId++, amountCents = cents, merchant = merchant, timestamp = at(date, minute), source = src, categoryId = cat)

    @Test
    fun `duplicados mismo importe en pocos minutos`() {
        val d = LocalDate.of(2025, 9, 10)
        val a = exp(d, 1250, "MERCADONA", 0)
        val b = exp(d, 1250, "Mercadona Valencia", 3)
        val c = exp(d, 1250, "Otro", 40) // demasiado tarde
        val m1 = exp(d, 500, "Bar", 0, ExpenseSource.MANUAL)
        val m2 = exp(d, 500, "Bar", 1, ExpenseSource.MANUAL) // dos manuales: los has apuntado tú
        val found = Duplicates.find(listOf(a, b, c, m1, m2))
        assertEquals(setOf(a.id, b.id), found)
        assertTrue(Duplicates.find(listOf(a, b), ignored = setOf(b.id)).isEmpty())
    }

    @Test
    fun `subida de precio de una suscripcion`() {
        val today = LocalDate.of(2025, 9, 12)
        val list = listOf(
            exp(LocalDate.of(2025, 7, 10), 1299, "NETFLIX.COM"),
            exp(LocalDate.of(2025, 8, 10), 1299, "Netflix.com"),
            exp(LocalDate.of(2025, 9, 10), 1399, "NETFLIX.COM"),
            // Supermercado: importes que cambian sin ser una subida de precio
            exp(LocalDate.of(2025, 7, 3), 4000, "Mercadona"),
            exp(LocalDate.of(2025, 8, 20), 5500, "Mercadona"),
            exp(LocalDate.of(2025, 9, 1), 7000, "Mercadona"),
        )
        val changes = PriceChanges.detect(list, today)
        assertEquals(1, changes.size)
        assertEquals(1299L, changes[0].oldCents)
        assertEquals(1399L, changes[0].newCents)
        assertEquals(7, changes[0].percent)
        assertTrue(PriceChanges.detect(list, today.plusDays(90)).isEmpty())
    }

    @Test
    fun `comparacion con la media`() {
        val sept = YearMonth.of(2025, 9)
        val list = listOf(
            exp(LocalDate.of(2025, 7, 5), 10000, cat = 1),
            exp(LocalDate.of(2025, 8, 5), 20000, cat = 1),
            exp(LocalDate.of(2025, 9, 5), 30000, cat = 1),
        )
        val c = CategoryAverages.compare(sept, list, today = LocalDate.of(2025, 10, 2)).first()
        assertEquals(15000L, c.averageCents)
        assertEquals(30000L, c.projectedCents)
        assertEquals(100, c.deltaPercent)
    }

    @Test
    fun arrastre() {
        assertEquals(3000L, Rollover.bonus(10000, 7000, true))
        assertEquals(0L, Rollover.bonus(10000, 12000, true))
        assertEquals(0L, Rollover.bonus(10000, 0, false))
    }

    @Test
    fun `texto dictado`() {
        assertEquals("12,50 café", SpokenEntry.normalize("doce con cincuenta café"))
        assertEquals("12,50 café", SpokenEntry.normalize("12 con 50 euros en café"))
        assertEquals("25 gasolina", SpokenEntry.normalize("veinte y cinco euros de gasolina"))
        assertEquals("25 gasolina", SpokenEntry.normalize("veinticinco euros en gasolina"))
        assertEquals("3 una cerveza", SpokenEntry.normalize("tres euros una cerveza"))
        assertEquals("12,50 café ayer", SpokenEntry.normalize("12,50 € café ayer"))
        assertEquals("una cerveza 3", SpokenEntry.normalize("una cerveza 3 euros"))
        assertEquals("taxi 8,05", SpokenEntry.normalize("taxi ocho euros con cinco"))
    }

    @Test
    fun `importe y concepto dictados para el reloj`() {
        assertEquals(1250L to "Café", SpokenEntry.amountAndNote("doce con cincuenta café"))
        assertEquals(2000L to "Gasolina", SpokenEntry.amountAndNote("veinte euros de gasolina"))
        assertEquals(399L to "Pan", SpokenEntry.amountAndNote("3,99 pan"))
        assertEquals(500L to "", SpokenEntry.amountAndNote("cinco euros"))
        assertEquals(null, SpokenEntry.amountAndNote("café con leche"))
    }

    @Test
    fun `dictado con centenas, miles y separador de miles`() {
        assertEquals("120 cena", SpokenEntry.normalize("ciento veinte euros en cena"))
        assertEquals("2500 sofá", SpokenEntry.normalize("dos mil quinientos euros sofá"))
        assertEquals("12,50 café", SpokenEntry.normalize("doce coma cinco café"))
        assertEquals(1_500_000L, SpokenEntry.amountAndNote("15.000 € coche")!!.first)
        assertEquals(120_000L, SpokenEntry.amountAndNote("€1,200 sofa")!!.first)
        assertEquals(1250L, SpokenEntry.amountAndNote("12,50 café")!!.first)
    }

    @Test
    fun `duplicado entre importado del banco y pago detectado el mismo dia`() {
        val day = java.time.LocalDate.of(2025, 9, 10)
        fun at(h: Int) = day.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val detected = com.midinero.control.data.Expense(id = 1, amountCents = 4520, merchant = "Mercadona", categoryId = null, timestamp = at(18), source = com.midinero.control.data.ExpenseSource.GOOGLE_PAY)
        val imported = com.midinero.control.data.Expense(id = 2, amountCents = 4520, merchant = "COMPRA TARJ MERCADONA", categoryId = null, timestamp = at(12), source = com.midinero.control.data.ExpenseSource.IMPORTED)
        assertEquals(setOf(1L, 2L), Duplicates.find(listOf(detected, imported)))
    }

    @Test
    fun `dos filas iguales del banco no son duplicados`() {
        val noon = java.time.LocalDate.of(2025, 9, 10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val a = Expense(id = 1, amountCents = 150, merchant = "CAFETERIA LUNA", categoryId = null, timestamp = noon, source = ExpenseSource.IMPORTED)
        assertTrue(Duplicates.find(listOf(a, a.copy(id = 2))).isEmpty())
    }
}
