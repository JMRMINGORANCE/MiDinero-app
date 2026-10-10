package com.midinero.control.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.midinero.control.MiDineroApp
import com.midinero.control.data.Category
import com.midinero.control.service.AppNotifications
import com.midinero.control.ui.MainActivity

/** Widget 4x1: botón "+" y tus 3 categorías más usadas para apuntar un gasto con un toque. */
class QuickAddWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MiDineroApp
        val categories = app.repository.quickCategoriesForWidget()
        provideContent { GlanceTheme(colors = com.midinero.control.ui.theme.WidgetColors) { Content(context, categories) } }
    }

    private fun open(context: Context, route: String) = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(AppNotifications.EXTRA_OPEN, route)
    }

    @Composable
    private fun Content(context: Context, categories: List<Category>) {
        Row(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(GlanceTheme.colors.surface).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier.cornerRadius(18.dp).background(GlanceTheme.colors.primary).padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(open(context, "expenses?add=true"))),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            }
            categories.forEach { cat ->
                Spacer(GlanceModifier.width(6.dp))
                Box(
                    GlanceModifier.defaultWeight().cornerRadius(18.dp).background(GlanceTheme.colors.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .clickable(actionStartActivity(open(context, "expenses?add=true&cat=${cat.id}"))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${cat.emoji} ${cat.name}",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp),
                    )
                }
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
