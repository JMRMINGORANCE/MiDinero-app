package com.midinero.control.ui.screens

import com.midinero.control.domain.signedAmount
import com.midinero.control.domain.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.label
import com.midinero.control.domain.monthOf
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.MainViewModel

/** Buscar en todos los meses por comercio, nota, categoría o importe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: MainViewModel, onBack: () -> Unit) {
    val text by vm.search.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val byId = categories.associateBy { it.id }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val focus = remember { FocusRequester() }
    val recent = remember(text.isEmpty()) { vm.prefs.recentSearches }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Volver", "Back")) }
                OutlinedTextField(
                    value = text,
                    onValueChange = vm::setSearch,
                    placeholder = { Text(tr("Comercio, importe o fecha", "Merchant, amount or date")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (text.isNotEmpty()) IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Filled.Close, contentDescription = tr("Borrar búsqueda", "Clear search")) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.rememberSearch(text) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        }
        if (text.isBlank()) {
            item {
                Column(Modifier.padding(vertical = 16.dp)) {
                    Text(
                        tr("Busca en todos tus meses. Puedes usar importes: «12,50», «>50», «<10» o «20-40», ", "Search across all your months. You can use amounts: “12.50”, “>50”, “<10” or “20-40”, ") +
                            tr("y fechas: «ayer», «septiembre», «12/09», «mes pasado»; todo se combina: «amazon >30 septiembre».", "and dates: “yesterday”, “september”, “12/09”, “last month”; everything combines: “amazon >30 september”."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (recent.isNotEmpty()) {
                        Text(tr("Recientes", "Recent"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            recent.forEach { r -> SuggestionChip(onClick = { vm.setSearch(r) }, label = { Text(r) }) }
                        }
                    }
                }
            }
        } else {
            item {
                val spent = results.filter { !it.isIncome }.sumOf { it.amountCents }
                Text(
                    if (results.isEmpty()) tr("Nada coincide con «${text.trim()}».", "Nothing matches “${text.trim()}”.")
                    else "${results.size}${if (results.size >= 300) "+" else ""} ${if (results.size == 1) tr("movimiento", "transaction") else tr("movimientos", "transactions")}" +
                        if (spent > 0) tr(" · ${formatCents(spent)} en gastos", " · ${formatCents(spent)} in expenses") else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val byMonth = results.groupBy { monthOf(it.timestamp) }
            byMonth.forEach { (month, list) ->
                item(key = "m_$month") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                        Text(month.label(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).asHeading())
                        val monthSpent = list.filter { !it.isIncome }.sumOf { it.amountCents }
                        if (monthSpent > 0) Text(formatCents(monthSpent), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(list, key = { it.id }) { e ->
                    val cat = e.categoryId?.let { byId[it] }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.rememberSearch(text)
                                editingId = e.id
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOfNotNull(cat?.emoji, if (e.receipt != null) "📎" else null, e.merchant).joinToString(" "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(dateOf(e.timestamp).shortLabel(), cat?.name, e.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            signedAmount(e),
                            fontWeight = FontWeight.SemiBold,
                            color = if (e.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    results.firstOrNull { it.id == editingId }?.let { old ->
        ExpenseEditorDialog(
            expense = old,
            categories = expenseCategories,
            incomeCategories = incomeCategories,
            merchantSuggestions = merchants,
            trips = trips,
            rateFor = vm.prefs::rateFor,
            onCreateCategory = vm::createCategory,
            onDismiss = { editingId = null },
            onSave = { d ->
                d.rate?.let { r -> d.originalCurrency?.let { vm.prefs.setRate(it, r) } }
                vm.updateExpense(
                    old,
                    old.copy(
                        amountCents = d.amountCents, merchant = d.merchant, note = d.note, categoryId = d.categoryId,
                        timestamp = d.timestamp, isIncome = d.isIncome,
                        originalCurrency = d.originalCurrency, originalAmountCents = d.originalAmountCents,
                        tripId = d.tripId, receipt = d.receipt,
                    ),
                    d.learn,
                )
                editingId = null
            },
            onDelete = {
                editingId = null
                vm.deleteExpense(old)
            },
        )
    }
}
