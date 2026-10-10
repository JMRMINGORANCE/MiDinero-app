package com.midinero.control.ui.screens

import androidx.compose.material.icons.filled.AutoAwesome
import com.midinero.control.domain.endMillis
import com.midinero.control.domain.startMillis
import com.midinero.control.ui.theme.Ink
import com.midinero.control.ui.theme.Lime
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Bolt
import kotlin.math.abs
import com.midinero.control.ui.theme.parseHexColor
import com.midinero.control.ui.theme.MoneyStyle
import com.midinero.control.ui.theme.AppBrushes
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.lengthInDays
import com.midinero.control.domain.dayIndex
import com.midinero.control.domain.Lang
import java.time.YearMonth
import com.midinero.control.domain.MonthStats
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.midinero.control.domain.tr
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import com.midinero.control.domain.TripStats
import com.midinero.control.domain.TripStatus
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.midinero.control.domain.BudgetPace
import com.midinero.control.domain.Budgets
import com.midinero.control.domain.Split
import com.midinero.control.domain.Tip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.midinero.control.ui.PrivacyState
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.domain.Recurrence
import com.midinero.control.domain.formatCents
import com.midinero.control.service.PaymentNotificationListener
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.BudgetColors

// Cabecera de Inicio: total del mes, anillo del presupuesto y curva del gasto.

/** Resumen de una categoría en el mes: lo gastado y su límite si lo tiene. */
data class CategorySpend(val category: Category?, val spentCents: Long, val limitCents: Long?) {
    val ratio: Float get() = if (limitCents == null || limitCents <= 0) 0f else spentCents.toFloat() / limitCents
    val hasLimit: Boolean get() = limitCents != null && limitCents > 0
}

/**
 * Cabecera de Inicio en tinta: total del mes, anillo del presupuesto, comparación con el mes anterior,
 * la curva de lo gastado día a día (con la línea del ritmo del presupuesto) y ingresos, ahorro y previsión.
 */
