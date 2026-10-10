package com.midinero.control.ui

import com.midinero.control.domain.tr
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf

/** Snackbar compartido por todas las pantallas (lo muestra el Scaffold principal). */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/** Muestra un mensaje con botón tr("Deshacer", "Undo") y llama a [onUndo] si se pulsa. */
suspend fun SnackbarHostState.showUndo(message: String, onUndo: () -> Unit) {
    currentSnackbarData?.dismiss()
    val result = showSnackbar(message, actionLabel = tr("Deshacer", "Undo"), withDismissAction = true, duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed) onUndo()
}
