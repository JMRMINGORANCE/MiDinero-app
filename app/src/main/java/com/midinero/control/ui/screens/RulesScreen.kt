package com.midinero.control.ui.screens

import com.midinero.control.domain.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.data.CustomRule
import com.midinero.control.data.RuleMatch
import com.midinero.control.domain.RuleMatcher
import com.midinero.control.ui.LocalSnackbarHostState
import com.midinero.control.ui.MainViewModel
import kotlinx.coroutines.launch

private val matchLabels get() = listOf(RuleMatch.CONTAINS to tr("Contiene", "Contains"), RuleMatch.STARTS_WITH to tr("Empieza por", "Starts with"), RuleMatch.EXACT to tr("Es igual a", "Equals"))

@Composable
fun RulesScreen(vm: MainViewModel, onBack: () -> Unit, initialPattern: String? = null) {
    val rules by vm.customRules.collectAsStateWithLifecycle()
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    val byId = categories.associateBy { it.id }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(initialPattern != null) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    fun save(rule: CustomRule, apply: Boolean) {
        vm.saveCustomRule(rule, apply) { n ->
            scope.launch {
                snackbar.showSnackbar(
                    if (apply) tr("Regla guardada", "Rule saved") + " · " + (if (n == 1) tr("1 gasto actualizado", "1 expense updated") else tr("$n gastos actualizados", "$n expenses updated")) else tr("Regla guardada", "Rule saved")
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                ScreenHeader(
                    tr("Tus reglas", "Your rules"),
                    subtitle = tr("Di a MiDinero cómo clasificar y cómo llamar a tus comercios. Por ejemplo: si contiene «amzn», ", "Tell MiDinero how to categorise and name your merchants. For example: if it contains “amzn”, ") +
                        tr("categoría Compras y llamarlo «Amazon». Tus reglas mandan sobre lo aprendido.", "category Shopping and call it “Amazon”. Your rules take priority over what it has learned."),
                    onBack = onBack,
                )
            }
            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🧩",
                        title = tr("No tienes reglas todavía", "No rules yet"),
                        text = tr("Por ejemplo: si el comercio contiene «amzn», categoría Compras y llamarlo «Amazon».", "For example: if the merchant contains “amzn”, category Shopping and name it “Amazon”."),
                    )
                }
            }
            items(rules, key = { it.id }) { r ->
                Column(Modifier.fillMaxWidth().clickable { editingId = r.id }.padding(vertical = 12.dp)) {
                    Text(
                        "${matchLabels.first { it.first == r.match }.second} «${r.pattern}»",
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        listOfNotNull(
                            r.categoryId?.let { byId[it] }?.let { "→ ${it.emoji} ${it.name}" },
                            r.renameTo?.takeIf { it.isNotBlank() }?.let { tr("se llamará «$it»", "will be named “$it”") },
                            if (r.priority > 0) tr("prioridad alta", "high priority") else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
        AppFab(
            text = tr("Nueva regla", "New rule"),
            action = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        RuleDialog(
            rule = CustomRule(pattern = initialPattern.orEmpty()),
            isNew = true,
            categories = categories,
            onCreateCategory = vm::createCategory,
            onDismiss = { creating = false },
            onSave = { r, apply -> save(r, apply); creating = false },
            onDelete = null,
        )
    }
    editingId?.let { id ->
        rules.firstOrNull { it.id == id }?.let { r ->
            RuleDialog(
                rule = r,
                isNew = false,
                categories = categories,
                onCreateCategory = vm::createCategory,
                onDismiss = { editingId = null },
                onSave = { updated, apply -> save(updated, apply); editingId = null },
                onDelete = { vm.deleteCustomRule(r); editingId = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(
    rule: CustomRule,
    isNew: Boolean,
    categories: List<Category>,
    onCreateCategory: (Category, (Long?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (CustomRule, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var pattern by rememberSaveable { mutableStateOf(rule.pattern) }
    var match by rememberSaveable { mutableStateOf(rule.match) }
    var categoryId by rememberSaveable { mutableStateOf(rule.categoryId) }
    var rename by rememberSaveable { mutableStateOf(rule.renameTo.orEmpty()) }
    var highPriority by rememberSaveable { mutableStateOf(rule.priority > 0) }
    var apply by rememberSaveable { mutableStateOf(true) }
    var test by rememberSaveable { mutableStateOf("") }
    val draft = rule.copy(
        pattern = pattern.trim(),
        match = match,
        categoryId = categoryId,
        renameTo = rename.trim().ifBlank { null },
        priority = if (highPriority) 10 else 0,
    )
    val valid = pattern.isNotBlank() && (categoryId != null || rename.isNotBlank())

    EditorSheet(
        title = if (isNew) tr("Nueva regla", "New rule") else tr("Editar regla", "Edit rule"),
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = valid,
        onPrimary = { onSave(draft, apply) },
    ) {
        Text(tr("Si el comercio…", "If the merchant…"), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            matchLabels.forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = match == m,
                    onClick = { match = m },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = matchLabels.size),
                    icon = {},
                ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it.take(60) },
            label = { Text(tr("Texto (ej. amzn)", "Text (e.g. amzn)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(tr("…entonces categoría", "…then category"), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        CategoryPicker(categories, categoryId, onCreate = onCreateCategory) { categoryId = it }
        OutlinedTextField(
            value = rename,
            onValueChange = { rename = it.take(60) },
            label = { Text(tr("Y llamarlo (opcional)", "And name it (optional)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(tr("Prioridad alta", "High priority"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = highPriority, onCheckedChange = { highPriority = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = apply, onCheckedChange = { apply = it })
            Text(tr("Aplicar también a los gastos que ya tengo", "Also apply to my existing expenses"), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = test,
            onValueChange = { test = it },
            label = { Text(tr("Probar con un comercio", "Test with a merchant")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (test.isNotBlank() && pattern.isNotBlank()) {
            val ok = RuleMatcher.matches(draft, test)
            Text(
                if (ok) tr("✅ Coincide", "✅ Matches") + (draft.renameTo?.let { tr(" · se guardará como «$it»", " · will be saved as “$it”") } ?: "") else tr("✖ No coincide", "✖ Doesn't match"),
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text(tr("Eliminar regla", "Delete rule"), color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.width(1.dp))
    }
}
