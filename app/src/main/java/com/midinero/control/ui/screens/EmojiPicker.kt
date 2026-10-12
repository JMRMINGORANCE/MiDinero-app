package com.midinero.control.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midinero.control.domain.EmojiCatalog
import com.midinero.control.domain.EmojiEntry
import com.midinero.control.domain.tr

/** Todos los iconos por temas, con buscador («perro», «gym», «luz»…). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { EmojiCatalog.search(query) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            Text(
                tr("Elige un icono", "Choose an icon"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).asHeading(),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Buscar: perro, gym, luz, regalo…", "Search: dog, gym, light, gift…")) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.medium,
                colors = softFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(52.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (query.isNotBlank()) {
                    if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            tr(
                                "No hay ninguno con ese nombre. Puedes escribir cualquier emoji con el teclado en el campo «Icono».",
                                "None with that name. You can type any emoji with the keyboard in the “Icon” field.",
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    items(results, key = { "r_${it.emoji}" }) { EmojiCell(it) { onPick(it.emoji) } }
                } else {
                    EmojiCatalog.groups.forEach { group ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h_${group.es}") {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp).asHeading(),
                            )
                        }
                        items(group.entries, key = { "${group.es}_${it.emoji}" }) { EmojiCell(it) { onPick(it.emoji) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(entry: EmojiEntry, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = entry.keywords.substringBefore(' ') },
        contentAlignment = Alignment.Center,
    ) {
        Text(entry.emoji, fontSize = 26.sp)
    }
}
