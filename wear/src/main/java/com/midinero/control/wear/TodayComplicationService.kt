package com.midinero.control.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.midinero.control.link.WatchState

/**
 * Complicación para cualquier esfera: texto corto con lo que se puede gastar hoy, o un anillo con la parte
 * del presupuesto del mes ya gastada. Al tocarla se abre MiDinero.
 */
class TodayComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, WatchStore.current(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WatchState(updated = 1, todayLabel = "Hoy", todayValue = "24 €", spent = "640 €", budget = "1.000 €", budgetRatio = 0.64f))

    private fun plain(text: String) = PlainComplicationText.Builder(text).build()

    private fun build(type: ComplicationType, state: WatchState): ComplicationData? {
        if (state.isEmpty) return NoDataComplicationData()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val today = compactMoney(state.todayValue)
        val description = plain("${state.todayLabel}: ${state.todayValue}")
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(plain(today), description)
                .setTitle(plain(tr("Hoy", "Today")))
                .setTapAction(tap)
                .build()
            ComplicationType.RANGED_VALUE -> {
                val ratio = if (state.budgetRatio < 0) 0f else state.budgetRatio.coerceIn(0f, 1f)
                RangedValueComplicationData.Builder(ratio * 100f, 0f, 100f, plain("${state.spent} / ${state.budget}"))
                    .setText(plain(today))
                    .setTitle(plain(tr("Hoy", "Today")))
                    .setTapAction(tap)
                    .build()
            }
            else -> null
        }
    }
}
