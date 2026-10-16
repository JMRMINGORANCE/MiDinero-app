package com.midinero.control.domain

import com.midinero.control.data.Account
import com.midinero.control.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentMethodsTest {
    private val bbva = Account(id = 1, name = "Crédito BBVA")
    private val cash = Account(id = 2, name = "Efectivo")
    private val old = Account(id = 3, name = "Vieja", archived = true)
    private val methods = listOf(bbva, cash, old)

    @Test
    fun metodoDeLosPagosDetectados() {
        val byApp = mapOf("com.bbva" to 1L, "com.old" to 3L)
        assertEquals(1L, PaymentMethods.forDetected("com.bbva", byApp, 2L, methods))
        assertEquals(2L, PaymentMethods.forDetected("com.otra", byApp, 2L, methods))
        // Uno archivado ya no se usa: se va al de por defecto
        assertEquals(2L, PaymentMethods.forDetected("com.old", byApp, 2L, methods))
        assertNull(PaymentMethods.forDetected(null, byApp, 9L, methods))
    }

    @Test
    fun totalesPorMetodo() {
        val list = listOf(
            Expense(amountCents = 1000, merchant = "a", timestamp = 0, accountId = 1),
            Expense(amountCents = 3000, merchant = "b", timestamp = 0, accountId = 2),
            Expense(amountCents = 500, merchant = "c", timestamp = 0),
            Expense(amountCents = 99999, merchant = "Nómina", timestamp = 0, isIncome = true, accountId = 1),
        )
        val t = PaymentMethods.totals(list)
        assertEquals(listOf(2L, 1L, null), t.map { it.methodId })
        assertEquals(1000L, t[1].cents)
        assertEquals(1, t[2].count)
    }
}
