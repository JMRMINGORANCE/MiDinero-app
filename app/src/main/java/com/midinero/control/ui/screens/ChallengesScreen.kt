package com.midinero.control.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.domain.ChallengeProgress
import com.midinero.control.domain.ChallengeState
import com.midinero.control.domain.ChallengeType
import com.midinero.control.domain.Challenges
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.tr
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.BudgetColors

/** Retos de ahorro: días sin gastos, gastar menos en una categoría y rachas. Avisan al cumplirse. */
@Composable
fun ChallengesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val progress by vm.challengeProgress.collectAsStateWithLifecycle()
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    tr("Retos de ahorro", "Savings challenges"),
                    subtitle = tr("Ponte un reto y te avisamos cuando lo consigas. Los de cada mes vuelven a empezar el día 1.", "Set yourself a challenge and we'll tell you when you make it. Monthly ones start again each month."),
                    onBack = onBack,
                )
            }
            if (progress.isEmpty()) item {
                Text(
                    tr("Aún no tienes retos. Prueba con «8 días sin gastos este mes» o «menos de 60 ${com.midinero.control.domain.Currencies.symbol()} en comida a domicilio».", "No challenges yet. Try “8 no-spend days this month” or “under 60 ${com.midinero.control.domain.Currencies.symbol()} on food delivery”."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(progress, key = { it.challenge.id }) { p ->
                ChallengeCard(p, categories, onDelete = { deleting = p.challenge.id })
            }
        }
        AppFab(
            text = tr("Nuevo reto", "New challenge"),
            action = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) NewChallengeDialog(categories, onDismiss = { creating = false }) { type, target, categoryId ->
        vm.addChallenge(type, target, categoryId)
        creating = false
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("¿Quitar este reto?", "Remove this challenge?")) },
            confirmButton = { TextButton(onClick = { vm.deleteChallenge(id); deleting = null }) { Text(tr("Quitar", "Remove"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

@Composable
fun ChallengeCard(p: ChallengeProgress, categories: List<Category>, onDelete: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val c = p.challenge
    val categoryName = c.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
    val color = when (p.state) {
        ChallengeState.COMPLETED -> BudgetColors.ok
        ChallengeState.FAILED -> BudgetColors.over
        ChallengeState.IN_PROGRESS -> if (c.type == ChallengeType.CATEGORY_CAP && p.ratio >= 0.8f) BudgetColors.warning else MaterialTheme.colorScheme.primary
    }
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (p.state) { ChallengeState.COMPLETED -> "🏆"; ChallengeState.FAILED -> "💥"; else -> "🎯" } + "  " + Challenges.title(c, categoryName),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) TextButton(onClick = onDelete) { Text(tr("Quitar", "Remove")) }
            }
            Spacer(Modifier.height(8.dp))
            // En el límite de categoría, la barra muestra lo gastado: llenarla es malo
            BudgetBar(p.ratio, color, height = 8)
            Spacer(Modifier.height(6.dp))
            Text(
                when (c.type) {
                    ChallengeType.NO_SPEND_DAYS -> when (p.state) {
                        ChallengeState.COMPLETED -> tr("¡Conseguido! ${p.current} días sin gastos este mes", "Done! ${p.current} no-spend days this month")
                        ChallengeState.FAILED -> tr("Este mes ya no da tiempo (${p.current} de ${c.target}). El mes que viene, otra vez.", "Not enough days left this month (${p.current} of ${c.target}). Try again next month.")
                        else -> tr("${p.current} de ${c.target} días", "${p.current} of ${c.target} days")
                    }
                    ChallengeType.CATEGORY_CAP -> when (p.state) {
                        ChallengeState.FAILED -> tr("Te has pasado: ${formatCents(p.current)} de ${formatCents(c.target)}", "Over the limit: ${formatCents(p.current)} of ${formatCents(c.target)}")
                        else -> tr("Llevas ${formatCents(p.current)}; te quedan ${formatCents(c.target - p.current)}", "${formatCents(p.current)} so far; ${formatCents(c.target - p.current)} left")
                    } + if (p.wonLastMonth) tr(" · ✓ el mes pasado lo cumpliste", " · ✓ you made it last month") else ""
                    ChallengeType.CATEGORY_STREAK -> if (p.state == ChallengeState.COMPLETED) tr("¡Conseguido! ${p.current} días seguidos", "Done! ${p.current} days in a row")
                        else tr("Llevas ${p.current} de ${c.target} días seguidos", "${p.current} of ${c.target} days in a row")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NewChallengeDialog(categories: List<Category>, onDismiss: () -> Unit, onCreate: (ChallengeType, Long, Long?) -> Unit) {
    var type by rememberSaveable { mutableStateOf(ChallengeType.NO_SPEND_DAYS) }
    var number by rememberSaveable { mutableStateOf("8") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val needsCategory = type != ChallengeType.NO_SPEND_DAYS
    val target: Long? = if (type == ChallengeType.CATEGORY_CAP) parseUserAmount(number) else number.toLongOrNull()
    val valid = target != null && target > 0 && (type != ChallengeType.NO_SPEND_DAYS || target <= 31) && (!needsCategory || categoryId != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Nuevo reto", "New challenge")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val options = listOf(
                    ChallengeType.NO_SPEND_DAYS to tr("Días sin gastos", "No-spend days"),
                    ChallengeType.CATEGORY_CAP to tr("Gastar menos en…", "Spend less on…"),
                    ChallengeType.CATEGORY_STREAK to tr("Racha sin…", "Streak without…"),
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    options.forEach { (t, label) ->
                        FilterChip(selected = type == t, onClick = {
                            type = t
                            number = when (t) { ChallengeType.NO_SPEND_DAYS -> "8"; ChallengeType.CATEGORY_CAP -> "60"; ChallengeType.CATEGORY_STREAK -> "7" }
                        }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8) },
                    label = {
                        Text(
                            when (type) {
                                ChallengeType.NO_SPEND_DAYS -> tr("Días sin gastos al mes", "No-spend days per month")
                                ChallengeType.CATEGORY_CAP -> tr("Como mucho al mes (${com.midinero.control.domain.Currencies.symbol()})", "At most per month (${com.midinero.control.domain.Currencies.symbol()})")
                                ChallengeType.CATEGORY_STREAK -> tr("Días seguidos", "Days in a row")
                            }
                        )
                    },
                    isError = number.isNotEmpty() && !valid && (!needsCategory || categoryId != null),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (needsCategory) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Categoría", "Category"), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    CategoryPicker(categories = categories, selectedId = categoryId, onSelect = { categoryId = it })
                }
                if (type == ChallengeType.NO_SPEND_DAYS) Text(
                    tr("Los cargos fijos (alquiler, suscripciones…) no cuentan como gasto del día.", "Recurring charges (rent, subscriptions…) don't count as spending that day."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(type, target!!, categoryId.takeIf { needsCategory }) }, enabled = valid) { Text(tr("Crear", "Create")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}
