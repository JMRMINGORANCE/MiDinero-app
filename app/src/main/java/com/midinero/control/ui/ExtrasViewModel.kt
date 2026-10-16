package com.midinero.control.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midinero.control.MiDineroApp
import com.midinero.control.data.Account
import com.midinero.control.data.Expense
import com.midinero.control.data.Features
import com.midinero.control.data.Profile
import com.midinero.control.data.Profiles
import com.midinero.control.domain.currentPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Funciones extra: métodos de pago y perfiles. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtrasViewModel(app: Application) : AndroidViewModel(app) {
    private val miDinero = app as MiDineroApp
    private val repo = miDinero.repository
    val prefs = miDinero.prefs

    val features: StateFlow<Features> = Profiles.features

    val methods: StateFlow<List<Account>> = repo.paymentMethods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _month = MutableStateFlow(currentPeriod())
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    val monthExpenses: StateFlow<List<Expense>> = _month.flatMapLatest { repo.expensesIn(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { if (_month.value < currentPeriod()) _month.value = _month.value.plusMonths(1) }

    /** Guarda un método; [isDefault] y [apps] son sus ajustes (por defecto y apps de pago cuyos avisos van con él). */
    fun saveMethod(method: Account, isDefault: Boolean, apps: Set<String>) = viewModelScope.launch {
        val id = repo.savePaymentMethod(method)
        if (isDefault) prefs.defaultPaymentMethod = id
        else if (prefs.defaultPaymentMethod == id) prefs.defaultPaymentMethod = null
        prefs.paymentMethodApps = prefs.paymentMethodApps.filterValues { it != id } + apps.associateWith { id }
    }

    fun deleteMethod(method: Account) = viewModelScope.launch { repo.deletePaymentMethod(method) }

    fun setFeatures(features: Features) = Profiles.setFeatures(getApplication(), features)

    private val _profiles = MutableStateFlow(Profiles.list(app))
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()
    val currentProfile: Int get() = Profiles.current

    private fun reloadProfiles() { _profiles.value = Profiles.list(getApplication()) }

    fun createProfile(name: String, emoji: String): Profile = Profiles.create(getApplication(), name, emoji).also { reloadProfiles() }
    fun renameProfile(profile: Profile) { Profiles.rename(getApplication(), profile); reloadProfiles() }
    fun deleteProfile(id: Int) { Profiles.delete(getApplication(), id); reloadProfiles() }

    /** Abre la app con otro perfil (se reinicia). */
    fun switchTo(id: Int) {
        if (id == Profiles.current) return
        Profiles.setActive(getApplication(), id)
        ProfileRestart.restart(getApplication())
    }
}
