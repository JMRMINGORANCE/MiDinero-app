package com.midinero.control.wear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.midinero.control.link.WatchCategory
import com.midinero.control.link.WatchMovement
import com.midinero.control.link.WatchState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Hueco del anillo arriba, donde va la hora. */
private const val GAP = 64f
private const val START = -90f + GAP / 2
private const val SWEEP = 360f - GAP

@Composable
fun HomeScreen(
    state: WatchState,
    pending: List<PendingEntry>,
    now: Long,
    onAdd: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onVoice: (() -> Unit)? = null,
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val screen = LocalConfiguration.current.screenWidthDp.dp
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(W.Black),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            contentPadding = PaddingValues(bottom = 28.dp),
            // El anillo ocupa la pantalla entera: sin encoger ni apagar los elementos de los bordes
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.9f, edgeAlpha = 0.7f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Dial(state, Modifier.size(screen), onAdd, onSync, onVoice) }
            if (state.isEmpty) return@ScalingLazyColumn
            if (state.versus.isNotBlank()) item { VersusPill(state) }
            if (state.curve.size > 1 && state.spent.isNotBlank()) item { CurveCard(state) }
            if (pending.isNotEmpty() || state.recent.isNotEmpty()) item { Header(tr("Últimos movimientos", "Latest activity")) }
            items(pending.sortedByDescending { it.time }) { p ->
                MovementRow(
                    WatchMovement(p.name, formatMoney(p.amountCents), p.emoji.ifBlank { "⌚" }, p.color, "", false),
                    pendingLabel = tr("Enviando al móvil…", "Sending to phone…"),
                )
            }
            items(state.recent) { MovementRow(it) }
            if (state.categories.isNotEmpty()) item { Header(tr("Por categorías", "By category")) }
            items(state.categories) { CategoryRow(it) }
            item { Footer(state, now, onSync, onSettings) }
        }
    }
}

/** Esfera principal: anillo del presupuesto del mes y, dentro, lo que se puede gastar hoy. */
@Composable
private fun Dial(state: WatchState, modifier: Modifier, onAdd: () -> Unit, onSync: () -> Unit, onVoice: (() -> Unit)?) {
    val ratio = state.budgetRatio
    val progress = remember { Animatable(0f) }
    LaunchedEffect(ratio) {
        progress.animateTo(if (ratio < 0) 0f else ratio.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing))
    }
    val ringColor = when {
        ratio > 1f -> W.Coral
        ratio >= 0 && ratio > state.pace + 0.1f -> W.Amber
        else -> W.Lime
    }
    val description = if (state.isEmpty) tr("Sin datos del móvil", "No data from phone")
    else "${state.todayLabel} ${state.todayValue}. ${state.monthLabel}: ${state.spent}" +
        (if (state.budget.isNotBlank()) tr(" de ${state.budget}", " of ${state.budget}") else "")
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(W.Track, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress.value > 0f) {
                drawArc(ringColor, START, SWEEP * progress.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Marca de dónde «tocaría» ir a estas alturas del mes
            if (ratio >= 0 && state.pace in 0.02f..0.98f) {
                val angle = Math.toRadians((START + SWEEP * state.pace).toDouble())
                val r = arcSize.width / 2
                val c = Offset(size.width / 2 + (r * cos(angle)).toFloat(), size.height / 2 + (r * sin(angle)).toFloat())
                drawCircle(W.Black, stroke * 0.42f, c)
                drawCircle(W.White, stroke * 0.26f, c)
            }
        }
        if (state.isEmpty) {
            EmptyDial(onSync)
        } else {
            // Un poco por encima del centro para dejar sitio al botón + dentro del anillo
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 26.dp.s(), end = 26.dp.s(), bottom = 42.dp.s())) {
                Text(state.todayLabel, style = label(12.sp), maxLines = 1)
                val value = state.todayValue
                val size = when {
                    value.length > 10 -> 22.sp
                    value.length > 8 -> 26.sp
                    else -> 30.sp
                } * scale()
                Text(value, style = money(size, if (state.todayOver) W.Coral else W.Lime), maxLines = 1)
                if (state.todayNote.isNotBlank()) {
                    Text(
                        state.todayNote, style = label(11.sp, W.Soft, FontWeight.Normal), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
                if (state.spent.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(36.dp).height(1.dp).background(W.Track))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(state.spent, style = money(15.sp * scale(), W.White, FontWeight.Bold), maxLines = 1)
                    if (state.budget.isNotBlank()) {
                        Text(" / ${state.budget}", style = label(11.sp, W.Soft), maxLines = 1, modifier = Modifier.padding(bottom = 1.dp))
                    }
                }
                }
            }
            Box(Modifier.fillMaxSize().padding(bottom = 32.dp.s()), contentAlignment = Alignment.BottomCenter) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onVoice != null) {
                        Box(
                            Modifier.size(28.dp.s()).clip(CircleShape).background(W.CardHigh).clickable(onClick = onVoice)
                                .semantics { contentDescription = tr("Apuntar por voz", "Add by voice") },
                            contentAlignment = Alignment.Center,
                        ) { MicGlyph(W.White, Modifier.size(16.dp)) }
                    }
                    RoundButton(Icons.Rounded.Add, tr("Apuntar gasto", "Add expense"), W.Lime, W.OnLime, 40.dp.s(), onAdd)
                    // Mismo ancho a la derecha para que el + quede centrado
                    if (onVoice != null) Spacer(Modifier.size(28.dp.s()))
                }
            }
        }
    }
}

