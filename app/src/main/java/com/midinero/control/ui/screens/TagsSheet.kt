package com.midinero.control.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.shortLabel
import com.midinero.control.domain.tr
import com.midinero.control.ui.MainViewModel

/** Tus etiquetas (#cumpleaños, #coche…) con lo gastado en cada una; al tocar una, sus movimientos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val totals by vm.tagTotals.collectAsState(initial = null)
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            val tag = selected?.let { k -> totals?.firstOrNull { it.key == k } }
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tag?.let { "#${it.label}" } ?: tr("Etiquetas", "Tags"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).asHeading(),
                )
                if (tag != null) TextButton(onClick = { selected = null }) { Text(tr("Todas", "All")) }
            }
            if (tag == null) {
                Text(
                    tr(
                        "Escribe #etiquetas en la nota de un gasto para juntar gastos de categorías distintas: #cumpleaños, #coche, #trabajo… También puedes buscarlas en el buscador.",
                        "Write #tags in an expense's note to group expenses from different categories: #birthday, #car, #work… You can also search for them.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    val list = totals.orEmpty()
                    if (totals != null && list.isEmpty()) item {
                        Text(tr("Aún no has usado ninguna etiqueta.", "You haven't used any tags yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                    }
                    items(list, key = { it.key }) { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = t.key }.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("#${t.label}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (t.count == 1) tr("1 gasto", "1 expense") else tr("${t.count} gastos", "${t.count} expenses"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(formatCents(t.totalCents), style = MaterialTheme.typography.titleSmall.merge(com.midinero.control.ui.theme.MoneyStyle))
                        }
                    }
                }
            } else {
                val flow = remember(tag.key) { vm.taggedExpenses(tag.key) }
                val movements by flow.collectAsState(initial = emptyList())
                Text(
                    tr("${formatCents(tag.totalCents)} en ${tag.count} gastos", "${formatCents(tag.totalCents)} in ${tag.count} expenses"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(movements, key = { it.id }) { e ->
                        val category = categories.firstOrNull { it.id == e.categoryId }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MerchantBadge(e.merchant, category, size = 36)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.merchant, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(dateOf(e.timestamp).shortLabel(), category?.name, e.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                (if (e.isIncome) "+" else "") + formatCents(e.amountCents),
                                style = MaterialTheme.typography.titleSmall.merge(com.midinero.control.ui.theme.MoneyStyle),
                            )
                        }
                    }
                }
            }
        }
    }
}
