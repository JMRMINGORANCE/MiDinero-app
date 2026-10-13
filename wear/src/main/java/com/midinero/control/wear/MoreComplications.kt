package com.midinero.control.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.midinero.control.link.WatchCategory
import com.midinero.control.link.WatchMovement
import com.midinero.control.link.WatchState

private fun plain(text: String) = PlainComplicationText.Builder(text).build()

private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** «Te queda esta semana»: texto corto o anillo con la parte de la semana gastada. */
class WeekComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, WatchStore.current(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WatchState(updated = 1, weekLabel = "Te queda esta semana", weekValue = "86 €", weekRatio = 0.45f))

    private fun build(type: ComplicationType, s: WatchState): ComplicationData? {
        if (s.isEmpty || s.weekValue.isBlank()) return NoDataComplicationData()
        val value = compactMoney(s.weekValue)
        val description = plain("${s.weekLabel}: ${s.weekValue}")
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(plain(value), description)
                .setTitle(plain(tr("Semana", "Week"))).setTapAction(openApp(this)).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                (if (s.weekRatio < 0) 0f else s.weekRatio.coerceIn(0f, 1f)) * 100f, 0f, 100f, description,
            ).setText(plain(value)).setTitle(plain(tr("Semana", "Week"))).setTapAction(openApp(this)).build()
            else -> null
        }
    }
}

/** La categoría más cerca de su límite del mes (anillo con lo gastado de su límite). */
class CategoryComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, WatchStore.current(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WatchState(updated = 1, categories = listOf(WatchCategory(1, "Restaurantes", "🍽️", 0, "142 €", "200 €", 0.71f))))

    private fun pick(s: WatchState): WatchCategory? = s.categories.filter { it.ratio >= 0 }.maxByOrNull { it.ratio }

    private fun build(type: ComplicationType, s: WatchState): ComplicationData? {
        val c = pick(s) ?: return NoDataComplicationData()
        val pct = "${(c.ratio * 100).toInt()}%"
        val description = plain("${c.name}: ${c.spent} / ${c.limit}")
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(plain(if (s.hidden) pct else compactMoney(c.spent)), description)
                .setTitle(plain(c.emoji)).setTapAction(openApp(this)).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(c.ratio.coerceIn(0f, 1f) * 100f, 0f, 100f, description)
                .setText(plain(c.emoji)).setTitle(plain(pct)).setTapAction(openApp(this)).build()
            else -> null
        }
    }
}

/** El último gasto apuntado («☕ 3,20 € · Starbucks»). */
class LastExpenseComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, WatchStore.current(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WatchState(updated = 1, recent = listOf(WatchMovement("Starbucks", "3,20 €", "☕", 0, "10:42", false))))

    private fun build(type: ComplicationType, s: WatchState): ComplicationData? {
        val m = s.recent.firstOrNull { !it.income } ?: return NoDataComplicationData()
        val description = plain("${m.title}: ${m.amount} (${m.time})")
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(plain(compactMoney(m.amount)), description)
                .setTitle(plain(m.emoji)).setTapAction(openApp(this)).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(plain("${m.emoji} ${m.amount} · ${m.title}"), description)
                .setTitle(plain(m.time)).setTapAction(openApp(this)).build()
            else -> null
        }
    }
}
