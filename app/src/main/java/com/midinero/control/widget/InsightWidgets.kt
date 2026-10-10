package com.midinero.control.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import com.midinero.control.domain.Budgets
import com.midinero.control.domain.DailyCurve
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.tr
import com.midinero.control.service.AppNotifications
import com.midinero.control.ui.MainActivity
import com.midinero.control.ui.ReportImage
import java.time.LocalDate

private val Ink = ColorProvider(Color(0xFF141318))
private val White = ColorProvider(Color.White)
private val Soft = ColorProvider(Color(0x9EFFFFFF))
private val LimeText = ColorProvider(Color(0xFFD4F54A))

private fun open(context: Context, route: String) = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    putExtra(AppNotifications.EXTRA_OPEN, route)
}

/** Widget con la curva de lo gastado este mes, sobre tinta como la cabecera de la app. */
class CurveWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MiDineroApp
        val month = currentPeriod()
        val flow = app.repository.expensesIn(month)
        provideContent {
            val expenses by flow.collectAsState(initial = emptyList())
            app.prefs.changes.collectAsState().value
            val curve = DailyCurve.cumulative(month, expenses)
            val total = curve.lastOrNull() ?: 0L
            val bitmap = androidx.core.graphics.createBitmap(640, 220).also {
                ReportImage.drawCurve(Canvas(it), RectF(8f, 16f, 632f, 212f), curve, 0xFFD4F54A.toInt(), strokeWidth = 7f)
            }
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Ink).padding(14.dp)
                    .clickable(actionStartActivity(open(context, "stats"))),
            ) {
                Text(
                    month.format(com.midinero.control.domain.Lang.dateFormat("LLLL")).replaceFirstChar { it.uppercase() },
                    style = TextStyle(color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Text(formatCents(total), style = TextStyle(color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(6.dp))
                Image(
                    ImageProvider(bitmap),
                    contentDescription = tr("Curva del gasto del mes", "This month's spending curve"),
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                )
            }
        }
    }
}

class CurveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurveWidget()
}

/** Widget «Hoy puedes gastar»: lo que queda del presupuesto repartido entre los días que faltan. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MiDineroApp
        val month = currentPeriod()
        val flow = app.repository.expensesIn(month)
        provideContent {
            val expenses by flow.collectAsState(initial = emptyList())
            app.prefs.changes.collectAsState().value
            val budget = app.prefs.monthlyBudgetCents
            val spending = expenses.filter { !it.isIncome }
            val spent = spending.sumOf { it.amountCents }
            val today = LocalDate.now()
            val spentToday = spending.filter { dateOf(it.timestamp) == today }.sumOf { it.amountCents }
            // Lo que se puede gastar hoy: lo que quedaba al empezar el día, repartido entre los días que faltan
            val pace = Budgets.pace(month, spent - spentToday, budget)
            val allowance = pace.dailyAllowanceCents
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Ink).padding(14.dp)
                    .clickable(actionStartActivity(open(context, "home"))),
            ) {
                if (budget > 0 && allowance != null) {
                    val left = allowance - spentToday
                    Text(tr("Hoy puedes gastar", "You can spend today"), style = TextStyle(color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    Text(
                        formatCents(left.coerceAtLeast(0)),
                        style = TextStyle(color = if (left >= 0) LimeText else ColorProvider(Color(0xFFFF7A7F)), fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        if (left >= 0) tr("Llevas ${formatCents(spentToday)} hoy", "${formatCents(spentToday)} spent today")
                        else tr("Te has pasado ${formatCents(-left)} hoy", "${formatCents(-left)} over today"),
                        style = TextStyle(color = White, fontSize = 12.sp),
                    )
                } else {
                    Text(tr("Hoy llevas", "Spent today"), style = TextStyle(color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    Text(formatCents(spentToday), style = TextStyle(color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        tr("Pon un presupuesto para ver cuánto puedes gastar al día", "Set a budget to see how much you can spend per day"),
                        style = TextStyle(color = Soft, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Pide redibujar los widgets de curva y de hoy. */
suspend fun refreshInsightWidgets(context: Context) {
    runCatching { CurveWidget().updateAll(context) }
    runCatching { TodayWidget().updateAll(context) }
}
