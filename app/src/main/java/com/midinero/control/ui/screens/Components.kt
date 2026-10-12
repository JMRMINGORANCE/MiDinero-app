package com.midinero.control.ui.screens

import androidx.compose.material.icons.filled.Lightbulb
import com.midinero.control.domain.KnownMerchant
import com.midinero.control.domain.MerchantCatalog
import com.midinero.control.ui.theme.LocalDarkTheme
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
import com.midinero.control.domain.tr
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.midinero.control.data.ReceiptStore
import kotlinx.coroutines.launch
import com.midinero.control.domain.endDateExclusive
import com.midinero.control.domain.currentPeriod
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.midinero.control.data.MerchantSuggestion
import com.midinero.control.domain.Categorizer
import com.midinero.control.domain.Currencies
import com.midinero.control.domain.TripStats
import com.midinero.control.domain.TripStatus
import com.midinero.control.data.Trip
import com.midinero.control.data.DEMO_MARKER
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import com.midinero.control.domain.centsToInput
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.label
import com.midinero.control.domain.parseUserAmount
import com.midinero.control.domain.zone
import com.midinero.control.ui.theme.parseHexColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

/** Pista de uso que se muestra hasta que el usuario la cierra. */
@Composable
fun HintCard(vm: com.midinero.control.ui.MainViewModel, id: String, text: String, modifier: Modifier = Modifier) {
    val version by vm.prefs.changes.collectAsStateWithLifecycle()
    val dismissed = remember(version) { id in vm.prefs.dismissedHints }
    if (dismissed) return
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(com.midinero.control.ui.theme.Lime, CircleShape).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = com.midinero.control.ui.theme.Ink, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        TextButton(onClick = { vm.prefs.dismissHint(id) }) { Text(tr("Entendido", "Got it")) }
    }
}

/** Marca un texto como encabezado para los lectores de pantalla (TalkBack salta entre encabezados). */
fun Modifier.asHeading(): Modifier = this.semantics { heading() }

/** Fecha propuesta para un gasto nuevo: hoy, o el último día del mes que se está viendo. */
fun defaultDateFor(month: YearMonth): LocalDate =
    if (month == currentPeriod()) LocalDate.now() else month.endDateExclusive().minusDays(1)

/** Selector de mes centrado (la píldora del diseño). */
@Composable
fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        MonthSwitcher(month, onPrevious, onNext)
    }
}

/** Icono de categoría: su emoji sobre un cuadrado redondeado de su color, suave. */
@Composable
fun CategoryBadge(category: Category?, size: Int = 44) {
    val color = category?.let { parseHexColor(it.colorHex) } ?: MaterialTheme.colorScheme.error
    // Decorativo: el nombre de la categoría siempre aparece al lado, así que el lector de pantalla lo omite.
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape((size * 0.34).dp))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(category?.emoji ?: "❔", fontSize = (size * 0.46).sp)
    }
}

/**
 * Distintivo de un comercio: el tuyo con su foto si lo añadiste, el logo o las siglas de uno conocido y,
 * si no, el de su categoría.
 */
@Composable
fun MerchantBadge(merchant: String, category: Category?, size: Int = 44) {
    val customs by com.midinero.control.data.CustomMerchantsState.list.collectAsState()
    val custom = remember(merchant, customs) { com.midinero.control.domain.CustomMerchants.find(customs, merchant) }
    val known = remember(merchant) { MerchantCatalog.find(merchant) }
    when {
        custom != null && (custom.photo != null || known == null) -> CustomMerchantBadge(custom, category, size)
        known != null -> MerchantLogo(known, size)
        else -> CategoryBadge(category, size)
    }
}

