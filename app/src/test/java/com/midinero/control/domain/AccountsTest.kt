package com.midinero.control.domain

import com.midinero.control.data.Account
import com.midinero.control.data.Expense
import com.midinero.control.data.Transfer
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountsTest {
    @Test
    fun saldoConMovimientosYTraspasos() {
        val bank = Account(id = 1, name = "Banco", initialBalanceCents = 100_000, balanceSince = 1_000)
        val cash = Account(id = 2, name = "Efectivo", initialBalanceCents = 2_000, balanceSince = 1_000)
        val expenses = listOf(
            Expense(amountCents = 5_000, merchant = "Super", timestamp = 2_000, accountId = 1),
            Expense(amountCents = 150_000, merchant = "Nómina", timestamp = 3_000, accountId = 1, isIncome = true),
            // Anterior al saldo inicial: ya está contado en él
            Expense(amountCents = 9_999, merchant = "Viejo", timestamp = 500, accountId = 1),
            Expense(amountCents = 700, merchant = "Café", timestamp = 4_000, accountId = 2),
            Expense(amountCents = 1_000, merchant = "Sin cuenta", timestamp = 4_000),
        )
        val transfers = listOf(Transfer(fromAccountId = 1, toAccountId = 2, amountCents = 5_000, timestamp = 5_000))
        val b = Accounts.balances(listOf(bank, cash), expenses, transfers).associate { it.account.id to it.balanceCents }
        assertEquals(100_000L - 5_000 + 150_000 - 5_000, b[1])
        assertEquals(2_000L - 700 + 5_000, b[2])
    }
}
