package com.midinero.control.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado observable de "ocultar importes". Al ser estado de Compose, las pantallas se
 * redibujan solas cuando cambia, aunque [com.midinero.control.domain.formatCents] sea una función normal.
 */
object PrivacyState {
    var hidden by mutableStateOf(false)
}
