package com.midinero.control

import com.midinero.control.domain.Lang
import android.app.Application
import android.os.StrictMode
import com.midinero.control.data.AppDatabase
import com.midinero.control.data.AppPrefs
import com.midinero.control.domain.MerchantCatalog
import com.midinero.control.domain.Period
import com.midinero.control.data.ExpenseRepository
import com.midinero.control.service.AppNotifications
import com.midinero.control.service.BackupWorker
import com.midinero.control.service.RecurringWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.midinero.control.watch.WatchBridge
import com.midinero.control.widget.SpendingWidget

open class MiDineroApp : Application() {

    // Un fallo en segundo plano (base de datos llena, fila corrupta…) se registra en vez de cerrar la app.
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> android.util.Log.e("MiDinero", "Fallo en segundo plano", e) }
    )

    lateinit var repository: ExpenseRepository
        private set

    lateinit var prefs: AppPrefs
        private set

    /** Nombre del fichero de la base de datos (los tests usan uno distinto en cada ejecución). */
    protected open val databaseName: String = AppDatabase.FILE_NAME

    override fun onCreate() {
        super.onCreate()
        com.midinero.control.service.CrashLog.install(this)
        if (BuildConfig.DEBUG) {
            // En desarrollo, avisa en el log de accesos a disco o red en el hilo principal y de fugas.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
        prefs = AppPrefs(this)
        // «Ocultar importes» también en widgets y avisos aunque no se haya abierto la app (MainActivity lo afina luego)
        com.midinero.control.domain.amountsHidden = { prefs.hideAmounts }
        Lang.english = Lang.resolve(prefs.language, resources.configuration.locales[0].language)
        Period.startDay = prefs.periodStartDay
        com.midinero.control.domain.Currencies.base = prefs.mainCurrency
        repository = ExpenseRepository(AppDatabase.build(this, databaseName), this, prefs)
        AppNotifications.createChannels(this)
        appScope.launch {
            repository.ensureDefaults()
            repository.generateDueRecurring()
            if (prefs.merchantCatalogApplied < MerchantCatalog.VERSION) {
                repository.categorizeUncategorized()
                prefs.merchantCatalogApplied = MerchantCatalog.VERSION
            }
            if (!prefs.topUpsCleaned) {
                repository.removeDetectedTopUps()
                prefs.topUpsCleaned = true
            }
        }
        // Tus comercios (con su foto) a mano para las listas
        appScope.launch { repository.customMerchants.collect { com.midinero.control.data.CustomMerchantsState.mutable.value = it } }
        // Retos de ahorro: se comprueban al cambiar los gastos (y cada día en RecurringWorker)
        appScope.launch {
            combine(repository.allExpenses, prefs.changes) { _, _ -> Unit }.collectLatest {
                delay(2_000)
                runCatching { repository.checkChallenges() }
            }
        }
        appScope.launch {
            repository.allExpenses.collectLatest { list ->
                com.midinero.control.data.TagsState.mutable.value = com.midinero.control.domain.Tags.suggestions(list)
            }
        }
        RecurringWorker.schedule(this)
        BackupWorker.schedule(this)
        // El reloj recibe el resumen cada vez que cambian los gastos, las categorías o los ajustes.
        appScope.launch {
            combine(repository.allExpenses, prefs.changes, repository.categories) { _, _, _ -> Unit }
                .collectLatest {
                    delay(800)
                    WatchBridge.publishSoon(this@MiDineroApp)
                }
        }
        // Mantiene el widget al día cuando cambian los gastos o los ajustes (de cualquier mes: el proceso puede
        // seguir vivo semanas con el lector de notificaciones y el periodo actual cambia).
        appScope.launch {
            combine(repository.allExpenses, prefs.changes) { _, _ -> Unit }
                .collectLatest {
                    delay(300)
                    SpendingWidget.refresh(this@MiDineroApp)
                }
        }
    }
}

