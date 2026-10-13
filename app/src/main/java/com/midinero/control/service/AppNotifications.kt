package com.midinero.control.service

import com.midinero.control.domain.tr
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.midinero.control.R
import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.data.Trip
import com.midinero.control.data.RecurringExpense
import com.midinero.control.domain.formatCents
import com.midinero.control.ui.MainActivity

object AppNotifications {
    private const val CHANNEL_DETECTED = "detected_payments"
    private const val CHANNEL_BUDGET = "budget_alerts"
    private const val CHANNEL_RECURRING = "recurring_charges"
    private const val CHANNEL_SUMMARY = "weekly_summary"
    const val TAG_PAYMENT = "payment"
    private const val TAG_RECURRING = "recurring"
    private const val TAG_BUDGET = "budget"

    /** Ruta a abrir al tocar un aviso o un acceso directo ("home", "expenses?edit=12"...). */
    const val EXTRA_OPEN = "open_screen"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DETECTED, tr("Gastos detectados", "Detected expenses"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = tr("Aviso cuando MiDinero registra un pago detectado", "Notification when MiDinero logs a detected payment")
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGET, tr("Límites de gasto", "Spending limits"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Aviso al acercarte o superar el límite de una categoría", "Notification when you get close to or go over a category limit")
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECURRING, tr("Cargos recurrentes", "Recurring charges"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("Aviso cuando se apunta un alquiler, suscripción u otro cargo fijo", "Notification when rent, a subscription or another recurring charge is logged")
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, tr("Resumen semanal", "Weekly summary"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("Cuánto gastaste la semana pasada, cada lunes", "How much you spent last week, every Monday")
            }
        )
    }

    fun paymentId(expense: Expense): Int = (expense.id % Int.MAX_VALUE).toInt()

    /**
     * Aviso de pago detectado. Si no se reconoce la categoría, ofrece [quickCategories] como botones
     * para asignarla sin abrir la app.
     */
    fun paymentDetected(
        context: Context,
        expense: Expense,
        category: Category?,
        quickCategories: List<Category> = emptyList(),
        /** Devolución sin compra original: se pregunta de qué categoría era. */
        askCategory: Boolean = false,
    ) {
        val text = if (expense.isRefund) {
            val back = formatCents(-expense.amountCents)
            tr("Te devuelven $back de ${expense.merchant}", "$back refunded by ${expense.merchant}") +
                when {
                    askCategory || category == null -> tr(" · ¿De qué categoría era la compra?", " · Which category was the purchase?")
                    else -> " · ${category.emoji} ${category.name}"
                }
        } else if (category != null) {
            tr("${formatCents(expense.amountCents)} en ${expense.merchant} · ${category.emoji} ${category.name}", "${formatCents(expense.amountCents)} at ${expense.merchant} · ${category.emoji} ${category.name}")
        } else {
            tr("${formatCents(expense.amountCents)} en ${expense.merchant} · ¿De qué categoría es?", "${formatCents(expense.amountCents)} at ${expense.merchant} · Which category is it?")
        }
        val id = paymentId(expense)
        val actions = quickCategories.take(3).map { cat ->
            val intent = Intent(context, CategoryActionReceiver::class.java).apply {
                action = CategoryActionReceiver.ACTION_ASSIGN
                putExtra(CategoryActionReceiver.EXTRA_EXPENSE_ID, expense.id)
                putExtra(CategoryActionReceiver.EXTRA_CATEGORY_ID, cat.id)
            }
            val pending = PendingIntent.getBroadcast(
                context, "assign${expense.id}_${cat.id}".hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            NotificationCompat.Action(0, "${cat.emoji} ${cat.name}", pending)
        }
        notify(
            context,
            tag = TAG_PAYMENT,
            id = id,
            channel = CHANNEL_DETECTED,
            title = if (expense.isRefund) tr("Devolución registrada", "Refund logged") else tr("Gasto registrado", "Expense logged"),
            text = text,
            open = "expenses?edit=${expense.id}",
            actions = actions,
        )
    }

    fun cancelPayment(context: Context, expenseId: Long) {
        NotificationManagerCompat.from(context).cancel(TAG_PAYMENT, (expenseId % Int.MAX_VALUE).toInt())
    }

    fun recurringCharged(context: Context, recurring: RecurringExpense, category: Category?) {
        val cat = category?.let { " · ${it.emoji} ${it.name}" } ?: ""
        notify(
            context,
            tag = TAG_RECURRING,
            id = (recurring.id % Int.MAX_VALUE).toInt(),
            channel = CHANNEL_RECURRING,
            title = if (recurring.isIncome) tr("Ingreso apuntado", "Income logged") else tr("Cargo fijo apuntado", "Recurring charge logged"),
            text = "${recurring.name}: ${formatCents(recurring.amountCents)}$cat",
            open = "expenses",
        )
    }

    fun recurringReminder(context: Context, recurring: RecurringExpense) {
        notify(
            context,
            tag = "reminder",
            id = (recurring.id % Int.MAX_VALUE).toInt(),
            channel = CHANNEL_RECURRING,
            title = if (recurring.isIncome) tr("Mañana cobras ${recurring.name}", "Tomorrow you get paid ${recurring.name}") else tr("Mañana se cobra ${recurring.name}", "${recurring.name} is charged tomorrow"),
            text = tr("${formatCents(recurring.amountCents)} · Toca para ver tus cargos fijos", "${formatCents(recurring.amountCents)} · Tap to see your recurring charges"),
            open = "recurring",
        )
    }

    /** Una semana antes de que se renueve un cargo anual (seguros, dominios, suscripciones anuales…). */
    fun renewalReminder(context: Context, recurring: RecurringExpense) {
        notify(
            context,
            tag = "renewal",
            id = (recurring.id % Int.MAX_VALUE).toInt(),
            channel = CHANNEL_RECURRING,
            title = tr("${recurring.name} se renueva en una semana", "${recurring.name} renews in a week"),
            text = tr("${formatCents(recurring.amountCents)} al año · Si no lo quieres, aún estás a tiempo de darte de baja", "${formatCents(recurring.amountCents)} a year · If you don't want it, there's still time to cancel"),
            open = "recurring",
        )
    }

    fun globalBudgetAlert(context: Context, spentCents: Long, budgetCents: Long) {
        val over = spentCents - budgetCents
        val title = if (over >= 0) tr("Has superado tu presupuesto del mes", "You've gone over your monthly budget") else tr("Llevas el ${spentCents * 100 / budgetCents} % de tu presupuesto", "You've used ${spentCents * 100 / budgetCents} % of your budget")
        val text = if (over >= 0) {
            tr("Llevas ${formatCents(spentCents)} de ${formatCents(budgetCents)} (te has pasado ${formatCents(over)})", "You've spent ${formatCents(spentCents)} of ${formatCents(budgetCents)} (over by ${formatCents(over)})")
        } else {
            tr("Llevas ${formatCents(spentCents)} de ${formatCents(budgetCents)}. Te quedan ${formatCents(-over)}", "You've spent ${formatCents(spentCents)} of ${formatCents(budgetCents)}. ${formatCents(-over)} left")
        }
        val watch = com.midinero.control.watch.WatchBridge.hasWatchApp(context)
        notify(context, tag = TAG_BUDGET, id = 0, channel = CHANNEL_BUDGET, title = title, text = text, open = "home", localOnly = watch)
        if (watch) com.midinero.control.watch.WatchBridge.sendAlert(context, title, text)
    }

    fun tripBudgetAlert(context: Context, trip: Trip, spentCents: Long, budgetCents: Long) {
        val over = spentCents - budgetCents
        val title = if (over >= 0) tr("${trip.emoji} Has superado el presupuesto de ${trip.name}", "${trip.emoji} You've gone over the budget for ${trip.name}")
        else tr("${trip.emoji} ${trip.name}: ${spentCents * 100 / budgetCents} % del presupuesto", "${trip.emoji} ${trip.name}: ${spentCents * 100 / budgetCents} % of the budget")
        val text = if (over >= 0) {
            tr("Llevas ${formatCents(spentCents)} de ${formatCents(budgetCents)} (te has pasado ${formatCents(over)})", "You've spent ${formatCents(spentCents)} of ${formatCents(budgetCents)} (over by ${formatCents(over)})")
        } else {
            tr("Llevas ${formatCents(spentCents)} de ${formatCents(budgetCents)}. Te quedan ${formatCents(-over)}", "You've spent ${formatCents(spentCents)} of ${formatCents(budgetCents)}. ${formatCents(-over)} left")
        }
        notify(context, tag = "trip", id = (trip.id % Int.MAX_VALUE).toInt(), channel = CHANNEL_BUDGET, title = title, text = text, open = "trip/${trip.id}")
    }

    fun weeklySummary(context: Context, lastWeekCents: Long, previousWeekCents: Long) {
        val change = if (previousWeekCents > 0) ((lastWeekCents - previousWeekCents) * 100 / previousWeekCents).toInt() else null
        val text = buildString {
            append(tr("La semana pasada gastaste ${formatCents(lastWeekCents)}", "Last week you spent ${formatCents(lastWeekCents)}"))
            when {
                change == null -> append(".")
                change > 0 -> append(tr(", un $change % más que la anterior.", ", $change % more than the week before."))
                change < 0 -> append(tr(", un ${-change} % menos que la anterior. ¡Bien!", ", ${-change} % less than the week before. Nice!"))
                else -> append(tr(", lo mismo que la anterior.", ", the same as the week before."))
            }
        }
        notify(context, tag = "weekly", id = 1, channel = CHANNEL_SUMMARY, title = tr("Tu resumen semanal", "Your weekly summary"), text = text, open = "stats")
    }

    /** «¡Reto cumplido!» */
    fun challengeAchieved(context: Context, key: String, title: String) {
        notify(context, tag = "challenge", id = key.hashCode(), channel = CHANNEL_SUMMARY, title = tr("¡Reto cumplido! 🎉", "Challenge completed! 🎉"), text = title, open = "challenges")
    }

    fun budgetAlert(context: Context, category: Category, spentCents: Long, limitCents: Long) {
        val over = spentCents - limitCents
        val title: String
        val text: String
        if (over >= 0) {
            title = tr("${category.emoji} Has superado el límite de ${category.name}", "${category.emoji} You've gone over the ${category.name} limit")
            text = tr("Llevas ${formatCents(spentCents)} de ${formatCents(limitCents)} (te has pasado ${formatCents(over)})", "You've spent ${formatCents(spentCents)} of ${formatCents(limitCents)} (over by ${formatCents(over)})")
        } else {
            val pct = (spentCents * 100 / limitCents)
            title = tr("${category.emoji} ${category.name}: $pct % del límite", "${category.emoji} ${category.name}: $pct % of the limit")
            text = tr("Llevas ${formatCents(spentCents)} de ${formatCents(limitCents)}. Te quedan ${formatCents(-over)}", "You've spent ${formatCents(spentCents)} of ${formatCents(limitCents)}. ${formatCents(-over)} left")
        }
        notify(
            context,
            tag = TAG_BUDGET,
            id = (category.id % Int.MAX_VALUE).toInt(),
            channel = CHANNEL_BUDGET,
            title = title,
            text = text,
            open = "home",
            localOnly = com.midinero.control.watch.WatchBridge.hasWatchApp(context),
        )
        if (com.midinero.control.watch.WatchBridge.hasWatchApp(context)) com.midinero.control.watch.WatchBridge.sendAlert(context, title, text)
    }

    private fun notify(
        context: Context,
        tag: String,
        id: Int,
        channel: String,
        title: String,
        text: String,
        open: String,
        actions: List<NotificationCompat.Action> = emptyList(),
        localOnly: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, open)
        }
        val pending = PendingIntent.getActivity(
            context, "$tag$id".hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_money)
            .setColor(0xFF141318.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setLocalOnly(localOnly)
        actions.forEach { builder.addAction(it) }
        try {
            NotificationManagerCompat.from(context).notify(tag, id, builder.build())
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: no pasa nada, el gasto ya está guardado.
        }
    }
}
