package com.midinero.control.domain

import com.midinero.control.data.Account
import com.midinero.control.data.Expense
import com.midinero.control.data.Transfer

data class AccountBalance(val account: Account, val balanceCents: Long, val spentThisMonthCents: Long)

/** Saldos de tus cuentas: el inicial más lo apuntado en ellas desde ese día y los traspasos. */
object Accounts {
    fun balances(accounts: List<Account>, expenses: List<Expense>, transfers: List<Transfer>, today: java.time.LocalDate = java.time.LocalDate.now()): List<AccountBalance> {
        val period = periodOf(today)
        val byAccount = expenses.filter { it.accountId != null }.groupBy { it.accountId }
        return accounts.map { a ->
            val moves = byAccount[a.id].orEmpty().filter { it.timestamp >= a.balanceSince }
            val net = moves.sumOf { if (it.isIncome) it.amountCents else -it.amountCents }
            val inbound = transfers.filter { it.toAccountId == a.id && it.timestamp >= a.balanceSince }.sumOf { it.amountCents }
            val outbound = transfers.filter { it.fromAccountId == a.id && it.timestamp >= a.balanceSince }.sumOf { it.amountCents }
            val spent = byAccount[a.id].orEmpty().filter { !it.isIncome && periodOf(dateOf(it.timestamp)) == period }.sumOf { it.amountCents }
            AccountBalance(a, a.initialBalanceCents + net + inbound - outbound, spent)
        }
    }
}
