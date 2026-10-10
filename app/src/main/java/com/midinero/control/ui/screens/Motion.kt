package com.midinero.control.ui.screens

import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.tr
import kotlin.math.roundToLong

// Movimiento y piezas de la interfaz: todo responde al dedo y las cifras y tarjetas entran con suavidad.

/** Encoge un poco el elemento mientras se pulsa y rebota al soltar. */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.965f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Progreso de 0 a 1 de la entrada de una pantalla; se reinicia cada vez que la pantalla aparece. */
@Composable
fun rememberEntrance(): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing)) }
    return progress.asState()
}

/**
 * Entrada escalonada: el elemento [index] aparece un poco después que el anterior, subiendo y haciéndose visible.
 * Solo toca el dibujo (no la composición) y no cambia la semántica.
 */
fun Modifier.entrance(progress: State<Float>, index: Int): Modifier = composed {
    val distance = with(LocalDensity.current) { 28.dp.toPx() }
    graphicsLayer {
        val t = (progress.value * 1.8f - index.coerceAtMost(8) * 0.1f).coerceIn(0f, 1f)
        alpha = t
        translationY = (1f - t) * distance
    }
}

/** Importe que cuenta hasta su valor al aparecer o cambiar. Para los lectores de pantalla y los tests es el valor final. */
@Composable
fun AnimatedMoney(
    cents: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    prefix: String = "",
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(cents) { animated.animateTo(cents.toFloat(), tween(durationMillis = 750, easing = FastOutSlowInEasing)) }
    val final = prefix + formatCents(cents)
    FitText(prefix + formatCents(animated.value.roundToLong()), style = style, color = color, modifier = modifier.clearAndSetSemantics { text = AnnotatedString(final) })
}

/** Proporción animada (barras, anillos) desde 0 hasta [target]. */
@Composable
fun animatedRatio(target: Float, delayMillis: Int = 0): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target, tween(durationMillis = 800, delayMillis = delayMillis, easing = FastOutSlowInEasing)) }
    return value.value
}

/** Icono sobre un cuadrado redondeado del color de su sección. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 40.dp, solid: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .background(if (solid) color else color.copy(alpha = 0.14f), RoundedCornerShape(size * 0.32f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (solid) Color.White else color, modifier = Modifier.size(size * 0.52f))
    }
}

/** Cabecera de pantalla: título grande, subtítulo y, si hace falta, botón de volver y acciones. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver", "Back")) }
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.asHeading(),
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/** Botón redondo de icono sobre tarjeta blanca, para las acciones de las cabeceras. */
@Composable
fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = tint),
    ) { Icon(icon, contentDescription = description) }
}

/** Grupo de opciones con título, dentro de una tarjeta. */
@Composable
fun MenuGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp).asHeading(),
            )
        }
        AppCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 4.dp), content = content) }
    }
}

/** Fila de menú: icono de color, título, subtítulo y flecha (o lo que se ponga a la derecha). */
@Composable
fun MenuRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    Column {
        if (divider) HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, color, size = 38.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** Selector segmentado con una píldora que se desliza hasta la opción elegida. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(track)
            .padding(4.dp),
    ) {
        val haptics = rememberHaptics()
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selected, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "segment")
        Box(
            Modifier
                // Con lambda, la animación solo recoloca la pastilla sin recomponer
                .offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                val color by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "segmentText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable {
                            if (i != selected) haptics.tick()
                            onSelect(i)
                        }
                        .semantics {
                            role = Role.Tab
                            this.selected = active
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Cifra pequeña en tarjeta: etiqueta, importe animado y un icono de su color. */
@Composable
fun MiniStat(label: String, cents: Long, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedMoney(cents, MaterialTheme.typography.titleMedium.merge(com.midinero.control.ui.theme.MoneyStyle))
            }
        }
    }
}

/**
 * Fondo de fila agrupada: las filas [index] de [count] forman una sola tarjeta blanca,
 * redondeada arriba en la primera y abajo en la última.
 */
fun Modifier.grouped(index: Int, count: Int): Modifier = composed {
    val top = if (index == 0) 24.dp else 0.dp
    val bottom = if (index == count - 1) 24.dp else 0.dp
    this
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
}

/** Separador de filas agrupadas (no se pinta tras la última). */
@Composable
fun GroupDivider(index: Int, count: Int) {
    if (index < count - 1) HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Colores de los filtros en forma de píldora: blancos y, al elegirlos, del color principal. */
@Composable
fun pillChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
)

/** Tarjeta de cifra principal con el fondo de tinta y el resplandor lima de la cabecera de Inicio. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val lime = com.midinero.control.ui.theme.Lime
    Column(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(com.midinero.control.ui.theme.AppBrushes.hero)
            .drawBehind {
                val center = androidx.compose.ui.geometry.Offset(size.width, 0f)
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(listOf(lime.copy(alpha = 0.26f), Color.Transparent), center = center, radius = size.width * 0.7f),
                    radius = size.width * 0.7f,
                    center = center,
                )
            },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
            content()
        }
    }
}

/** Colores de las píldoras dentro de hojas y tarjetas: gris suave y, al elegirlas, del color principal. */
@Composable
fun sheetChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
)

/** Texto de una línea que encoge la letra lo justo para caber, en vez de cortarse (cifras largas, letra grande). */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val resolved = if (color != Color.Unspecified) color
    else if (style.color != Color.Unspecified) style.color
    else androidx.compose.material3.LocalContentColor.current
    val max = if (style.fontSize.isSp) style.fontSize else MaterialTheme.typography.bodyLarge.fontSize
    androidx.compose.foundation.text.BasicText(
        text,
        modifier = modifier,
        style = style.copy(color = resolved),
        maxLines = 1,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
            maxFontSize = max,
        ),
    )
}
