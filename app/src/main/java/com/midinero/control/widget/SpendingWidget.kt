package com.midinero.control.widget

import com.midinero.control.domain.tr
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.midinero.control.MiDineroApp
import com.midinero.control.domain.HIDDEN_AMOUNT
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.endMillis
import com.midinero.control.domain.startMillis
import com.midinero.control.service.AppNotifications
import com.midinero.control.ui.MainActivity
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** Widget de la pantalla de inicio: lo gastado este mes, lo que queda del presupuesto y un botón para añadir. */
class SpendingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MiDineroApp
        val month = currentPeriod()
        val spentFlow = app.repository.observeSpent(month)
        val initial = app.repository.spentBetween(month.startMillis(), month.endMillis())
        provideContent {
            val spent by spentFlow.collectAsState(initial = initial)
            // Leer la versión de ajustes hace que el widget se redibuje si cambia el presupuesto u "ocultar importes".
            app.prefs.changes.collectAsState().value
            GlanceTheme(colors = com.midinero.control.ui.theme.WidgetColors) {
                Content(
                    context = context,
                    monthName = month.format(com.midinero.control.domain.Lang.dateFormat("LLLL")),
                    spent = spent,
                    budget = app.prefs.monthlyBudgetCents,
                    hidden = app.prefs.hideAmounts,
                )
            }
        }
    }

    @Composable
    private fun Content(context: Context, monthName: String, spent: Long, budget: Long, hidden: Boolean) {
        fun money(cents: Long) = if (hidden) HIDDEN_AMOUNT else euro(cents)
        val openHome = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppNotifications.EXTRA_OPEN, "home")
        }
        val openAdd = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppNotifications.EXTRA_OPEN, "expenses?add=true")
        }
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(14.dp)
                .clickable(actionStartActivity(openHome)),
        ) {
            Text(
                tr("Gastado en $monthName", "Spent in $monthName"),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            // Con los importes ocultos, el porcentaje del presupuesto en lugar de puntos
            val share = if (hidden) com.midinero.control.domain.PrivateShare.label(spent, budget) else null
            Text(
                share?.let { tr("$it del presupuesto", "$it of budget") } ?: money(spent),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (share != null) 20.sp else 24.sp, fontWeight = FontWeight.Bold),
            )
            if (budget > 0) {
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (spent.toFloat() / budget).coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = if (spent > budget) ColorProvider(Color(0xFFE5484D)) else GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                )
                Spacer(GlanceModifier.height(4.dp))
                if (!hidden) Text(
                    if (spent > budget) tr("Te has pasado ${money(spent - budget)}", "Over by ${money(spent - budget)}") else tr("Quedan ${money(budget - spent)} de ${money(budget)}", "${money(budget - spent)} left of ${money(budget)}"),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Box(
                    GlanceModifier
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.primary)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clickable(actionStartActivity(openAdd)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("+ Añadir", "+ Add"), style = TextStyle(color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Medium))
                }
            }
        }
    }

    companion object {
        // El widget ignora «ocultar importes» por su cuenta (lo decide [hidden]); el formato es el de la moneda principal
        private fun euro(cents: Long): String =
            NumberFormat.getCurrencyInstance(com.midinero.control.domain.Lang.locale).apply {
                currency = Currency.getInstance(com.midinero.control.domain.Currencies.base)
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(cents / 100.0)

        /** Pide redibujar todos los widgets (tras cambiar datos o ajustes). */
        suspend fun refresh(context: Context) {
            runCatching { SpendingWidget().updateAll(context) }
            refreshInsightWidgets(context)
        }
    }
}

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendingWidget()
}
