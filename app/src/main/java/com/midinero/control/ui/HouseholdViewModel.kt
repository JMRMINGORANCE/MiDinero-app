package com.midinero.control.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midinero.control.MiDineroApp
import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.data.Policy
import com.midinero.control.data.PolicyPrice
import com.midinero.control.data.Vehicle
import com.midinero.control.domain.TrackerKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Paneles de casa y coche: combustible, luz, gas, agua, coches y seguros. */
class HouseholdViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as MiDineroApp).repository

    private fun <T> List<T>.start(flow: kotlinx.coroutines.flow.Flow<List<T>>): StateFlow<List<T>> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), this)

    val categories: StateFlow<List<Category>> = emptyList<Category>().start(repo.categories)
    val expenses: StateFlow<List<Expense>> = emptyList<Expense>().start(repo.allExpenses)
    val vehicles: StateFlow<List<Vehicle>> = emptyList<Vehicle>().start(repo.vehicles)
    val policies: StateFlow<List<Policy>> = emptyList<Policy>().start(repo.policies)
    val prices: StateFlow<List<PolicyPrice>> = emptyList<PolicyPrice>().start(repo.policyPrices)

    private fun afterChange() = viewModelScope.launch { runCatching { repo.checkReminders() } }

    fun saveVehicle(vehicle: Vehicle, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        onSaved(repo.saveVehicle(vehicle)); afterChange()
    }

    fun deleteVehicle(vehicle: Vehicle) = viewModelScope.launch { repo.deleteVehicle(vehicle) }

    fun savePolicy(policy: Policy, currentPriceCents: Long?) = viewModelScope.launch {
        repo.savePolicy(policy, currentPriceCents); afterChange()
    }

    fun deletePolicy(policy: Policy) = viewModelScope.launch { repo.deletePolicy(policy) }

    fun renewPolicy(policy: Policy, priceCents: Long, note: String) = viewModelScope.launch {
        repo.renewPolicy(policy, priceCents, note); afterChange()
    }

    fun savePolicyPrice(price: PolicyPrice) = viewModelScope.launch { repo.savePolicyPrice(price) }
    fun deletePolicyPrice(price: PolicyPrice) = viewModelScope.launch { repo.deletePolicyPrice(price) }

    fun setCategoryKind(categoryId: Long, kind: TrackerKind?) = viewModelScope.launch { repo.setCategoryKind(categoryId, kind) }

    fun createKindCategory(kind: TrackerKind, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        onCreated(repo.createKindCategory(kind))
    }
}
