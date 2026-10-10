package com.midinero.control.wear

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.midinero.control.link.WatchCategory
import kotlinx.coroutines.delay

/** Lo tecleado («12,5») a céntimos. */
fun typedToCents(typed: String): Long {
    if (typed.isEmpty()) return 0
    val parts = typed.split('.')
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return 0
    val decimals = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).toLongOrNull() ?: 0
    return whole * 100 + decimals
}

/** Añade una tecla a lo tecleado respetando el formato: hasta 6 cifras enteras y 2 decimales. */
fun typeKey(typed: String, key: Char): String = when {
    key == '.' -> if ('.' in typed) typed else typed.ifEmpty { "0" } + "."
    '.' in typed -> if (typed.substringAfter('.').length >= 2) typed else typed + key
    typed == "0" -> key.toString()
    typed.length >= 6 -> typed
    else -> typed + key
}

/** Lo tecleado tal cual se ve, con separadores y moneda («1.234,5 €»). */
fun typedLabel(typed: String, english: Boolean = WatchStore.english): String {
    if (typed.isEmpty()) return if (english) "€0" else "0 €"
    val whole = typed.substringBefore('.').ifEmpty { "0" }
    val grouped = whole.reversed().chunked(3).joinToString(if (english) "," else ".").reversed()
    val number = if ('.' in typed) grouped + (if (english) "." else ",") + typed.substringAfter('.') else grouped
    return if (english) "€$number" else "$number €"
}

/** Teclado numérico pensado para la pantalla redonda. */
@Composable
fun AmountScreen(onNext: (Long) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val view = LocalView.current
    val cents = typedToCents(typed)
    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    val keyW = 52.dp.s()
    val keyH = 27.dp.s()
    Column(
        Modifier.fillMaxSize().background(W.Black).padding(top = 18.dp.s()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(30.dp.s()), contentAlignment = Alignment.Center) {
            val text = typedLabel(typed)
            Text(
                text,
                style = money((if (text.length > 9) 20.sp else 25.sp) * scale(), if (cents > 0) W.Lime else W.Faint),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(3.dp))
        val rows = listOf("123", "456", "789", if (WatchStore.english) ".0<" else ",0<")
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 1.5.dp)) {
                row.forEach { k ->
                    when (k) {
                        '<' -> Key(keyW, keyH, tr("Borrar", "Delete"), W.Ink, onLongClick = { tap(); typed = "" }, onClick = { tap(); typed = typed.dropLast(1) }) {
                            BackspaceGlyph(Modifier.size(18.dp))
                        }
                        ',', '.' -> Key(keyW, keyH, tr("Coma", "Decimal point"), W.Ink, onClick = { tap(); typed = typeKey(typed, '.') }) {
                            Text(k.toString(), style = money(18.sp, W.White, FontWeight.Bold))
                        }
                        else -> Key(keyW, keyH, k.toString(), W.Card, onClick = { tap(); typed = typeKey(typed, k) }) {
                            Text(k.toString(), style = money(17.sp * scale(), W.White, FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        val enabled = cents > 0
        Box(
            Modifier.width(84.dp.s()).height(34.dp.s()).clip(RoundedCornerShape(50))
                .background(if (enabled) W.Lime else W.CardHigh)
                .clickable(enabled = enabled) { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onNext(cents) }
                .semantics { contentDescription = tr("Siguiente: elegir categoría", "Next: choose category") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = if (enabled) W.OnLime else W.Faint, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Key(
    width: Dp,
    height: Dp,
    description: String,
    background: Color,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.width(width).height(height).clip(RoundedCornerShape(50)).background(background)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Icono de borrar dibujado a mano (las fuentes del reloj no siempre traen «⌫»). */
@Composable
private fun BackspaceGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val outline = Path().apply {
            moveTo(w * 0.32f, h * 0.2f); lineTo(w * 0.92f, h * 0.2f); lineTo(w * 0.92f, h * 0.8f)
            lineTo(w * 0.32f, h * 0.8f); lineTo(w * 0.06f, h * 0.5f); close()
        }
        drawPath(outline, W.White, style = stroke)
        drawLine(W.White, Offset(w * 0.5f, h * 0.38f), Offset(w * 0.74f, h * 0.62f), stroke.width, StrokeCap.Round)
        drawLine(W.White, Offset(w * 0.74f, h * 0.38f), Offset(w * 0.5f, h * 0.62f), stroke.width, StrokeCap.Round)
    }
}

/** Elegir en qué ha sido el gasto: las categorías más usadas primero. */
@Composable
fun CategoryScreen(amountCents: Long, categories: List<WatchCategory>, note: String = "", onPick: (WatchCategory?) -> Unit) {
    val listState = rememberScalingLazyListState()
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(W.Black),
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text(formatMoney(amountCents), style = money(22.sp, W.Lime))
                    if (note.isNotBlank()) Text("«$note»", style = label(12.sp, W.White, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("¿En qué ha sido?", "What was it for?"), style = label(12.sp))
                }
            }
            items(categories) { c -> CategoryChip(c.emoji, c.name, Color(c.color)) { onPick(c) } }
            item {
                CategoryChip("❔", if (note.isNotBlank()) tr("Que la elija el móvil", "Let the phone choose") else tr("Sin categoría", "No category"), W.Faint) { onPick(null) }
            }
        }
    }
}

@Composable
private fun CategoryChip(emoji: String, name: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(W.Card).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiDot(emoji, color, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(name, style = label(14.sp, W.White, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Confirmación: un círculo lima con el visto que «salta» y vuelta a la esfera. */
@Composable
fun DoneScreen(amountCents: Long, category: String, onDone: () -> Unit) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        delay(900)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().background(W.Black).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).scale(pop.value).clip(CircleShape).background(W.Lime), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = W.OnLime, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(formatMoney(amountCents), style = money(20.sp, W.White))
        Text(category, style = label(12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(tr("Apuntado · se guarda en el móvil", "Saved · it goes to your phone"), style = label(10.sp, W.Faint), textAlign = TextAlign.Center)
    }
}

/** Micrófono dibujado a mano (los iconos básicos no lo traen). */
@Composable
fun MicGlyph(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(w * 0.1f, cap = StrokeCap.Round)
        drawRoundRect(color, Offset(w * 0.36f, h * 0.08f), androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.5f), androidx.compose.ui.geometry.CornerRadius(w * 0.14f))
        drawArc(color, 0f, 180f, false, Offset(w * 0.22f, h * 0.24f), androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.5f), style = stroke)
        drawLine(color, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.9f), stroke.width, StrokeCap.Round)
    }
}
