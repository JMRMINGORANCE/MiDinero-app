package com.midinero.control.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Gráfico de barras verticales de una sola serie (un solo tono).
 * La barra [selected] va en el color fuerte y muestra su valor; tocar una barra la selecciona.
 * Toda la columna es zona táctil, no solo la barra.
 */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    selected: Int?,
    valueLabel: (Long) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    barAreaHeight: Int = 120,
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEachIndexed { i, v ->
            val isSelected = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(i) }
                    .semantics { contentDescription = "${labels[i]}: ${valueLabel(v)}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (isSelected) valueLabel(v) else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
                Box(Modifier.height(barAreaHeight.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val fraction = v.toFloat() / max
                    if (v > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isSelected || selected == null) strong else soft),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Text(
                    labels[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Un segmento de la barra de reparto. */
data class ShareSegment(val label: String, val value: Long, val color: Color)

/**
 * Barra apilada horizontal para ver el reparto de un total (parte del todo).
 * Separación de 2 dp entre segmentos; la identidad la da la leyenda con nombre, nunca solo el color.
 */
@Composable
fun ShareBar(segments: List<ShareSegment>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.value }.coerceAtLeast(1L)
    Row(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .semantics { contentDescription = segments.joinToString { "${it.label} ${it.value * 100 / total} %" } },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.filter { it.value > 0 }.forEach { seg ->
            Box(
                Modifier
                    .weight(seg.value.toFloat() / total)
                    .fillMaxHeight()
                    .background(seg.color),
            )
        }
    }
}

/** Barra de ranking horizontal (una fila de una lista ordenada). */
@Composable
fun RankBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) }
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/** Tarjeta con una cifra destacada. */
@Composable
fun StatTile(title: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.merge(com.midinero.control.ui.theme.MoneyStyle), maxLines = 2)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
