package com.midinero.control.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midinero.control.MiDineroApp
import com.midinero.control.data.Debt
import com.midinero.control.data.Expense
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Cuentas con amigos: quién debe qué. */
class DebtsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as MiDineroApp).repository

    val debts: StateFlow<List<Debt>> = repo.debts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveDebt(debt: Debt) = viewModelScope.launch { repo.saveDebt(debt) }
    fun deleteDebt(debt: Debt) = viewModelScope.launch { repo.deleteDebt(debt) }
    fun restoreDebt(debt: Debt) = viewModelScope.launch { repo.restoreDebt(debt) }
    fun settlePerson(person: String) = viewModelScope.launch { repo.settlePerson(person) }
    fun shareExpense(expense: Expense, people: List<String>) = viewModelScope.launch { repo.shareExpense(expense, people) }
}
