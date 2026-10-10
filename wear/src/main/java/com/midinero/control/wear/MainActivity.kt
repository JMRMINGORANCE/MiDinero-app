package com.midinero.control.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val openAdd = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.init(this)
        openAdd.value = intent?.getStringExtra(EXTRA_OPEN) == "add"
        setContent { WatchTheme { WatchAppUi(openAdd.value) { openAdd.value = false } } }
        // Para los avisos de límite (Wear OS 4 o posterior pide permiso)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(EXTRA_OPEN) == "add") openAdd.value = true
    }

    override fun onResume() {
        super.onResume()
        WatchSync.scope.launch { WatchSync.refresh(applicationContext) }
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}

@Composable
fun WatchAppUi(openAdd: Boolean, onOpened: () -> Unit) {
    val context = LocalContext.current
    val nav = rememberSwipeDismissableNavController()
    val state by WatchStore.state.collectAsStateWithLifecycle()
    val pending by WatchStore.pending.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var amount by rememberSaveable { mutableLongStateOf(0L) }
    var note by rememberSaveable { mutableStateOf("") }
    // Dictado: «doce con cincuenta café» → importe y concepto, que se confirman eligiendo la categoría
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val parsed = spoken?.let { com.midinero.control.domain.SpokenEntry.amountAndNote(it) }
        if (parsed != null) {
            amount = parsed.first
            note = parsed.second
            nav.navigate("category")
        } else if (spoken != null) {
            android.widget.Toast.makeText(context, tr("No he entendido el importe: escríbelo", "I didn't catch the amount: type it"), android.widget.Toast.LENGTH_SHORT).show()
            note = ""
            nav.navigate("amount")
        }
    }
    val startVoice: () -> Unit = {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (WatchStore.english) "en-GB" else "es-ES")
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, tr("¿Cuánto y en qué?", "How much and on what?"))
        val ok = runCatching { voice.launch(intent) }.isSuccess
        if (!ok) {
            android.widget.Toast.makeText(context, tr("El reloj no tiene dictado: usa el teclado", "No dictation on this watch: use the keypad"), android.widget.Toast.LENGTH_SHORT).show()
            nav.navigate("amount")
        }
    }
    var picked by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val sync: () -> Unit = { scope.launch { WatchSync.refresh(context.applicationContext); now = System.currentTimeMillis() } }
    LaunchedEffect(openAdd) {
        if (openAdd) {
            nav.navigate("amount")
            onOpened()
        }
    }
    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                state, pending, now,
                onAdd = { note = ""; nav.navigate("amount") },
                onSync = sync,
                onSettings = { nav.navigate("settings") },
                onVoice = startVoice,
            )
        }
        composable("amount") {
            AmountScreen { cents ->
                amount = cents
                nav.navigate("category")
            }
        }
        composable("category") {
            CategoryScreen(amount, state.quick, note) { c ->
                WatchSync.add(
                    context.applicationContext, amount, c?.id, c?.emoji.orEmpty(),
                    c?.name ?: tr("Gasto desde el reloj", "Expense from watch"), c?.color ?: 0xFF8A8A93.toInt(), note,
                )
                picked = listOfNotNull(note.takeIf { it.isNotBlank() }, c?.let { "${it.emoji} ${it.name}" }).joinToString(" · ")
                    .ifBlank { tr("Sin categoría", "No category") }
                note = ""
                nav.navigate("done") { popUpTo("home") }
            }
        }
        composable("done") {
            DoneScreen(amount, picked) { nav.popBackStack("home", inclusive = false) }
        }
        composable("settings") {
            SettingsScreen(state, WatchPaymentListener.isEnabled(context), now, sync)
        }
    }
}
