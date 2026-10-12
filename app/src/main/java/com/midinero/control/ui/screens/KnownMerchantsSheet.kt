package com.midinero.control.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midinero.control.domain.Categorizer
import com.midinero.control.domain.CategoryNames
import com.midinero.control.domain.CustomMerchants
import com.midinero.control.domain.MerchantCatalog
import com.midinero.control.ui.MainViewModel
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.domain.tr

/** Los comercios que MiDinero reconoce desde el primer día, por categoría y con buscador. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownMerchantsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = Categorizer.normalize(query)
    val mine by vm.customMerchants.collectAsStateWithLifecycle()
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    // null: cerrado; nombre vacío: comercio nuevo
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val shownMine = mine.filter { m -> q.isBlank() || CustomMerchants.patterns(m).any { Categorizer.normalize(it).contains(q) } }
    val groups = remember(q) {
        MerchantCatalog.all
            .filter { m -> q.isBlank() || m.aliases.any { it.contains(q) } || Categorizer.normalize(CategoryNames.localized(m.category)).contains(q) }
            .sortedBy { Categorizer.normalize(it.name) }
            .groupBy { it.category }
            .toSortedMap(compareBy { CategoryNames.localized(it) })
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding()) {
            Text(
                tr("Comercios que ya conoce", "Merchants it already knows"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).asHeading(),
            )
            Text(
                tr(
                    "${MerchantCatalog.all.size} comercios habituales: sus pagos se clasifican solos y se muestran con su nombre limpio. Si cambias la categoría de uno, manda la tuya.",
                    "${MerchantCatalog.all.size} common merchants: their payments are sorted automatically and shown with a clean name. If you change one's category, yours wins.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Buscar comercio o categoría", "Search merchant or category")) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = softFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                item(key = "mine_header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tr("Tus comercios", "Your merchants") + if (mine.isNotEmpty()) " · ${mine.size}" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).asHeading(),
                        )
                        TextButton(onClick = { editing = query.trim() }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Añadir", "Add"))
                        }
                    }
                    if (mine.isEmpty()) Text(
                        tr(
                            "Añade el bar de tu barrio o cualquier comercio con una foto de tu móvil. También puedes tocar uno de la lista para ponerle tu foto.",
                            "Add your local bar or any merchant with a photo from your phone. You can also tap one on the list to give it your photo.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                items(shownMine, key = { "mine_${it.id}" }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = m.name }.padding(horizontal = 24.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CustomMerchantBadge(m, categories.firstOrNull { it.id == m.categoryId }, size = 36)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Medium)
                            val category = categories.firstOrNull { it.id == m.categoryId }
                            val detail = listOfNotNull(category?.let { "${it.emoji} ${it.name}" }, m.aliases.takeIf { it.isNotBlank() }).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                if (groups.isEmpty()) item {
                    Text(
                        tr("No está en la lista. Añádelo arriba en «Tus comercios» o crea una regla.", "Not on the list. Add it above under “Your merchants” or create a rule."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                groups.forEach { (category, merchants) ->
                    item(key = "h_$category") {
                        Text(
                            "${CategoryNames.localized(category)} · ${merchants.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).asHeading(),
                        )
                    }
                    items(merchants, key = { it.name }) { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = m.name }.padding(horizontal = 24.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            MerchantBadge(m.name, null, size = 36)
                            Spacer(Modifier.width(12.dp))
                            Text(m.name, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
    editing?.let { name ->
        val existing = mine.firstOrNull { it.name.equals(name, ignoreCase = true) }
        CustomMerchantEditor(
            existing = existing,
            initialName = name,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { merchant, done -> vm.saveCustomMerchant(merchant, done) },
            onDelete = existing?.let { { vm.deleteCustomMerchant(it) } },
        )
    }
}
