package com.midinero.control.ui.screens

import com.midinero.control.ui.theme.MoneyStyle
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AutoAwesome
import com.midinero.control.ui.theme.Ink
import com.midinero.control.ui.theme.Lime
import androidx.compose.material.icons.filled.Bolt
import com.midinero.control.domain.Lang
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.midinero.control.domain.tr
import androidx.compose.material.icons.filled.Share
import android.content.Intent
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
import com.midinero.control.domain.Tip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midinero.control.domain.formatCents
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.BudgetColors

// Tarjetas de Inicio: presupuesto, consejos, avisos y la barra para apuntar rápido.

/** Presupuesto total del mes, en la parte de arriba de Inicio. */
@Composable
fun GlobalBudgetCard(pace: BudgetPace, onClick: () -> Unit) {
    val color = budgetColor(pace.ratio)
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Presupuesto del mes", "Monthly budget"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${(pace.ratio * 100).toInt()} %",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                tr("${formatCents(pace.spentCents)} de ${formatCents(pace.limitCents)}", "${formatCents(pace.spentCents)} of ${formatCents(pace.limitCents)}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PaceBar(pace, color)
            Spacer(Modifier.height(8.dp))
            BudgetMessage(pace, color)
        }
    }
}

internal fun budgetColor(ratio: Float) = when {
    ratio > 1f -> BudgetColors.over
    ratio >= 0.8f -> BudgetColors.warning
    else -> BudgetColors.ok
}

/** Barra de progreso con una marca de dónde "debería" ir a estas alturas del mes. */
@Composable
internal fun PaceBar(pace: BudgetPace, color: Color) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
        LinearProgressIndicator(
            progress = { pace.ratio.coerceIn(0f, 1f) },
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(10.dp).align(Alignment.CenterStart),
        )
        if (pace.monthFraction in 0.01f..0.99f) {
            Box(
                Modifier
                    .offset(x = maxWidth * pace.monthFraction - 1.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    .semantics { contentDescription = tr("Hoy va el ${(pace.monthFraction * 100).toInt()} % del mes", "${(pace.monthFraction * 100).toInt()} % of the month has gone by") },
            )
        }
    }
}

@Composable
internal fun BudgetMessage(pace: BudgetPace, color: Color) {
    val warn = pace.ratio >= 0.8f || pace.aheadOfPace
    Text(
        when {
            pace.remainingCents < 0 -> tr("⚠️ Te has pasado ${formatCents(-pace.remainingCents)}", "⚠️ You're over by ${formatCents(-pace.remainingCents)}")
            pace.remainingCents == 0L -> tr("Has llegado justo al límite", "You've reached the limit exactly")
            pace.dailyAllowanceCents != null && pace.aheadOfPace ->
                tr("⚠️ Vas rápido: quedan ${formatCents(pace.remainingCents)}, unos ${formatCents(pace.dailyAllowanceCents!!)} al día", "⚠️ Spending fast: ${formatCents(pace.remainingCents)} left, about ${formatCents(pace.dailyAllowanceCents!!)} a day")
            pace.dailyAllowanceCents != null ->
                tr("Te quedan ${formatCents(pace.remainingCents)} · ${formatCents(pace.dailyAllowanceCents!!)} al día", "${formatCents(pace.remainingCents)} left · ${formatCents(pace.dailyAllowanceCents!!)} a day")
            else -> tr("Te quedan ${formatCents(pace.remainingCents)}", "${formatCents(pace.remainingCents)} left")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (warn) color else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (warn) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/** Apuntar escribiendo: "12,50 café", "taxi 18 ayer", "+1200 nómina". */
@Composable
internal fun QuickEntryBar(vm: MainViewModel, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    fun submit() {
        if (text.isBlank()) return
        val entry = vm.quickAdd(text)
        scope.launch {
            snackbar.showSnackbar(
                entry?.let {
                    (if (it.isIncome) tr("Ingreso apuntado: +", "Income logged: +") else tr("Apuntado: ", "Logged: ")) + formatCents(it.amountCents) + " · " + it.merchant +
                        if (it.date != LocalDate.now()) " (${it.date.shortLabel()})" else ""
                } ?: tr("Escribe también el importe, por ejemplo «12,50 café»", "Include the amount too, e.g. “12.50 coffee”")
            )
        }
        if (entry != null) {
            text = ""
            focus.clearFocus()
        }
    }
    // Dictar: se usa el reconocimiento de voz del móvil y el texto se apunta como si se hubiera escrito.
    val speech = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            text = com.midinero.control.domain.SpokenEntry.normalize(spoken)
            submit()
        }
    }
    fun dictate() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, com.midinero.control.domain.Lang.locale.toLanguageTag())
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, tr("Di el importe y en qué: «doce con cincuenta café»", "Say the amount and what for: “twelve fifty coffee”"))
        try {
            speech.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar(tr("Este móvil no tiene reconocimiento de voz", "This phone has no speech recognition")) }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(100) },
        placeholder = { Text(tr("Apunta rápido: 12,50 café ayer", "Quick add: 12.50 coffee yesterday")) },
        leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        singleLine = true,
        shape = CircleShape,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            if (text.isBlank()) {
                IconButton(onClick = { dictate() }) {
                    Icon(Icons.Filled.Mic, contentDescription = tr("Dictar un gasto", "Dictate an expense"))
                }
            } else {
                IconButton(onClick = { submit() }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("Apuntar", "Add"))
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Consejo breve con emoji; si es de una categoría, al tocar se abre su detalle. */
@Composable
fun TipCard(tip: Tip, onClick: (() -> Unit)?) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tip.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(12.dp))
            Text(tip.text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (tip.important) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    text: String,
    button: String,
    onClick: () -> Unit,
    secondary: Pair<String, () -> Unit>? = null,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(Lime, CircleShape).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClick) { Text(button) }
                if (secondary != null) {
                    OutlinedButton(onClick = secondary.second) { Text(secondary.first) }
                }
            }
        }
    }
}