@Composable
internal fun HomeHero(
    month: YearMonth,
    total: Long,
    stats: MonthStats?,
    count: Int,
    expenses: List<com.midinero.control.data.Expense>,
    budget: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrent = month == currentPeriod()
    val onHero = Color.White
    val soft = onHero.copy(alpha = 0.62f)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(AppBrushes.hero)
            .drawBehind {
                // Resplandor lima en la esquina
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(Lime.copy(alpha = 0.28f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, 0f),
                        radius = size.width * 0.75f,
                    ),
                    radius = size.width * 0.75f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, 0f),
                )
            }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthSwitcher(month, onPrevious, onNext, onDark = true)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onShare,
                colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(containerColor = onHero.copy(alpha = 0.1f), contentColor = onHero),
            ) { Icon(Icons.Filled.Share, contentDescription = tr("Compartir resumen del mes", "Share month summary")) }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Con los importes ocultos se enseña solo la parte del presupuesto gastada, que no dice cuánto dinero es
                val share = if (com.midinero.control.ui.PrivacyState.hidden) com.midinero.control.domain.PrivateShare.label(total, budget) else null
                Text(
                    if (share != null) tr("Del presupuesto llevas", "Of your budget you've used")
                    else if (isCurrent) tr("Llevas gastado", "Spent so far") else tr("Gastaste", "You spent"),
                    style = MaterialTheme.typography.labelLarge,
                    color = soft,
                )
                if (share != null) {
                    Text(share, style = MaterialTheme.typography.displayMedium.merge(MoneyStyle), color = onHero)
                } else {
                    AnimatedMoney(total, MaterialTheme.typography.displayMedium.merge(MoneyStyle), color = onHero)
                }
            }
            if (budget > 0) BudgetRing(total.toFloat() / budget, onHero)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val previous = stats?.previousTotalCents ?: 0L
            if (previous > 0) {
                val diff = total - previous
                val pct = (abs(diff) * 100 / previous).toInt()
                val up = diff > 0
                val prevName = month.minusMonths(1).format(Lang.dateFormat("LLLL"))
                Text(
                    (if (up) "▲ " else "▼ ") + tr("$pct % vs $prevName", "$pct % vs $prevName"),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (up) Color.White else Ink,
                    modifier = Modifier
                        .background(if (up) BudgetColors.over else Lime, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            val daily = stats?.dailyAverageCents ?: 0L
            Text(
                (if (count == 1) tr("1 gasto", "1 expense") else tr("$count gastos", "$count expenses")) +
                    tr(" · ${formatCents(daily)}/día", " · ${formatCents(daily)}/day"),
                style = MaterialTheme.typography.bodySmall,
                color = soft,
            )
        }

        SpendingCurve(month, expenses, budget, Modifier.padding(top = 18.dp).fillMaxWidth().height(86.dp))

        stats?.let { st ->
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(onHero.copy(alpha = 0.07f), MaterialTheme.shapes.medium)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                HeroFigure(tr("Ingresos", "Income"), if (st.incomeCents > 0) "+${formatCents(st.incomeCents)}" else "—", Modifier.weight(1f))
                HeroFigure(
                    if (st.balanceCents >= 0) tr("Ahorro", "Saved") else tr("Déficit", "Deficit"),
                    if (st.incomeCents > 0) formatCents(st.balanceCents) else "—",
                    Modifier.weight(1f),
                )
                HeroFigure(
                    tr("Previsión", "Forecast"),
                    st.projectionCents?.takeIf { st.totalCents > 0 }?.let { formatCents(it) } ?: "—",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun HeroFigure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
        FitText(value, style = MaterialTheme.typography.titleSmall.merge(MoneyStyle), color = Color.White)
    }
}

/**
 * Curva de lo gastado acumulado cada día del mes, que se dibuja de izquierda a derecha al aparecer.
 * Si hay presupuesto, una línea discontinua marca el ritmo «ideal» para no pasarse.
 */
@Composable
internal fun SpendingCurve(month: YearMonth, expenses: List<com.midinero.control.data.Expense>, budget: Long, modifier: Modifier) {
    // Por días del calendario, no de 24 h: los cambios de hora no descuadran la curva
    val days = month.lengthInDays().coerceAtLeast(1)
    val today = if (month == currentPeriod()) (month.dayIndex(java.time.LocalDate.now()) - 1).coerceIn(0, days - 1) else days - 1
    val cumulative = remember(expenses, month) {
        val perDay = LongArray(days)
        expenses.filter { !it.isIncome }.forEach { e ->
            val d = month.dayIndex(com.midinero.control.domain.dateOf(e.timestamp)) - 1
            if (d in 0 until days) perDay[d] += e.amountCents
        }
        var run = 0L
        perDay.map { run += it; run }
    }
    val max = maxOf(cumulative.lastOrNull() ?: 0L, budget, 1L).toFloat()
    val reveal = animatedRatio(1f, delayMillis = 150)
    val total = cumulative.getOrNull(today) ?: 0L
    androidx.compose.foundation.Canvas(
        modifier.semantics { contentDescription = tr("Gasto acumulado del mes: ${formatCents(total)}", "Spending so far this month: ${formatCents(total)}") },
    ) {
        val w = size.width
        val h = size.height
        fun x(d: Int) = if (days == 1) 0f else w * d / (days - 1)
        fun y(v: Long) = h - (v / max) * h * 0.92f
        if (budget > 0) {
            drawLine(
                Color.White.copy(alpha = 0.28f),
                androidx.compose.ui.geometry.Offset(0f, h),
                androidx.compose.ui.geometry.Offset(w, y(budget)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
        val last = (today * reveal).toInt().coerceAtLeast(0)
        val line = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, y(cumulative.firstOrNull() ?: 0L))
            for (d in 1..last) lineTo(x(d), y(cumulative[d]))
        }
        val area = androidx.compose.ui.graphics.Path().apply {
            addPath(line)
            lineTo(x(last), h)
            lineTo(0f, h)
            close()
        }
        drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Lime.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, Lime, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(Ink, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x(last), y(cumulative[last])))
        drawCircle(Lime, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x(last), y(cumulative[last])))
    }
}


internal fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 6..13 -> tr("Buenos días", "Good morning")
        in 14..20 -> tr("Buenas tardes", "Good afternoon")
        else -> tr("Buenas noches", "Good evening")
    }
}

/** Anillo del presupuesto del mes dentro de la cabecera, que se llena al aparecer. */
@Composable
internal fun BudgetRing(ratio: Float, color: Color) {
    val shown = animatedRatio(ratio.coerceIn(0f, 1f), delayMillis = 200)
    val over = ratio > 1f
    val arc = if (over) BudgetColors.over else Lime
    val label = "${(ratio * 100).toInt()} %"
    Box(
        Modifier
            .size(76.dp)
            .semantics { contentDescription = tr("Presupuesto usado: $label", "Budget used: $label") },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(color.copy(alpha = 0.22f), -90f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(arc, -90f, 360f * shown, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Text(label, style = MaterialTheme.typography.titleSmall.merge(MoneyStyle), color = color)
            Text(tr("del límite", "of budget"), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

internal data class QuickAction(val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val label: String, val onClick: () -> Unit)