@Composable
private fun EmptyDial(onSync: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 30.dp)) {
        Text("MiDinero", style = money(18.sp, W.Lime))
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Abre MiDinero en el móvil para ver aquí tus gastos", "Open MiDinero on your phone to see your spending here"),
            style = label(12.sp, W.Soft, FontWeight.Normal), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Pill(tr("Sincronizar", "Sync"), Icons.Rounded.Refresh, onSync)
    }
}

@Composable
fun RoundButton(icon: ImageVector, description: String, background: Color, tint: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Pill(text: String, icon: ImageVector?, onClick: () -> Unit, background: Color = W.CardHigh, tint: Color = W.White) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(background).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = label(13.sp, tint, FontWeight.SemiBold))
    }
}

@Composable
private fun VersusPill(state: WatchState) {
    val color = if (state.versusUp) W.Coral else W.Lime
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (state.versusUp) "▲" else "▼", style = label(9.sp, color, FontWeight.Bold))
        Spacer(Modifier.width(5.dp))
        Text(state.versus, style = label(11.sp, color, FontWeight.SemiBold), maxLines = 1)
    }
}

/** Curva del gasto acumulado del mes, como en el móvil. */
@Composable
private fun CurveCard(state: WatchState) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(22.dp)).background(W.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(tr("Gasto de ${state.monthLabel.lowercase()}", "${state.monthLabel} spending"), style = label(11.sp))
        Text(state.spent, style = money(18.sp, W.White))
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val curve = state.curve
            val max = curve.maxOrNull()?.coerceAtLeast(1) ?: 1
            val steps = (curve.size - 1).coerceAtLeast(1)
            fun x(i: Int) = size.width * i / steps
            fun y(v: Long) = size.height - size.height * 0.92f * v / max
            val line = Path().apply {
                moveTo(x(0), y(curve[0]))
                for (i in 1 until curve.size) lineTo(x(i), y(curve[i]))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(curve.size - 1), size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(W.Lime.copy(alpha = 0.32f), Color.Transparent)))
            drawPath(line, W.Lime, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(W.Lime, 3.5.dp.toPx(), Offset(x(curve.size - 1), y(curve.last())))
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = label(12.sp, W.Soft, FontWeight.SemiBold), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun MovementRow(m: WatchMovement, pendingLabel: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(24.dp)).background(W.Card)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiDot(m.emoji, Color(m.color), 30.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = label(13.sp, W.White, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = pendingLabel ?: m.time
            if (sub.isNotBlank()) {
                Text(sub, style = label(10.sp, if (pendingLabel != null) W.Amber else W.Faint), maxLines = 1)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(m.amount, style = money(13.sp, if (m.income) W.Lime else W.White, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun CategoryRow(c: WatchCategory) {
    val color = Color(c.color)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(24.dp)).background(W.Card)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text(c.name, style = label(13.sp, W.White, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(c.spent, style = money(13.sp, W.White, FontWeight.Bold), maxLines = 1)
        }
        if (c.ratio >= 0) {
            Spacer(Modifier.height(7.dp))
            val over = c.ratio > 1f
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(W.Track)) {
                Box(
                    Modifier.fillMaxWidth(min(c.ratio, 1f).coerceAtLeast(0.03f)).height(5.dp).clip(RoundedCornerShape(50))
                        .background(if (over) W.Coral else color),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                tr("de ${c.limit}", "of ${c.limit}"), style = label(10.sp, if (over) W.Coral else W.Faint),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun Footer(state: WatchState, now: Long, onSync: () -> Unit, onSettings: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(updatedLabel(state.updated, now), style = label(10.sp, W.Faint), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundButton(Icons.Rounded.Refresh, tr("Sincronizar", "Sync"), W.CardHigh, W.White, 40.dp, onSync)
            RoundButton(Icons.Rounded.Settings, tr("Ajustes del reloj", "Watch settings"), W.CardHigh, W.White, 40.dp, onSettings)
        }
    }
}

fun updatedLabel(updated: Long, now: Long): String {
    if (updated <= 0) return tr("Sin sincronizar", "Not synced yet")
    val minutes = ((now - updated) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> tr("Actualizado ahora", "Updated just now")
        minutes < 60 -> tr("Actualizado hace $minutes min", "Updated $minutes min ago")
        minutes < 24 * 60 -> tr("Actualizado hace ${minutes / 60} h", "Updated ${minutes / 60} h ago")
        else -> tr("Actualizado hace ${minutes / (24 * 60)} d", "Updated ${minutes / (24 * 60)} d ago")
    }
}
