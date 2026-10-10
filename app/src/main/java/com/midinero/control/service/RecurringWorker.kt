package com.midinero.control.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.midinero.control.MiDineroApp
import com.midinero.control.domain.zone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Revisa varias veces al día si toca apuntar algún recurrente o avisar de uno, aunque no abras la app. */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MiDineroApp
        // Cada tarea por separado: si una falla, las demás se hacen igual y solo se reintenta lo que falló.
        val generated = runCatching { app.repository.generateDueRecurring() }
        runCatching { app.repository.remindUpcoming() }
        runCatching { sendWeeklySummary(app) }
        runCatching { app.repository.cleanReceipts() }
        // El resumen del reloj cambia de día aunque no haya gastos nuevos («Hoy puedes gastar»).
        runCatching { com.midinero.control.watch.WatchBridge.publishSoon(app) }
        val error = generated.exceptionOrNull()
        if (error is kotlinx.coroutines.CancellationException) throw error
        return if (error == null) Result.success() else Result.retry()
    }

    /** Los lunes a partir de las 9:00, un aviso con lo gastado la semana anterior (una vez por semana). */
    private suspend fun sendWeeklySummary(app: MiDineroApp) {
        val prefs = app.prefs
        if (!prefs.weeklySummary) return
        val now = LocalDateTime.now()
        if (now.dayOfWeek != DayOfWeek.MONDAY || now.hour < 9) return
        val thisMonday = now.toLocalDate()
        if (prefs.lastWeeklySummary == thisMonday.toEpochDay()) return
        fun millis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val last = app.repository.spentBetween(millis(thisMonday.minusWeeks(1)), millis(thisMonday))
        val previous = app.repository.spentBetween(millis(thisMonday.minusWeeks(2)), millis(thisMonday.minusWeeks(1)))
        prefs.lastWeeklySummary = thisMonday.toEpochDay()
        if (last > 0) AppNotifications.weeklySummary(app, last, previous)
    }

    companion object {
        private const val NAME = "recurring_expenses"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
