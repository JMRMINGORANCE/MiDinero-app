package com.midinero.control.ui.screens

import androidx.lifecycle.viewmodel.compose.viewModel
import com.midinero.control.ui.DataViewModel
import com.midinero.control.domain.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.midinero.control.domain.CsvImport
import com.midinero.control.domain.CsvMapping
import com.midinero.control.domain.CsvPreview
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.shortLabel
import com.midinero.control.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch

/** Importar movimientos de un CSV exportado desde la web o la app del banco. */
@Composable
fun ImportScreen(onBack: () -> Unit, vm: DataViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var mapping by remember { mutableStateOf<CsvMapping?>(null) }
    var includeIncome by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readBankCsv(uri) { p ->
            preview = p
            mapping = p?.mapping
            error = if (p == null) tr("No he encontrado movimientos en ese fichero. ¿Es un CSV con fechas e importes?", "I couldn't find any transactions in that file. Is it a CSV with dates and amounts?") else null
        }
    }
    val p = preview
    val m = mapping
    val rows = if (p != null && m != null) CsvImport.rows(p, m) else emptyList()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(
                tr("Importar del banco", "Import from bank"),
                subtitle = tr("Descarga tus movimientos en CSV desde la web o la app de tu banco y elígelo aquí. ", "Download your transactions as CSV from your bank's website or app and choose the file here. ") +
                    tr("Se clasificarán con tus reglas y lo aprendido, y no se duplicará lo que ya tengas.", "They'll be categorised with your rules and what the app has learned, and nothing you already have will be duplicated."),
                onBack = onBack,
            )
        }
        item {
            Button(onClick = { runCatching { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) }.onFailure { android.widget.Toast.makeText(context, tr("Este móvil no deja abrir archivos desde aquí", "This phone can't open files from here"), android.widget.Toast.LENGTH_LONG).show() } }) {
                Text(if (p == null) tr("Elegir fichero CSV", "Choose CSV file") else tr("Elegir otro fichero", "Choose another file"))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
        if (p != null && m != null) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("Columnas", "Columns"), fontWeight = FontWeight.SemiBold)
                        Text(
                            tr("Comprueba que cada dato sale de la columna correcta.", "Check that each field comes from the right column."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColumnPicker(tr("Fecha", "Date"), p.header, m.date) { mapping = m.copy(date = it) }
                        ColumnPicker(tr("Concepto", "Description"), p.header, m.description) { mapping = m.copy(description = it) }
                        ColumnPicker(tr("Importe", "Amount"), p.header, m.amount) { mapping = m.copy(amount = it) }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Importar también los ingresos", "Also import income"), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    tr("Importes positivos (nómina, transferencias recibidas...)", "Positive amounts (salary, transfers received...)"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = includeIncome, onCheckedChange = { includeIncome = it })
                        }
                    }
                }
            }
            item {
                val spent = rows.filter { it.amountCents < 0 }
                val income = rows.filter { it.amountCents > 0 }
                Text(
                    tr("${rows.size} movimientos: ${spent.size} gastos (${formatCents(-spent.sumOf { it.amountCents })})", "${rows.size} transactions: ${spent.size} expenses (${formatCents(-spent.sumOf { it.amountCents })})") +
                        if (income.isNotEmpty()) tr(" y ${income.size} ingresos (${formatCents(income.sumOf { it.amountCents })})", " and ${income.size} income (${formatCents(income.sumOf { it.amountCents })})") else "",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(rows.take(12)) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.date.shortLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        (if (r.amountCents > 0) "+" else "-") + formatCents(kotlin.math.abs(r.amountCents)),
                        fontWeight = FontWeight.SemiBold,
                        color = if (r.amountCents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider()
            }
            if (rows.size > 12) {
                item { Text(tr("…y ${rows.size - 12} más", "…and ${rows.size - 12} more"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Button(
                    enabled = rows.isNotEmpty() && !importing,
                    onClick = {
                        importing = true
                        vm.importBankCsv(p, m, includeIncome) { r ->
                            importing = false
                            if (r == null) {
                                error = tr("No se han podido importar los movimientos. Prueba de nuevo.", "Couldn't import the transactions. Please try again.")
                                return@importBankCsv
                            }
                            scope.launch {
                                snackbar.showSnackbar(
                                    tr("Importados ${r.added}", "Imported ${r.added}") +
                                        (if (r.duplicates > 0) tr(" · ${r.duplicates} ya estaban", " · ${r.duplicates} already there") else "") +
                                        (if (r.skippedIncome > 0) tr(" · ${r.skippedIncome} ingresos omitidos", " · ${r.skippedIncome} income skipped") else "")
                                )
                            }
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (importing) tr("Importando…", "Importing…") else tr("Importar ${rows.size} movimientos", "Import ${rows.size} transactions")) }
            }
        }
    }
}

@Composable
private fun ColumnPicker(label: String, header: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(0.65f)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(header.getOrNull(selected) ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                header.forEachIndexed { i, name ->
                    DropdownMenuItem(text = { Text(name.ifBlank { tr("Columna ${i + 1}", "Column ${i + 1}") }) }, onClick = { onSelect(i); open = false })
                }
            }
        }
    }
}
