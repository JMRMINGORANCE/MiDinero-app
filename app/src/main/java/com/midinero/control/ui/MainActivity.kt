package com.midinero.control.ui

import com.midinero.control.domain.tr
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.domain.amountsHidden
import com.midinero.control.ui.screens.LockScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.midinero.control.MiDineroApp
import com.midinero.control.service.AppNotifications
import com.midinero.control.service.PaymentNotificationListener
import com.midinero.control.ui.screens.CategoriesScreen
import com.midinero.control.ui.screens.ChallengesScreen
import com.midinero.control.ui.screens.AccountsScreen
import com.midinero.control.ui.screens.CategoryDetailScreen
import com.midinero.control.ui.screens.DebtsScreen
import com.midinero.control.ui.screens.TripDetailScreen
import com.midinero.control.ui.screens.TripsScreen
import com.midinero.control.ui.screens.SearchScreen
import com.midinero.control.ui.screens.ExpensesScreen
import com.midinero.control.ui.screens.MerchantDetailScreen
import com.midinero.control.ui.screens.MerchantsScreen
import com.midinero.control.ui.screens.GoalsScreen
import com.midinero.control.ui.screens.ImportScreen
import com.midinero.control.ui.screens.HelpScreen
import com.midinero.control.ui.screens.HomeScreen
import com.midinero.control.ui.screens.LimitsScreen
import com.midinero.control.ui.screens.OnboardingScreen
import com.midinero.control.ui.screens.RecurringScreen
import com.midinero.control.ui.screens.RulesScreen
import com.midinero.control.ui.screens.SettingsScreen
import com.midinero.control.ui.screens.StatsScreen
import com.midinero.control.ui.theme.MiDineroTheme
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.midinero.control.ui.screens.ExpenseEditorDialog
import com.midinero.control.ui.screens.MoreScreen
import com.midinero.control.ui.screens.defaultDateFor
import com.midinero.control.ui.screens.pressScale
import com.midinero.control.ui.theme.Ink
import com.midinero.control.ui.theme.Lime
import com.midinero.control.ui.theme.LocalDarkTheme

class MainActivity : FragmentActivity() {

    /** Ruta que pide abrir una notificación o un acceso directo ("home", "expenses?edit=3"...). */
    private val openRequest = mutableStateOf<String?>(null)

    /** true mientras la app está bloqueada esperando huella / PIN. */
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    private val app get() = application as MiDineroApp

