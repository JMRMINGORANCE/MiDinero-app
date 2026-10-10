package com.midinero.control.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Avisa cuando se vuelve a pulsar la pestaña en la que ya se está (su ruta). */
val LocalTabReselect = staticCompositionLocalOf<SharedFlow<String>> { MutableSharedFlow() }

/** Al volver a pulsar la pestaña [route], la lista sube hasta arriba. */
@Composable
fun ScrollToTopOnReselect(route: String, state: LazyListState) {
    val reselect = LocalTabReselect.current
    LaunchedEffect(reselect, state) {
        reselect.collect { if (it == route && state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset > 0) state.animateScrollToItem(0) }
    }
}