/** Elegir cómo compartir el resumen del mes: como imagen o como texto. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ShareMonthSheet(onDismiss: () -> Unit, onImage: () -> Unit, onText: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                tr("Compartir el resumen del mes", "Share the month summary"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp).asHeading(),
            )
            MenuRow(
                Icons.Filled.Image, SectionColors.trips, tr("Como imagen", "As an image"),
                tr("Una tarjeta con el total, la curva del mes y en qué se ha ido", "A card with the total, the month's curve and where it went"),
                onClick = onImage,
            )
            MenuRow(
                Icons.Filled.Description, SectionColors.settings, tr("Como texto", "As text"),
                tr("Para pegarlo en un mensaje", "To paste into a message"),
                onClick = onText, divider = true,
            )
        }
    }
}

/** «Si sigues así»: cómo acabará el mes frente al presupuesto y lo que sobrará o faltará con tus ingresos. */
@Composable
fun ForecastCard(forecast: com.midinero.control.domain.MonthForecast, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Si sigues así", "If you keep this up"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (forecast.daysLeft == 1) tr("queda 1 día", "1 day left") else tr("quedan ${forecast.daysLeft} días", "${forecast.daysLeft} days left"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                tr("Acabarás el mes gastando unos ${formatCents(forecast.projectedSpendCents)}", "You'll end the month spending about ${formatCents(forecast.projectedSpendCents)}"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            forecast.vsBudgetCents?.let { diff ->
                val over = diff > 0
                Text(
                    if (over) tr("${formatCents(diff)} por encima de tu presupuesto", "${formatCents(diff)} over your budget")
                    else tr("${formatCents(-diff)} por debajo de tu presupuesto", "${formatCents(-diff)} under your budget"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (over) BudgetColors.over else BudgetColors.ok,
                )
            }
            forecast.balanceCents?.let { balance ->
                Spacer(Modifier.height(10.dp))
                val ok = balance >= 0
                Text(
                    if (ok) tr("Con tus ingresos te sobrarán", "With your income you'll have left") else tr("Con tus ingresos te faltarán", "With your income you'll be short"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    (if (ok) "+" else "−") + formatCents(kotlin.math.abs(balance)),
                    style = MaterialTheme.typography.headlineSmall.merge(MoneyStyle),
                    fontWeight = FontWeight.Bold,
                    color = if (ok) BudgetColors.ok else BudgetColors.over,
                )
                if (forecast.pendingIncomeCents > 0) Text(
                    tr("Cuenta ${formatCents(forecast.pendingIncomeCents)} de ingresos fijos que aún no han llegado", "Includes ${formatCents(forecast.pendingIncomeCents)} of recurring income still to come"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
