package com.midinero.control.ui.screens

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midinero.control.data.Category
import com.midinero.control.domain.Lang
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.label
import com.midinero.control.domain.rangeLabel
import com.midinero.control.domain.shortLabel
import com.midinero.control.domain.tr
import com.midinero.control.ui.theme.BudgetColors
import com.midinero.control.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

// Piezas del diseño de MiDinero: tarjetas blancas muy redondeadas sobre fondo gris claro,
// cifras en negrita y hojas inferiores para los editores.

/** Tarjeta blanca redondeada, la base visual de toda la app. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        // Al pulsar, la tarjeta se hunde un poco y rebota.
        val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Card(onClick = onClick, modifier = modifier.pressScale(source), shape = shape, colors = colors, interactionSource = source, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, content = content)
    }
}

/** Título de sección con una acción a la derecha. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).asHeading())
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Selector de mes en forma de píldora. [onDark] para usarlo sobre la cabecera de color. */
@Composable
fun MonthSwitcher(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onDark: Boolean = false) {
    val content = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val bg = if (onDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLowest
    Row(
        Modifier.background(bg, CircleShape).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = content),
        ) { Icon(Icons.Filled.ChevronLeft, tr("Mes anterior", "Previous month")) }
        // Región viva: TalkBack anuncia el mes nuevo al cambiarlo.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(month.label(), style = MaterialTheme.typography.labelLarge, color = content)
            month.rangeLabel()?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.75f))
            }
        }
        IconButton(
            onClick = onNext,
            enabled = month < currentPeriod(),
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = content, disabledContentColor = content.copy(alpha = 0.3f)),
        ) { Icon(Icons.Filled.ChevronRight, tr("Mes siguiente", "Next month")) }
    }
}

/** Barra de progreso redondeada para límites y presupuestos. */
@Composable
fun BudgetBar(ratio: Float, color: Color, modifier: Modifier = Modifier, height: Int = 8) {
    LinearProgressIndicator(
        progress = { ratio.coerceIn(0f, 1f) },
        color = color,
        trackColor = color.copy(alpha = 0.15f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = modifier.fillMaxWidth().height(height.dp),
    )
}

/** Color de un límite según lo gastado: verde, naranja desde el 80 % y rojo al pasarse. */
fun limitColor(ratio: Float): Color = when {
    ratio > 1f -> BudgetColors.over
    ratio >= 0.8f -> BudgetColors.warning
    else -> BudgetColors.ok
}

/** Etiqueta pequeña tipo «píldora». */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.13f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Estado vacío amable: icono grande, título y una explicación de qué hacer. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 40.sp) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction, shape = CircleShape) { Text(action) }
        }
    }
}

/**
 * Hoja que sube desde abajo, con título, contenido desplazable y botón principal fijo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryText: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    closeOnPrimary: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp).asHeading(),
            )
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                content = content,
            )
            Button(
                onClick = {
                    haptics.confirm()
                    onPrimary()
                    if (closeOnPrimary) scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
                },
                enabled = primaryEnabled,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(56.dp),
            ) { Text(primaryText, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** Campo de importe grande y centrado, sin marco: la cifra es la protagonista. [suffix] es la moneda. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    suffix: String = com.midinero.control.domain.Currencies.symbol(),
    label: String = tr("Importe", "Amount"),
    focusRequester: FocusRequester? = null,
    supportingText: String? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12)) },
            placeholder = {
                Text(
                    if (Lang.english) "0.00" else "0,00",
                    style = MaterialTheme.typography.displayMedium.merge(com.midinero.control.ui.theme.MoneyStyle),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            },
            suffix = { Text(suffix, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            textStyle = MaterialTheme.typography.displayMedium.merge(com.midinero.control.ui.theme.MoneyStyle).copy(textAlign = TextAlign.Center),
            singleLine = true,
            isError = isError,
            supportingText = if (supportingText != null) {
                { Text(supportingText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label }
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        )
    }
}

/** Colores de los campos de texto «suaves»: fondo gris, sin borde hasta que tienen el foco. */
@Composable
fun softFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)

/** Etiqueta encima de un grupo de campos del editor. */
@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).asHeading(),
    )
}

/**
 * Categorías seleccionables con su icono y color. Con [onCreate] aparece «+ Nueva» para crear una categoría
 * personalizada sin salir del editor; [onCreate] recibe la categoría y devuelve su id (null si el nombre ya existe),
 * y la nueva queda elegida.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long?,
    onCreate: ((Category, (Long?) -> Unit) -> Unit)? = null,
    isIncome: Boolean = false,
    onSelect: (Long?) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Las ocultas no se ofrecen, salvo la que ya tiene el movimiento
    val shown = categories.filter { !it.hidden || it.id == selectedId }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { cat ->
            val selected = selectedId == cat.id
            val color = parseHexColor(cat.colorHex)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        CircleShape,
                    )
                    .border(BorderStroke(1.5.dp, if (selected) color else Color.Transparent), CircleShape)
                    .clickable { onSelect(if (selected) null else cat.id) }
                    .semantics {
                        role = Role.Checkbox
                        this.selected = selected
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(cat.emoji, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    cat.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onCreate != null) {
            val label = tr("Nueva", "New")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
                    .clip(CircleShape)
                    .clickable { creating = true }
                    .semantics {
                        role = Role.Button
                        contentDescription = tr("Nueva categoría", "New category")
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (creating && onCreate != null) {
        CategoryDialog(
            category = null,
            onDismiss = { creating = false },
            onSave = { cat ->
                onCreate(cat) { id ->
                    if (id != null) {
                        creating = false
                        onSelect(id)
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            tr("Ya hay una categoría con ese nombre", "A category with that name already exists"),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onDelete = null,
            isIncome = isIncome,
        )
    }
}

/** Selector de fecha con atajos «Hoy» y «Ayer». */
@Composable
fun DateSelector(date: LocalDate, onChange: (LocalDate) -> Unit, allowFuture: Boolean = false) {
    var showPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = date == today, onClick = { onChange(today) }, label = { Text(tr("Hoy", "Today")) }, shape = CircleShape, colors = sheetChipColors(), border = null)
        FilterChip(
            selected = date == today.minusDays(1),
            onClick = { onChange(today.minusDays(1)) },
            label = { Text(tr("Ayer", "Yesterday")) },
            shape = CircleShape,
            colors = sheetChipColors(),
            border = null,
        )
        val other = date != today && date != today.minusDays(1)
        FilterChip(
            selected = other,
            onClick = { showPicker = true },
            label = { Text(if (other) date.shortLabel() else tr("Otra fecha", "Other date"), maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)) },
            shape = CircleShape,
            colors = sheetChipColors(),
            border = null,
        )
    }
    if (showPicker) {
        DatePickerModal(
            initial = date,
            onDismiss = { showPicker = false },
            onPicked = {
                onChange(if (!allowFuture && it.isAfter(today)) today else it)
                showPicker = false
            },
        )
    }
}