    override fun onCreate(savedInstanceState: Bundle?) {
        Language.apply(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            openRequest.value = intent?.getStringExtra(AppNotifications.EXTRA_OPEN)
            handleAssistant(intent)
        }
        stoppedAt = savedInstanceState?.getLong(KEY_STOPPED_AT) ?: 0L
        // Al girar la pantalla se conserva el estado: no se vuelve a pedir la huella.
        locked.value = savedInstanceState?.getBoolean(KEY_LOCKED)
            ?: (app.prefs.appLock && AppLock.isAvailable(this))
        PrivacyState.hidden = app.prefs.hideAmounts
        amountsHidden = { PrivacyState.hidden }
        applySecureFlag()
        setContent {
            val prefsVersion by app.prefs.changes.collectAsStateWithLifecycle()
            val themeMode = remember(prefsVersion) { app.prefs.themeMode }
            val dark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
            }
            LaunchedEffect(prefsVersion) {
                PrivacyState.hidden = app.prefs.hideAmounts
                applySecureFlag()
            }
            MiDineroTheme(mode = themeMode) {
                var onboardingDone by remember { mutableStateOf(app.prefs.onboardingDone) }
                // Si la última vez la app se cerró por un error, se ofrece pasar el informe
                var crash by remember { mutableStateOf(com.midinero.control.service.CrashLog.read(this@MainActivity)) }
                crash?.let { report ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { com.midinero.control.service.CrashLog.clear(this@MainActivity); crash = null },
                        title = { androidx.compose.material3.Text(tr("MiDinero se cerró la última vez", "MiDinero closed unexpectedly last time")) },
                        text = {
                            androidx.compose.material3.Text(
                                tr(
                                    "Se ha guardado qué falló (solo en tu móvil). Si lo compartes o lo copias, se puede arreglar.",
                                    "What failed was saved (only on your phone). Share or copy it so it can be fixed.",
                                ),
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                                runCatching { startActivity(Intent.createChooser(send, tr("Compartir informe", "Share report"))) }
                                com.midinero.control.service.CrashLog.clear(this@MainActivity)
                                crash = null
                            }) { androidx.compose.material3.Text(tr("Compartir", "Share")) }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                getSystemService(android.content.ClipboardManager::class.java)
                                    ?.setPrimaryClip(android.content.ClipData.newPlainText("MiDinero", report))
                                com.midinero.control.service.CrashLog.clear(this@MainActivity)
                                crash = null
                            }) { androidx.compose.material3.Text(tr("Copiar", "Copy")) }
                        },
                    )
                }
                // El bloqueo se dibuja encima: lo de debajo (un gasto a medias, la foto del ticket) no se pierde
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Box(
                    if (locked.value) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()
                ) {
                when {
                    !onboardingDone -> OnboardingScreen(
                        onFinish = {
                            app.prefs.onboardingDone = true
                            onboardingDone = true
                        },
                        onTryDemo = {
                            app.appScope.launch { app.repository.loadDemo() }
                            app.prefs.onboardingDone = true
                            onboardingDone = true
                        },
                    )
                    else -> MiDineroRoot(
                        openRequest = openRequest.value,
                        onOpenHandled = { openRequest.value = null },
                    )
                }
                }
                if (locked.value) LockScreen(onUnlock = ::unlock)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value)
        outState.putLong(KEY_STOPPED_AT, stoppedAt)
    }

    private fun unlock() {
        AppLock.authenticate(this) { ok -> if (ok) locked.value = false }
    }

    private fun applySecureFlag() {
        if (app.prefs.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        // Se vuelve a bloquear si la app ha estado en segundo plano más de 30 segundos.
        if (stoppedAt > 0 && app.prefs.appLock && AppLock.isAvailable(this) &&
            System.currentTimeMillis() - stoppedAt > 30_000
        ) {
            locked.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        // Apunta los cargos recurrentes que hayan vencido mientras la app estaba cerrada.
        app.appScope.launch { app.repository.generateDueRecurring() }
        PaymentNotificationListener.ensureConnected(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest.value = intent.getStringExtra(AppNotifications.EXTRA_OPEN)
        handleAssistant(intent)
    }

    /**
     * Asistente de Google: «apunta 4 euros de café» se apunta directamente (con su categoría automática);
     * si no se entiende, o se pidió solo «apuntar gasto», se abre el editor.
     */
    private fun handleAssistant(intent: Intent?) {
        intent ?: return
        val text = intent.getStringExtra("assistant_text")?.trim()
        val feature = intent.getStringExtra("assistant_feature")
        if (text.isNullOrEmpty() && feature == null) return
        val entry = text?.let { com.midinero.control.domain.AssistantPhrase.toEntry(it) }
        if (entry == null) {
            openRequest.value = "expenses?add=true"
            if (!text.isNullOrEmpty()) android.widget.Toast.makeText(this, tr("No he entendido el importe: apúntalo aquí", "I didn't catch the amount: log it here"), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val time = if (entry.date == java.time.LocalDate.now()) java.time.LocalTime.now() else java.time.LocalTime.NOON
        val ts = entry.date.atTime(time).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        app.appScope.launch {
            val categoryId = if (entry.isIncome) null else app.repository.categorize(entry.merchant)
            app.repository.addExpense(
                com.midinero.control.data.Expense(
                    amountCents = entry.amountCents, merchant = entry.merchant.replaceFirstChar { it.uppercase() },
                    categoryId = categoryId, timestamp = ts, isIncome = entry.isIncome,
                    accountId = app.repository.defaultAccountId(),
                )
            )
        }
        android.widget.Toast.makeText(
            this,
            tr("Apuntado: ${com.midinero.control.domain.formatCents(entry.amountCents)} · ${entry.merchant}", "Logged: ${com.midinero.control.domain.formatCents(entry.amountCents)} · ${entry.merchant}"),
            android.widget.Toast.LENGTH_LONG,
        ).show()
        openRequest.value = "expenses"
    }
}

private const val KEY_LOCKED = "locked"
private const val KEY_STOPPED_AT = "stopped_at"

private enum class Tab(
    val route: String,
    private val es: String,
    private val en: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("home", "Inicio", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    EXPENSES("expenses", "Actividad", "Activity", Icons.Outlined.Receipt, Icons.Filled.Receipt),
    STATS("stats", "Análisis", "Insights", Icons.Outlined.PieChart, Icons.Filled.PieChart),
    MORE("more", "Más", "More", Icons.Outlined.GridView, Icons.Filled.GridView);

    val label: String get() = tr(es, en)
}

private const val SETTINGS_ROUTE = "settings"
private const val RECURRING_ROUTE = "recurring"
private const val LIMITS_ROUTE = "limits"

@Composable
private fun MiDineroRoot(openRequest: String?, onOpenHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val vm: MainViewModel = viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore("?")
    var adding by rememberSaveable { mutableStateOf(false) }
    val reselect = remember { kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1) }
    val month by vm.month.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()

    fun go(route: String) {
        if (route == Tab.HOME.route) {
            // Inicio es la raíz: se vuelve a ella quitando lo que haya encima. No se «restaura» su pila guardada,
            // porque podía contener Actividad (abierta desde una categoría o un movimiento) y se volvía a abrir.
            val start = nav.graph.findStartDestination().id
            if (!nav.popBackStack(start, inclusive = false)) nav.navigate(route) { launchSingleTop = true }
            return
        }
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun open(route: String) = nav.navigate(route) { launchSingleTop = true }
    // Pulsar la pestaña en la que ya se está sube su lista hasta arriba; si no, se cambia de pestaña.
    fun select(tab: Tab) {
        if (currentRoute == tab.route) reselect.tryEmit(tab.route) else go(tab.route)
    }

    fun openCategoryMovements(id: Long) = open("expenses?filter=category:$id")
    fun openCategory(id: Long) = open("category/$id")
    fun openMerchant(key: String) {
        // Un comercio sin letras ("1234") no tiene clave: no hay historial que abrir.
        if (key.isNotBlank()) open("merchant/${Uri.encode(key)}")
    }

    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            if (openRequest.startsWith("expenses?")) {
                // Un aviso de un gasto nuevo: se muestra el mes actual.
                vm.goToCurrentMonth()
                nav.navigate(openRequest) { launchSingleTop = true }
            } else {
                go(openRequest)
            }
            onOpenHandled()
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar, com.midinero.control.ui.screens.LocalTabReselect provides reselect) {
        // En tablets y en horizontal (ancho >= 600 dp) la navegación va en una barra lateral.
        val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
        val wide = screenWidth >= 600.dp
        // En tablets y plegables abiertos (>= 900 dp), Inicio muestra Actividad a su lado.
        val twoPane = screenWidth >= 900.dp
        // La barra solo se ve en las cuatro pantallas principales; en el resto se esconde deslizándose.
        val rootScreen = currentRoute == null || Tab.entries.any { it.route == currentRoute }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AnimatedVisibility(
                    visible = !wide && rootScreen,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    FloatingNavBar(currentRoute = currentRoute, onSelect = ::select, onAdd = { adding = true })
                }
            }
        ) { padding ->
          Row(Modifier.padding(padding).fillMaxSize()) {
            if (wide) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.background,
                    header = {
                        FloatingActionButton(
                            onClick = { adding = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Icon(Icons.Filled.Add, contentDescription = tr("Añadir gasto", "Add expense")) }
                    },
                ) {
                    Spacer(Modifier.weight(1f))
                    Tab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { select(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            // El contenido no se estira más de 840 dp para que se lea bien en pantallas grandes.
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            NavHost(
                navController = nav,
                startDestination = Tab.HOME.route,
                modifier = Modifier.widthIn(max = if (twoPane && currentRoute == Tab.HOME.route) 1500.dp else 840.dp).fillMaxSize(),
                // Las pantallas entran con un fundido y un leve zoom; al volver, al revés.
                enterTransition = { fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 1.03f) },
                popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.97f) },
            ) {
                composable(Tab.HOME.route) {
                  Row(Modifier.fillMaxSize()) {
                   Box(Modifier.weight(1f).fillMaxHeight()) {
                    HomeScreen(
                        vm = vm,
                        onOpenUncategorized = {
                            nav.navigate("expenses?filter=uncategorized") { launchSingleTop = true }
                        },
                        onOpenCategory = ::openCategory,
                        onOpenLimits = { open(LIMITS_ROUTE) },
                        onOpenSettings = { open(SETTINGS_ROUTE) },
                        onOpenRecurring = { open(RECURRING_ROUTE) },
                        onAdd = { adding = true },
                        onOpenMovements = { go(Tab.EXPENSES.route) },
                        onOpenExpense = { id -> open("expenses?edit=$id") },
                        onOpenChallenges = { open("challenges") },
                        onOpenGoals = { nav.navigate("goals") { launchSingleTop = true } },
                        onOpenDebts = { nav.navigate("debts") { launchSingleTop = true } },
                        onOpenTrips = { nav.navigate("trips") { launchSingleTop = true } },
                        onOpenTrip = { id -> nav.navigate("trip/$id") { launchSingleTop = true } },
                        onOpenSearch = { nav.navigate("search") { launchSingleTop = true } },
                        onOpenCategoryMovements = ::openCategoryMovements,
                    )
                   }
                   if (twoPane) {
                       Box(Modifier.weight(1f).fillMaxHeight()) {
                           ExpensesScreen(
                               vm = vm,
                               onOpenMerchant = ::openMerchant,
                               initialFilter = null,
                               onOpenSearch = { open("search") },
                           )
                       }
                   }
                  }
                }
                composable(
                    route = "expenses?filter={filter}&edit={edit}&add={add}&cat={cat}",
                    arguments = listOf(
                        navArgument("filter") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("edit") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument("add") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument("cat") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
                ) { entry ->
                    ExpensesScreen(
                        vm = vm,
                        onOpenMerchant = ::openMerchant,
                        initialFilter = entry.arguments?.getString("filter"),
                        openExpenseId = entry.arguments?.getLong("edit")?.takeIf { it > 0 },
                        openNewExpense = entry.arguments?.getBoolean("add") == true,
                        newExpenseCategory = entry.arguments?.getLong("cat")?.takeIf { it > 0 },
                        onOpenSearch = { nav.navigate("search") { launchSingleTop = true } },
                    )
                }
                composable(Tab.STATS.route) {
                    StatsScreen(vm = vm, onOpenCategory = ::openCategory, onOpenMerchant = ::openMerchant, onOpenMerchants = { open("merchants") })
                }
                composable("category/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    CategoryDetailScreen(
                        vm = vm,
                        categoryId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                        onOpenMerchant = ::openMerchant,
                        onOpenMovements = ::openCategoryMovements,
                    )
                }
                composable("merchant/{key}") { entry ->
                    MerchantDetailScreen(
                        vm = vm,
                        merchantKey = entry.arguments?.getString("key").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenCategory = ::openCategory,
                        onCreateRule = { name -> nav.navigate("rules?pattern=${Uri.encode(name)}") },
                    )
                }
                composable(RECURRING_ROUTE) { RecurringScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable(LIMITS_ROUTE) { LimitsScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable(Tab.MORE.route) {
                    MoreScreen(
                        vm = vm,
                        onOpenRecurring = { open(RECURRING_ROUTE) },
                        onOpenLimits = { open(LIMITS_ROUTE) },
                        onOpenGoals = { open("goals") },
                        onOpenTrips = { open("trips") },
                        onOpenDebts = { open("debts") },
                        onOpenRules = { open("rules") },
                        onOpenImport = { open("import") },
                        onOpenSearch = { open("search") },
                        onOpenMerchants = { open("merchants") },
                        onOpenCategories = { open("categories") },
                        onOpenChallenges = { open("challenges") },
                        onOpenAccounts = { open("accounts") },
                        onOpenSettings = { open(SETTINGS_ROUTE) },
                        onOpenHelp = { open("help") },
                    )
                }
                composable(
                    "rules?pattern={pattern}",
                    arguments = listOf(navArgument("pattern") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entry ->
                    RulesScreen(vm = vm, onBack = { nav.popBackStack() }, initialPattern = entry.arguments?.getString("pattern"))
                }
                composable("search") { SearchScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("merchants") { MerchantsScreen(vm = vm, onBack = { nav.popBackStack() }, onOpenMerchant = ::openMerchant) }
                composable("trips") {
                    TripsScreen(vm = vm, onBack = { nav.popBackStack() }, onOpenTrip = { id -> nav.navigate("trip/$id") })
                }
                composable("trip/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    TripDetailScreen(vm = vm, tripId = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                }
                composable("categories") { CategoriesScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("challenges") { ChallengesScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("accounts") { AccountsScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("debts") { DebtsScreen(onBack = { nav.popBackStack() }) }
                composable("help") { HelpScreen(onBack = { nav.popBackStack() }) }
                composable("import") { ImportScreen(onBack = { nav.popBackStack() }) }
                composable("goals") { GoalsScreen(onBack = { nav.popBackStack() }) }
                composable(SETTINGS_ROUTE) {
                    SettingsScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onOpenHelp = { nav.navigate("help") },
                    )
                }
            }
            }
          }
        }
    }
    if (adding) {
        ExpenseEditorDialog(
            expense = null,
            categories = expenseCategories,
            incomeCategories = incomeCategories,
            merchantSuggestions = merchants,
            trips = trips,
            rateFor = vm.prefs::rateFor,
            onCreateCategory = vm::createCategory,
            defaultDate = defaultDateFor(month),
            onDismiss = { adding = false },
            onSave = { d ->
                vm.addExpense(d)
                adding = false
            },
        )
    }
}

/** Barra de abajo flotante en tinta: cuatro pestañas y el botón lima de añadir en el centro. */
@Composable
private fun FloatingNavBar(currentRoute: String?, onSelect: (Tab) -> Unit, onAdd: () -> Unit) {
    val dark = LocalDarkTheme.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)) {
        // En claro la barra es blanca con una sombra suave; en oscuro, tinta un poco más clara que el fondo.
        Surface(
            shape = CircleShape,
            color = if (dark) Color(0xFF1E1E25) else Color.White,
            shadowElevation = if (dark) 16.dp else 10.dp,
            border = if (dark) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(Tab.HOME, currentRoute == Tab.HOME.route, onSelect)
                NavItem(Tab.EXPENSES, currentRoute == Tab.EXPENSES.route, onSelect)
                AddButton(onAdd)
                NavItem(Tab.STATS, currentRoute == Tab.STATS.route, onSelect)
                NavItem(Tab.MORE, currentRoute == Tab.MORE.route, onSelect)
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: Tab, selected: Boolean, onSelect: (Tab) -> Unit) {
    val haptics = com.midinero.control.ui.screens.rememberHaptics()
    val dark = LocalDarkTheme.current
    val idle = if (dark) Color(0xFF8E8B97) else Color(0xFF8A8792)
    val pill by animateColorAsState(if (selected) Lime else Color.Transparent, tween(250), label = "navPill")
    val iconTint by animateColorAsState(if (selected) Ink else idle, tween(250), label = "navIcon")
    val labelTint by animateColorAsState(if (selected) (if (dark) Color.White else Ink) else idle, tween(250), label = "navLabel")
    val scale by animateFloatAsState(
        if (selected) 1.08f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "navScale",
    )
    val pillWidth by animateDpAsState(if (selected) 48.dp else 32.dp, spring(dampingRatio = 0.6f), label = "navPillWidth")
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(24.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = {
                haptics.tick()
                onSelect(tab)
            })
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(pillWidth).height(32.dp).background(pill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(21.dp).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = labelTint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AddButton(onAdd: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    // En claro, botón de tinta con el + lima; en oscuro, lima con el + de tinta.
    val dark = LocalDarkTheme.current
    val label = tr("Añadir gasto", "Add expense")
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .pressScale(source, 0.9f)
            .size(56.dp)
            .clip(CircleShape)
            .background(if (dark) Lime else Ink)
            .clickable(interactionSource = source, indication = ripple(color = if (dark) Ink else Lime), onClick = onAdd)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = if (dark) Ink else Lime, modifier = Modifier.size(30.dp))
    }
}
