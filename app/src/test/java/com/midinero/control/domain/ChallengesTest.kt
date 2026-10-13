package com.midinero.control.domain

import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ChallengesTest {
    private val today = LocalDate.of(2026, 10, 11)
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun spend(day: Int, cents: Long = 1000, cat: Long? = 1, month: Int = 10, source: ExpenseSource = ExpenseSource.MANUAL) =
        Expense(amountCents = cents, merchant = "x", categoryId = cat, timestamp = at(LocalDate.of(2026, month, day)), source = source)

    @Test
    fun diasSinGastos() {
        // Días cerrados: 1..10 → gastos los días 2 y 5; el cargo fijo del 3 no cuenta
        val list = listOf(spend(2), spend(5), spend(3, source = ExpenseSource.RECURRING))
        val p = Challenges.evaluate(Challenge("a", ChallengeType.NO_SPEND_DAYS, 8), list, today)
        assertEquals(8L, p.current)
        assertEquals(ChallengeState.COMPLETED, p.state)
        assertEquals("a:2026-10", p.achievementKey)
        val hard = Challenges.evaluate(Challenge("b", ChallengeType.NO_SPEND_DAYS, 30), list, today)
        assertEquals(ChallengeState.FAILED, hard.state)
    }

    @Test
    fun limiteDeCategoria() {
        val created = at(LocalDate.of(2026, 8, 20))
        val c = Challenge("c", ChallengeType.CATEGORY_CAP, 5000, categoryId = 1, createdAt = created)
        val p = Challenges.evaluate(c, listOf(spend(4, 2000), spend(10, 2000, month = 9), spend(6, 9000, cat = 2)), today)
        assertEquals(2000L, p.current)
        assertEquals(ChallengeState.IN_PROGRESS, p.state)
        // Septiembre entero con el reto y por debajo del límite
        assertEquals("c:2026-09", p.achievementKey)
        val over = Challenges.evaluate(c, listOf(spend(4, 6000)), today)
        assertEquals(ChallengeState.FAILED, over.state)
        // Creado a mitad de septiembre: septiembre no cuenta
        val recent = Challenges.evaluate(c.copy(createdAt = at(LocalDate.of(2026, 9, 15))), emptyList(), today)
        assertNull(recent.achievementKey)
    }

    @Test
    fun rachaSinCategoria() {
        val c = Challenge("d", ChallengeType.CATEGORY_STREAK, 7, categoryId = 1, createdAt = at(LocalDate.of(2026, 9, 1)))
        val p = Challenges.evaluate(c, listOf(spend(2)), today)
        // Último gasto el 2: racha desde el 3 → 8 días
        assertEquals(8L, p.current)
        assertEquals(ChallengeState.COMPLETED, p.state)
        val short = Challenges.evaluate(c, listOf(spend(9)), today)
        assertEquals(1L, short.current)
        assertEquals(ChallengeState.IN_PROGRESS, short.state)
    }
}