/** Foto de un comercio tuyo; sin foto, sus siglas sobre el color de su categoría. */
@Composable
fun CustomMerchantBadge(merchant: com.midinero.control.data.CustomMerchant, category: Category?, size: Int = 44) {
    val shape = RoundedCornerShape((size * 0.34).dp)
    val image = remember(merchant.photo) {
        merchant.photo?.let { bytes -> runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
    }
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(shape).clearAndSetSemantics { },
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        )
        return
    }
    val color = category?.let { parseHexColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary
    val mark = remember(merchant.name) { com.midinero.control.domain.CustomMerchants.initials(merchant.name) }
    Box(
        modifier = Modifier.size(size.dp).background(color.copy(alpha = 0.22f), shape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(mark, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = (size * if (mark.length == 1) 0.46 else 0.36).sp, maxLines = 1)
    }
}

@Composable
fun MerchantLogo(known: KnownMerchant, size: Int = 44) {
    val context = LocalContext.current
    val logo = remember(known.name) { MerchantLogos.get(context, known.name) }
    if (logo != null) {
        val shape = RoundedCornerShape((size * 0.34).dp)
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                bitmap = logo.image,
                contentDescription = null,
                modifier = if (logo.fullBleed) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding((size * 0.14).dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
        }
        return
    }
    val background = parseHexColor(known.color)
    // Las marcas negras se pierden sobre el fondo oscuro: se les pone un borde fino.
    val outlined = LocalDarkTheme.current && background.luminance() < 0.08f
    val shape = RoundedCornerShape((size * 0.34).dp)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(background, shape)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val scale = when (known.mark.length) { 1 -> 0.46; 2 -> 0.36; else -> 0.27 }
        Text(
            known.mark,
            color = parseHexColor(known.onColor, Color.White),
            fontWeight = FontWeight.Black,
            fontSize = (size * scale).sp,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
    }
}

/** Lo que devuelve el editor al guardar. [amountCents] siempre en euros; si se pagó en otra moneda, el original va aparte. */
data class ExpenseDraft(
    val amountCents: Long,
    val merchant: String,
    val note: String,
    val categoryId: Long?,
    val timestamp: Long,
    val learn: Boolean,
    val isIncome: Boolean,
    val originalCurrency: String? = null,
    val originalAmountCents: Long? = null,
    val rate: Double? = null,
    val tripId: Long? = null,
    val receipt: String? = null,
)

/**
 * Diálogo para crear o editar un gasto.
 * [expense] null = gasto nuevo.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorDialog(
    expense: Expense?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (ExpenseDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
    defaultDate: LocalDate = LocalDate.now(),
    /** Para un gasto nuevo: hora, comercio, nota e importe con que empieza (al apuntar un aviso a mano). */
    initialTime: LocalTime? = null,
    initialMerchant: String = "",
    initialNote: String = "",
    initialAmountCents: Long? = null,
    merchantSuggestions: List<MerchantSuggestion> = emptyList(),
    onDuplicate: (() -> Unit)? = null,
    incomeCategories: List<Category> = emptyList(),
    onOpenHistory: (() -> Unit)? = null,
    initialCategoryId: Long? = null,
    onShare: (() -> Unit)? = null,
    rateFor: (String) -> Double? = { null },
    trips: List<Trip> = emptyList(),
    /** Si se pasa, el selector de categorías ofrece crear una nueva (ver [CategoryPicker]). */
    onCreateCategory: ((Category, (Long?) -> Unit) -> Unit)? = null,
    /** Apunta la devolución de este gasto por el importe (en positivo) que recibe. */
    onRefund: ((Long) -> Unit)? = null,
) {
    // Una devolución se edita en positivo y se guarda en negativo
    val refundMode = expense?.isRefund == true
    var isIncome by rememberSaveable { mutableStateOf(expense?.isIncome ?: false) }
    val shownCategories = if (isIncome) incomeCategories else categories
    var currency by rememberSaveable { mutableStateOf(expense?.originalCurrency ?: Currencies.EUR) }
    var amountText by rememberSaveable {
        mutableStateOf(expense?.let { centsToInput(kotlin.math.abs(it.originalAmountCents ?: it.amountCents)) } ?: initialAmountCents?.let { centsToInput(it) } ?: "")
    }
    var rateText by rememberSaveable {
        mutableStateOf(
            expense?.let { e ->
                val orig = e.originalAmountCents
                if (e.originalCurrency != null && orig != null && orig > 0) rateToInput(e.amountCents.toDouble() / orig) else null
            } ?: ""
        )
    }
    var currencyMenu by remember { mutableStateOf(false) }
    var askingRefund by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val receiptStore = remember { ReceiptStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var receipt by rememberSaveable { mutableStateOf(expense?.receipt) }
    var importingReceipt by remember { mutableStateOf(false) }
    var viewingReceipt by remember { mutableStateOf(false) }
    var receiptError by remember { mutableStateOf(false) }
    fun importReceipt(uri: android.net.Uri) {
        importingReceipt = true
        scope.launch {
            val name = receiptStore.import(uri)
            importingReceipt = false
            receiptError = name == null
            if (name != null) receipt = name
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importReceipt(uri)
    }
    // La ruta de la foto se crea al pulsar «Hacer foto», no al dibujar el editor: si fallara, no rompe el diálogo
    var cameraUri by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) importReceipt(uri)
    }
    // Viajes que se pueden elegir: los que no han terminado y el que ya tuviera el gasto
    val tripChoices = remember(trips, expense) {
        trips.filter { it.id == expense?.tripId || TripStats.status(it, defaultDate) != TripStatus.FINISHED }
    }
    var tripId by rememberSaveable {
        mutableStateOf(
            if (expense != null) expense.tripId
            else trips.firstOrNull { it.autoAssign && TripStats.status(it, defaultDate) == TripStatus.ACTIVE }?.id
        )
    }
    var merchant by rememberSaveable { mutableStateOf(expense?.merchant ?: initialMerchant) }
    var note by rememberSaveable { mutableStateOf(expense?.note ?: initialNote) }
    var categoryId by rememberSaveable { mutableStateOf(expense?.categoryId ?: initialCategoryId) }
    var date by rememberSaveable { mutableStateOf(expense?.let { dateOf(it.timestamp) } ?: defaultDate) }
    var time by rememberSaveable {
        mutableStateOf(
            expense?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalTime() }
                ?: initialTime ?: if (defaultDate == LocalDate.now()) LocalTime.now() else LocalTime.NOON
        )
    }
    var learn by rememberSaveable { mutableStateOf(true) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var merchantFocused by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    val foreign = currency != Currencies.EUR
    val typed = parseUserAmount(amountText)
    val rate = rateText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val amount = if (foreign) typed?.let { t -> rate?.let { Currencies.toEurCents(t, it) } } else typed
    val tooBig = amount != null && amount > MAX_AMOUNT_CENTS
    val canSave = amount != null && amount > 0 && !tooBig && merchant.isNotBlank()
    val categoryChanged = !isIncome && expense != null && categoryId != null && categoryId != expense.categoryId
    val suggestions = remember(merchant, merchantSuggestions) {
        val q = Categorizer.normalize(merchant)
        if (q.isEmpty()) merchantSuggestions.take(5)
        else merchantSuggestions.filter {
            val m = Categorizer.normalize(it.merchant)
            m != q && m.contains(q)
        }.take(5)
    }

    LaunchedEffect(Unit) {
        if (expense == null) runCatching { amountFocus.requestFocus() }
    }

    EditorSheet(
        title = when {
            refundMode -> tr("Editar devolución", "Edit refund")
            expense == null && isIncome -> tr("Nuevo ingreso", "New income")
            expense == null -> tr("Nuevo gasto", "New expense")
            isIncome -> tr("Editar ingreso", "Edit income")
            else -> tr("Editar gasto", "Edit expense")
        },
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = canSave,
        onPrimary = {
            val ts = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            onSave(
                ExpenseDraft(
                    amountCents = if (refundMode) -amount!! else amount!!,
                    merchant = merchant.trim(),
                    note = note.trim(),
                    categoryId = categoryId,
                    timestamp = ts,
                    learn = learn && categoryChanged,
                    isIncome = isIncome,
                    originalCurrency = if (foreign) currency else null,
                    originalAmountCents = if (foreign) typed?.let { if (refundMode) -it else it } else null,
                    rate = if (foreign) rate else null,
                    tripId = if (isIncome) null else tripId,
                    receipt = receipt,
                )
            )
        },
    ) {
        if (refundMode) {
            Pill(tr("Devolución: resta de lo gastado en su categoría y su mes", "Refund: subtracted from its category's and month's spending"), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        } else if (incomeCategories.isNotEmpty() && (expense == null || expense.source == ExpenseSource.MANUAL)) {
            SegmentedTabs(
                listOf(tr("Gasto", "Expense"), tr("Ingreso", "Income")),
                selected = if (isIncome) 1 else 0,
                onSelect = { i ->
                    val income = i == 1
                    if (isIncome != income) {
                        isIncome = income
                        categoryId = null
                    }
                },
                track = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (expense != null && expense.source != ExpenseSource.MANUAL && !refundMode) {
            Pill(
                when (expense.source) {
                    ExpenseSource.GOOGLE_PAY -> tr("Detectado automáticamente en una notificación de pago", "Detected automatically from a payment notification")
                    ExpenseSource.IMPORTED -> tr("Importado de un fichero del banco", "Imported from a bank file")
                    else -> tr("Cargo fijo apuntado automáticamente", "Recurring charge logged automatically")
                },
                MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
        }
        AmountField(
            value = amountText,
            onValueChange = { amountText = it },
            isError = (amountText.isNotEmpty() && typed == null) || tooBig,
            suffix = if (foreign) currency else "€",
            label = tr("Importe", "Amount"),
            focusRequester = amountFocus,
            supportingText = if (tooBig) tr("Importe demasiado alto", "Amount too high") else null,
        )
        Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
            TextButton(
                onClick = { currencyMenu = true },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.semantics { contentDescription = tr("Moneda: $currency. Cambiar moneda", "Currency: $currency. Change currency") },
            ) {
                Text(tr("Moneda: $currency", "Currency: $currency"))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                DropdownMenuItem(
                    text = { Text(tr("EUR · Euro", "EUR · Euro")) },
                    onClick = { currency = Currencies.EUR; rateText = ""; currencyMenu = false },
                )
                Currencies.all.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.code} · ${c.name}") },
                        onClick = {
                            currency = c.code
                            rateText = rateToInput(rateFor(c.code) ?: c.approxEurRate)
                            currencyMenu = false
                        },
                    )
                }
            }
        }
        if (foreign) {
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) },
                label = { Text(tr("Cambio: 1 $currency = … €", "Rate: 1 $currency = … €")) },
                singleLine = true,
                isError = rate == null,
                supportingText = {
                    Text(
                        if (amount != null) tr("Se apuntará como ${formatCents(amount)}. Pon el cambio de tu banco si lo sabes.", "It will be logged as ${formatCents(amount)}. Use your bank's rate if you know it.")
                        else tr("Escribe el importe y el cambio.", "Enter the amount and the rate.")
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
                colors = softFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it.take(80) },
            label = { Text(if (isIncome) tr("Concepto (ej. Nómina)", "Description (e.g. Salary)") else tr("¿Dónde o en qué?", "Where or on what?")) },
            // Si es un comercio conocido, se ve su distintivo al escribirlo
            leadingIcon = MerchantCatalog.find(merchant)?.let { known -> { MerchantLogo(known, size = 28) } },
            shape = MaterialTheme.shapes.medium,
            colors = softFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().onFocusChanged { merchantFocused = it.isFocused },
        )
        if (merchantFocused && suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { sug ->
                    val cat = categories.firstOrNull { it.id == sug.categoryId }
                    SuggestionChip(
                        onClick = {
                            merchant = sug.merchant
                            if (categoryId == null && shownCategories.any { it.id == sug.categoryId }) {
                                categoryId = sug.categoryId
                            }
                        },
                        label = { Text(listOfNotNull(cat?.emoji, sug.merchant).joinToString(" ")) },
                    )
                }
            }
        }
        FieldLabel(tr("Fecha", "Date"))
        DateSelector(date, { date = it }, allowFuture = true)
        FilterChip(
            selected = false,
            onClick = { showTimePicker = true },
            label = { Text(tr("Hora: ", "Time: ") + time.format(hourMinute)) },
            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, Modifier.size(18.dp)) },
            shape = CircleShape,
            colors = sheetChipColors(),
            border = null,
        )
        FieldLabel(tr("Categoría", "Category"))
        if (expense == null && categoryId == null && !isIncome) {
            Text(
                tr("Si no eliges ninguna, MiDinero intentará adivinarla por el comercio.", "If you don't pick one, MiDinero will guess it from the merchant."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        CategoryPicker(shownCategories, categoryId, onCreate = onCreateCategory, isIncome = isIncome) { categoryId = it }
        if (categoryChanged && merchant.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = learn, onCheckedChange = { learn = it })
                Text(
                    tr("Usar siempre esta categoría para «${merchant.trim()}»", "Always use this category for “${merchant.trim()}”"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (tripChoices.isNotEmpty() && !isIncome) {
            FieldLabel(tr("Viaje o evento", "Trip or event"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tripChoices.forEach { t ->
                    FilterChip(
                        selected = tripId == t.id,
                        onClick = { tripId = if (tripId == t.id) null else t.id },
                        label = { Text("${t.emoji} ${t.name}") },
                        shape = CircleShape,
                        colors = sheetChipColors(),
                        border = null,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(300) },
            label = { Text(tr("Nota (opcional)", "Note (optional)")) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
            colors = softFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        val receiptFile = receipt?.let { receiptStore.file(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (receiptFile != null) {
                ReceiptThumbnail(receiptFile, onClick = { viewingReceipt = true })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Ticket adjunto", "Receipt attached"), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { viewingReceipt = true }) { Text(tr("Ver", "View")) }
                        TextButton(onClick = { receipt = null }) { Text(tr("Quitar", "Remove")) }
                    }
                }
            } else {
                Text(tr("Ticket", "Receipt"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (importingReceipt) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        receiptError = runCatching {
                            val uri = receiptStore.cameraTarget()
                            cameraUri = uri
                            runCatching { cameraLauncher.launch(uri) }.onFailure { android.widget.Toast.makeText(context, tr("Este móvil no deja abrir la cámara desde aquí", "This phone can't open the camera from here"), android.widget.Toast.LENGTH_LONG).show() }
                        }.isFailure
                    }) { Text(tr("Hacer foto", "Take photo")) }
                    TextButton(onClick = {
                        runCatching { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { android.widget.Toast.makeText(context, tr("Este móvil no deja abrir archivos desde aquí", "This phone can't open files from here"), android.widget.Toast.LENGTH_LONG).show() }
                    }) { Text(tr("Galería", "Gallery")) }
                }
            }
        }
        if (receiptError) {
            Text(tr("No se ha podido guardar la foto. Prueba con otra.", "Couldn't save the photo. Try another one."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (expense != null && expense.rawText.isNotBlank() && expense.rawText != DEMO_MARKER) {
            Spacer(Modifier.height(8.dp))
            Text(
                (if (expense.source == ExpenseSource.IMPORTED) tr("Concepto del banco: ", "Bank description: ") else tr("Notificación original: ", "Original notification: ")) + expense.rawText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null || onDuplicate != null) {
            Spacer(Modifier.height(8.dp))
            FlowRow {
                if (onOpenHistory != null && !isIncome) {
                    TextButton(onClick = onOpenHistory) { Text(tr("Historial", "History")) }
                }
                if (onShare != null && !isIncome) {
                    TextButton(onClick = onShare) { Text(tr("Compartir", "Share")) }
                }
                if (onDuplicate != null) {
                    TextButton(onClick = onDuplicate) { Text(tr("Duplicar", "Duplicate")) }
                }
                if (onRefund != null && expense != null && !expense.isIncome && expense.amountCents > 0) {
                    TextButton(onClick = { askingRefund = true }) { Text(tr("Devolución", "Refund")) }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (askingRefund && onRefund != null && expense != null) {
        RefundDialog(
            maxCents = expense.amountCents,
            onDismiss = { askingRefund = false },
            onConfirm = { askingRefund = false; onRefund(it) },
        )
    }

    if (viewingReceipt) {
        receipt?.let { receiptStore.file(it) }?.let { f ->
            ReceiptViewer(
                file = f,
                onDismiss = { viewingReceipt = false },
                onShare = {
                    receiptStore.shareIntent(f.name)?.let { intent ->
                        runCatching { context.startActivity(android.content.Intent.createChooser(intent, tr("Compartir ticket", "Share receipt"))) }
                    }
                },
            )
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPicked = { date = it; showDatePicker = false },
        )
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(tr("Hora del gasto", "Time of the expense")) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTimePicker = false
                }) { Text(tr("Aceptar", "OK")) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

/** Cuánto te devuelven de una compra: por defecto todo, como mucho [maxCents]. */
@Composable
private fun RefundDialog(maxCents: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(centsToInput(maxCents)) }
    val cents = parseUserAmount(text)
    val valid = cents != null && cents in 1..maxCents
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Apuntar una devolución", "Log a refund")) },
        text = {
            Column {
                Text(
                    tr("Se apunta hoy, en la misma categoría, y resta de lo gastado. Si te devuelven solo una parte, cambia el importe.", "It's logged today, in the same category, and subtracted from your spending. If only part is refunded, change the amount."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) },
                    label = { Text(tr("Importe devuelto (€)", "Amount refunded (€)")) },
                    singleLine = true,
                    isError = text.isNotBlank() && !valid,
                    supportingText = if (cents != null && cents > maxCents) { { Text(tr("Como mucho ${formatCents(maxCents)}", "At most ${formatCents(maxCents)}")) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(cents!!) }, enabled = valid) { Text(tr("Apuntar", "Log")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

/** Importe máximo razonable para un gasto (1 millón de euros). */
const val MAX_AMOUNT_CENTS = 100_000_000L

private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")

/** Selector de fecha. Trabaja en UTC internamente, como pide el DatePicker de Material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                if (picked != null) onPicked(picked) else onDismiss()
            }) { Text(tr("Aceptar", "OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    ) {
        DatePicker(state = state)
    }
}

/** Iconos habituales para elegir con un toque (también se puede escribir cualquier emoji). */
private val quickEmojis = listOf(
    "🛒", "🍽️", "☕", "🛵", "⛽", "🚗", "🚇", "✈️", "🏠", "💡", "📱", "📺",
    "🎉", "🏋️", "💊", "🐶", "👶", "🎓", "🎁", "👕", "💄", "🔧", "💶", "📦",
)

/** Colores que se ofrecen para categorías nuevas. */
val categoryPalette = listOf(
    "#E8590C", "#D6336C", "#2F9E44", "#1971C2", "#7048E8", "#0C8599", "#C2255C", "#5F3DC4",
    "#099268", "#E67700", "#495057", "#AE3EC9", "#74B816", "#F59F00", "#3BC9DB",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    /** Recibe la categoría a la que pasan sus movimientos, o null para dejarlos sin categoría. */
    onDelete: ((Long?) -> Unit)?,
    suggestionCents: Long? = null,
    /** Categoría de ingresos: sin límite mensual. */
    isIncome: Boolean = category?.isIncome ?: false,
    /** Categorías a las que se pueden pasar los movimientos al borrarla. */
    moveTargets: List<Category> = emptyList(),
) {
    var name by rememberSaveable { mutableStateOf(category?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(category?.emoji ?: "💶") }
    var color by rememberSaveable { mutableStateOf(category?.colorHex ?: categoryPalette.first()) }
    var limitText by rememberSaveable { mutableStateOf(category?.monthlyLimitCents?.let { centsToInput(it) } ?: "") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(category?.hidden ?: false) }
    var pickingEmoji by rememberSaveable { mutableStateOf(false) }
    var moveTo by rememberSaveable { mutableStateOf<Long?>(null) }
    val limit = parseUserAmount(limitText)
    val limitValid = limitText.isBlank() || limit != null

    EditorSheet(
        title = when {
                    category == null && isIncome -> tr("Nueva categoría de ingresos", "New income category")
                    category == null -> tr("Nueva categoría", "New category")
                    else -> tr("Editar categoría", "Edit category")
                },
        onDismiss = onDismiss,
        primaryText = tr("Guardar", "Save"),
        primaryEnabled = name.isNotBlank() && limitValid,
        onPrimary = {
            val base = category ?: Category(name = "", emoji = "", colorHex = color, isIncome = isIncome)
            onSave(
                base.copy(
                    name = name.trim(),
                    emoji = emoji.trim().ifEmpty { "💶" },
                    colorHex = color,
                    monthlyLimitCents = if (isIncome || limitText.isBlank()) null else limit,
                    hidden = hidden,
                )
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = emoji,
                // Cualquier emoji del teclado (los compuestos, como 👨‍👩‍👧, ocupan varios caracteres)
                onValueChange = { if (it.length <= 16) emoji = it },
                label = { Text(tr("Icono", "Icon")) },
                singleLine = true,
                modifier = Modifier.width(90.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("Nombre", "Name")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow {
            quickEmojis.forEach { e ->
                TextButton(onClick = { emoji = e }, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(e, fontSize = 20.sp)
                }
            }
        }
        TextButton(onClick = { pickingEmoji = true }) {
            Text(tr("Ver todos los iconos…", "See all icons…"))
        }
        Text(
            tr("O toca «Icono» y escribe cualquier emoji con el teclado.", "Or tap “Icon” and type any emoji with the keyboard."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pickingEmoji) EmojiPickerSheet(onDismiss = { pickingEmoji = false }, onPick = { emoji = it; pickingEmoji = false })
        if (!isIncome) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                label = { Text(tr("Límite mensual (€)", "Monthly limit (€)")) },
                supportingText = { Text(tr("Déjalo vacío si no quieres límite", "Leave empty for no limit")) },
                isError = !limitValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (suggestionCents != null) {
                SuggestionChip(
                    onClick = { limitText = centsToInput(suggestionCents) },
                    label = { Text(tr("Sugerido: ${formatCents(suggestionCents)} (lo que sueles gastar)", "Suggested: ${formatCents(suggestionCents)} (what you usually spend)")) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Color", "Colour"), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            categoryPalette.forEachIndexed { i, hex ->
                val selected = hex == color
                // Zona táctil de 48 dp aunque el círculo sea más pequeño.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { color = hex }
                        .semantics {
                            contentDescription = tr("Color ${i + 1}", "Colour ${i + 1}") + if (selected) tr(", elegido", ", selected") else ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(if (selected) 36.dp else 30.dp).background(parseHexColor(hex), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text("✓", color = Color.White)
                    }
                }
            }
        }
        if (category != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Ocultar", "Hide"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        tr("No se ofrece al apuntar ni para clasificar sola; su historial se conserva.", "It isn't offered when logging or used for automatic sorting; its history is kept."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(checked = hidden, onCheckedChange = { hidden = it })
            }
        }
        if (onDelete != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDelete = true }) {
                Text(tr("Eliminar categoría", "Delete category"), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Eliminar «${category?.name}»?", "Delete “${category?.name}”?")) },
            text = {
                val targets = moveTargets.filter { it.id != category?.id && it.isIncome == isIncome }
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        when {
                            targets.isEmpty() && isIncome -> tr("Los ingresos de esta categoría quedarán sin categoría.", "Income in this category will be left uncategorised.")
                            targets.isEmpty() -> tr("Los gastos de esta categoría quedarán sin categoría.", "Expenses in this category will be left uncategorised.")
                            else -> tr(
                                "¿A qué categoría pasan sus movimientos, cargos fijos y reglas? Si no eliges ninguna, los movimientos quedarán sin categoría.",
                                "Which category should its transactions, recurring charges and rules move to? If you don't pick one, the transactions will be left uncategorised.",
                            )
                        },
                    )
                    if (targets.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        CategoryPicker(targets, moveTo) { moveTo = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(moveTo) }) {
                    Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

/** "0.92" → "0,92": el cambio con coma decimal y sin ceros sobrantes. */
private fun rateToInput(rate: Double): String =
    java.math.BigDecimal(rate).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

/**
 * Botón flotante con texto. Declara su nombre y su acción de forma explícita para que
 * TalkBack lo lea bien («Añadir, botón») y se pueda pulsar desde la accesibilidad.
 */
@Composable
fun AppFab(
    text: String,
    action: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Add,
) {
    ExtendedFloatingActionButton(
        onClick = action,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text) },
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = text
            role = androidx.compose.ui.semantics.Role.Button
            onClick(label = text) { action(); true }
        },
    )
}
