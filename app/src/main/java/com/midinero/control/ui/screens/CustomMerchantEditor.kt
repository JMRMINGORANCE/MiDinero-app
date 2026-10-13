package com.midinero.control.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.midinero.control.data.Category
import com.midinero.control.data.CustomMerchant
import com.midinero.control.data.MerchantPhotos
import com.midinero.control.domain.tr
import kotlinx.coroutines.launch

/**
 * Crear o editar un comercio tuyo: nombre, textos con los que aparece en los avisos, categoría y una foto del móvil.
 * [onSave] recibe el comercio y una función a la que avisar con false si el nombre ya existe.
 */
@Composable
fun CustomMerchantEditor(
    existing: CustomMerchant?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (CustomMerchant, (Boolean) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    initialName: String = "",
    initialCategoryId: Long? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name ?: initialName) }
    var aliases by rememberSaveable { mutableStateOf(existing?.aliases ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: initialCategoryId) }
    var photo by remember { mutableStateOf(existing?.photo) }
    var loading by remember { mutableStateOf(false) }
    var nameTaken by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        scope.launch {
            val bytes = MerchantPhotos.fromUri(context, uri)
            loading = false
            if (bytes != null) photo = bytes
            else android.widget.Toast.makeText(context, tr("No se ha podido leer esa imagen", "That image couldn't be read"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val preview = CustomMerchant(id = existing?.id ?: 0, name = name.ifBlank { "?" }, photo = photo)

    EditorSheet(
        title = if (existing == null) tr("Nuevo comercio", "New merchant") else tr("Editar comercio", "Edit merchant"),
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = name.isNotBlank() && !loading,
        closeOnPrimary = false,
        onPrimary = {
            val merchant = (existing ?: CustomMerchant(name = "")).copy(name = name.trim(), aliases = aliases, categoryId = categoryId, photo = photo)
            onSave(merchant) { ok -> if (ok) onDismiss() else nameTaken = true }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CustomMerchantBadge(preview, categories.firstOrNull { it.id == categoryId }, size = 72)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = {
                        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .onFailure { android.widget.Toast.makeText(context, tr("Este móvil no deja abrir la galería desde aquí", "This phone can't open the gallery from here"), android.widget.Toast.LENGTH_LONG).show() }
                    },
                    enabled = !loading,
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            loading -> tr("Cargando…", "Loading…")
                            photo == null -> tr("Elegir foto", "Choose photo")
                            else -> tr("Cambiar foto", "Change photo")
                        }
                    )
                }
                if (photo != null) TextButton(onClick = { photo = null }) { Text(tr("Quitar foto", "Remove photo")) }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60); nameTaken = false },
            label = { Text(tr("Nombre", "Name")) },
            isError = nameTaken,
            supportingText = if (nameTaken) { { Text(tr("Ya tienes un comercio con ese nombre", "You already have a merchant with that name")) } } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = aliases,
            onValueChange = { aliases = it.take(200) },
            label = { Text(tr("Cómo aparece en el banco (opcional)", "How it shows at your bank (optional)")) },
            supportingText = {
                Text(
                    tr(
                        "Otros textos de sus pagos, separados por comas. Ej.: «BAR PACO SL, PACO TAPAS». Con el nombre ya basta si aparece tal cual.",
                        "Other texts from its payments, separated by commas. E.g. “BAR PACO SL, PACO TAPAS”. The name is enough if it shows as is.",
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FieldLabel(tr("Categoría (opcional)", "Category (optional)"))
        CategoryPicker(categories = categories.filter { !it.isIncome }, selectedId = categoryId, onSelect = { categoryId = it })
        Text(
            tr(
                "Con categoría, sus pagos se clasifican solos, también los que tenías sin categoría.",
                "With a category, its payments are sorted automatically, including the ones you had without a category.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onDelete != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDelete = true }) {
                Text(tr("Eliminar comercio", "Delete merchant"), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar «${existing?.name}»?", "Delete “${existing?.name}”?")) },
            text = { Text(tr("Sus gastos no se borran: solo dejan de mostrar su foto.", "Its expenses aren't deleted: they just stop showing its photo.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(); onDismiss() }) {
                    Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}
