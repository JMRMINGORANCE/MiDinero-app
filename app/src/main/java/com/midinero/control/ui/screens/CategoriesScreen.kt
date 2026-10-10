package com.midinero.control.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.data.Category
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.tr
import com.midinero.control.ui.MainViewModel

/** Tus categorías: crear, editar, ordenar, ocultar y borrar (pasando sus movimientos a otra). */
@Composable
fun CategoriesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val expense by vm.expenseCategories.collectAsStateWithLifecycle()
    val income by vm.incomeCategories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = com.midinero.control.ui.LocalSnackbarHostState.current
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var creatingIncome by rememberSaveable { mutableStateOf(false) }
    val all = expense + income
    val editing = editingId?.let { id -> all.firstOrNull { it.id == id } }
    fun duplicate() = Toast.makeText(context, tr("Ya hay una categoría con ese nombre", "A category with that name already exists"), Toast.LENGTH_SHORT).show()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                ScreenHeader(
                    tr("Categorías", "Categories"),
                    subtitle = tr("Crea las tuyas, ordénalas con las flechas y oculta las que no uses.", "Create your own, order them with the arrows and hide the ones you don't use."),
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item { GroupTitle(tr("Gastos", "Expenses")) }
            itemsIndexed(expense, key = { _, it -> it.id }) { i, cat ->
                CategoryOrderRow(cat, i, expense, vm::reorderCategories) { editingId = cat.id }
            }
            item { GroupTitle(tr("Ingresos", "Income")) }
            itemsIndexed(income, key = { _, it -> it.id }) { i, cat ->
                CategoryOrderRow(cat, i, income, vm::reorderCategories) { editingId = cat.id }
            }
            item {
                TextButton(onClick = { creatingIncome = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(tr("+ Nueva categoría de ingresos", "+ New income category"))
                }
            }
        }
        AppFab(
            text = tr("Nueva categoría", "New category"),
            action = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { cat ->
        CategoryDialog(
            category = cat,
            onDismiss = { editingId = null },
            onSave = { vm.saveCategory(it) { ok -> if (ok) editingId = null else duplicate() } },
            onDelete = { to -> vm.deleteCategory(cat, to, snackbar); editingId = null },
            moveTargets = all,
        )
    }
    if (creating || creatingIncome) {
        CategoryDialog(
            category = null,
            onDismiss = { creating = false; creatingIncome = false },
            onSave = { vm.saveCategory(it) { ok -> if (ok) { creating = false; creatingIncome = false } else duplicate() } },
            onDelete = null,
            isIncome = creatingIncome,
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).asHeading(),
    )
}

@Composable
private fun CategoryOrderRow(
    category: Category,
    index: Int,
    group: List<Category>,
    onReorder: (List<Long>) -> Unit,
    onClick: () -> Unit,
) {
    fun move(delta: Int) {
        val ids = group.map { it.id }.toMutableList()
        val target = index + delta
        if (target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        onReorder(ids)
    }
    Column(Modifier.grouped(index, group.size)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.alpha(if (category.hidden) 0.45f else 1f)) { CategoryBadge(category, size = 40) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    category.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (category.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val details = listOfNotNull(
                    tr("Oculta", "Hidden").takeIf { category.hidden },
                    category.monthlyLimitCents?.takeIf { it > 0 }?.let { tr("Límite ${formatCents(it)}", "Limit ${formatCents(it)}") },
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { move(-1) }, enabled = index > 0) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = tr("Subir ${category.name}", "Move ${category.name} up"))
            }
            IconButton(onClick = { move(1) }, enabled = index < group.lastIndex) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = tr("Bajar ${category.name}", "Move ${category.name} down"))
            }
        }
        GroupDivider(index, group.size)
    }
}
