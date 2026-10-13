package com.midinero.control.ui

import androidx.compose.ui.graphics.toArgb
import com.midinero.control.domain.monthOf
import com.midinero.control.domain.Rollover
import com.midinero.control.domain.tr
import kotlinx.coroutines.flow.first
import com.midinero.control.domain.label
import com.midinero.control.domain.rangeLabel
import com.midinero.control.domain.MonthReport
import com.midinero.control.domain.SearchQuery
import com.midinero.control.ui.screens.ExpenseDraft
import com.midinero.control.domain.currentPeriod
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midinero.control.MiDineroApp
import com.midinero.control.data.Category
import com.midinero.control.data.CategoryTotal
import com.midinero.control.data.CustomRule
import com.midinero.control.data.Trip
import com.midinero.control.data.DefaultCategories
import com.midinero.control.data.Expense
import com.midinero.control.data.ExpenseSource
import com.midinero.control.data.MerchantRule
import com.midinero.control.data.MerchantSuggestion
import com.midinero.control.data.RecurringExpense
import com.midinero.control.data.RecurringHistory
import com.midinero.control.domain.Budgets
import com.midinero.control.domain.CategoryInsight
import com.midinero.control.domain.SubscriptionCandidate
import com.midinero.control.domain.SubscriptionDetector
import com.midinero.control.domain.Tip
import com.midinero.control.domain.Tips
import com.midinero.control.domain.SpendingCalendar
import com.midinero.control.domain.YearSummary
import com.midinero.control.domain.QuickEntry
import com.midinero.control.domain.QuickEntryParser
import com.midinero.control.domain.zone
import com.midinero.control.domain.Insights
import com.midinero.control.domain.MerchantInsight
import com.midinero.control.domain.MonthStats
import com.midinero.control.domain.Period
import com.midinero.control.domain.Stats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as MiDineroApp).repository
    val prefs = (app as MiDineroApp).prefs

    private val _month = MutableStateFlow(currentPeriod())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val categories: StateFlow<List<Category>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categorías de gasto (sin las de ingresos), para selectores y límites. */
    val expenseCategories: StateFlow<List<Category>> = repo.categories.map { list -> list.filter { !it.isIncome } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeCategories: StateFlow<List<Category>> = repo.categories.map { list -> list.filter { it.isIncome } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenses: StateFlow<List<Expense>> = _month
        .flatMapLatest { repo.expensesIn(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totals: StateFlow<List<CategoryTotal>> = _month
        .flatMapLatest { repo.totalsIn(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estadísticas del mes visto, con los 6 meses anteriores para la tendencia. */
    val stats: StateFlow<MonthStats?> = _month
        .flatMapLatest { m ->
            combine(repo.expensesBetween(m.minusMonths(6), m), repo.recurring) { list, charges ->
                Stats.compute(m, list, recurringCharges = charges)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Gastos de los 6 meses anteriores al visto, para sugerir límites y ver cuántos se cumplieron. */
    private val history: StateFlow<List<Expense>> = _month
        .flatMapLatest { m -> repo.expensesBetween(m.minusMonths(6), m.minusMonths(1)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Lo que se suma a cada límite si está activado arrastrar lo que sobra: la clave null es el presupuesto total.
     */
    val rollover: StateFlow<Map<Long?, Long>> = combine(_month, history, repo.categories, prefs.changes) { m, hist, cats, _ ->
        if (!prefs.rolloverEnabled) return@combine emptyMap()
        val prev = m.minusMonths(1)
        val prevSpending = hist.filter { !it.isIncome && monthOf(it.timestamp) == prev }
        val active = prevSpending.isNotEmpty()
        val byCategory = prevSpending.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
        buildMap {
            cats.filter { !it.isIncome && (it.monthlyLimitCents ?: 0L) > 0 }.forEach { c ->
                put(c.id, Rollover.bonus(c.monthlyLimitCents!!, byCategory[c.id] ?: 0L, active))
            }
            val budget = prefs.monthlyBudgetCents
            if (budget > 0) put(null, Rollover.bonus(budget, prevSpending.sumOf { it.amountCents }, active))
        }.filterValues { it > 0 }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Límite sugerido por categoría (media de 3 meses, redondeada). */
    val limitSuggestions: StateFlow<Map<Long, Long>> = combine(_month, history) { m, list ->
        Budgets.monthlyAverageByCategory(list, m).filterValues { it > 0 }.mapValues { Budgets.roundSuggestion(it.value) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Meses cumplidos / meses con datos, por categoría con límite. */
    val compliance: StateFlow<Map<Long, Pair<Int, Int>>> = combine(_month, history, repo.categories) { m, list, cats ->
        val limits = cats.mapNotNull { c -> c.monthlyLimitCents?.takeIf { it > 0 }?.let { c.id to it } }.toMap()
        Budgets.compliance(list, limits, m)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _year = MutableStateFlow(currentPeriod().year)
    val year: StateFlow<Int> = _year.asStateFlow()

    val yearSummary: StateFlow<YearSummary?> = _year
        .flatMapLatest { y -> repo.expensesBetween(YearMonth.of(y - 1, 1), YearMonth.of(y, 12)).map { SpendingCalendar.year(y, it) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousYear() { _year.value -= 1 }
    fun nextYear() { if (_year.value < currentPeriod().year) _year.value += 1 }

    // ---------- Sugerencias ----------

    /** Pagos que parecen suscripciones y aún no están en Cargos fijos. */
    val subscriptionCandidates: StateFlow<List<SubscriptionCandidate>> =
        // Basta con el último año: una suscripción viva tiene pagos recientes.
        combine(repo.expensesBetween(currentPeriod().minusMonths(12), currentPeriod()), repo.recurring, prefs.changes) { expenses, recurring, _ ->
            val ignored = prefs.ignoredSubscriptions
            SubscriptionDetector.detect(expenses, recurring).filter { it.merchantKey !in ignored }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSubscription(c: SubscriptionCandidate) {
        saveRecurring(
            RecurringExpense(
                name = c.merchant,
                amountCents = c.amountCents,
                categoryId = c.categoryId,
                dayOfMonth = c.dayOfMonth,
                nextDueEpochDay = c.nextDate.toEpochDay(),
            )
        )
    }

    fun ignoreSubscription(c: SubscriptionCandidate) {
        prefs.ignoredSubscriptions = prefs.ignoredSubscriptions + c.merchantKey
    }

    /** Consejos del mes visto. */
    val tips: StateFlow<List<Tip>> = _month
        .flatMapLatest { m ->
            combine(repo.expensesBetween(m.minusMonths(3), m), repo.categories, repo.recurring) { list, cats, rec ->
                Tips.generate(m, list, cats.associateBy { it.id }, rec)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- Búsqueda en todo el historial ----------

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    fun setSearch(text: String) {
        _search.value = text.take(60)
    }

    /** Movimientos de cualquier mes que encajan con la búsqueda (hasta 300, los más recientes). */
    val searchResults: StateFlow<List<Expense>> =
        combine(repo.allExpenses, repo.categories, _search) { all, cats, text ->
            val q = SearchQuery.parse(text)
            if (q.isEmpty) emptyList()
            else {
                val byId = cats.associateBy { it.id }
                all.asSequence().filter { q.matches(it, it.categoryId?.let(byId::get)) }.take(300).toList()
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rememberSearch(text: String) {
        val t = text.trim()
        if (t.length >= 2) prefs.recentSearches = (listOf(t) + prefs.recentSearches.filter { !it.equals(t, true) }).take(6)
    }

    /** Texto con el resumen del periodo que se está viendo, para compartir. */
    suspend fun monthReport(): String = withContext(Dispatchers.Default) {
        val m = _month.value
        val list = repo.expensesIn(m).first()
        val previous = repo.expensesIn(m.minusMonths(1)).first()
        val cats = repo.categories.first().associateBy { it.id }
        val spent = list.filter { !it.isIncome }
        val top = spent.groupBy { it.categoryId }
            .map { (id, l) -> (id?.let { cats[it] }?.let { "${it.emoji} ${it.name}" } ?: tr("Sin categoría", "Uncategorised")) to l.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
        MonthReport.text(
            periodLabel = m.rangeLabel() ?: m.label().lowercase(),
            spentCents = spent.sumOf { it.amountCents },
            incomeCents = list.filter { it.isIncome }.sumOf { it.amountCents },
            previousSpentCents = previous.filter { !it.isIncome }.sumOf { it.amountCents },
            topCategories = top,
            budgetCents = prefs.monthlyBudgetCents.takeIf { it > 0 },
        )
    }

    // ---------- Viajes y eventos ----------

    val trips: StateFlow<List<Trip>> = repo.trips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tripTotals: StateFlow<Map<Long, Long>> = repo.tripTotals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun tripExpenses(tripId: Long): Flow<List<Expense>> = repo.tripExpenses(tripId)

    /** Guarda un viaje; [onDone] recibe cuántos gastos ya apuntados se han recogido en él. */
    fun saveTrip(trip: Trip, collectExisting: Boolean, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.saveTrip(trip, collectExisting).second) }
    }

    fun deleteTrip(trip: Trip) = viewModelScope.launch { repo.deleteTrip(trip) }

    // ---------- Reglas personalizadas ----------

    val customRules: StateFlow<List<CustomRule>> =
        repo.customRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveCustomRule(rule: CustomRule, applyToHistory: Boolean, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            repo.saveCustomRule(rule)
            onDone(if (applyToHistory) repo.applyRuleToHistory(rule) else 0)
        }
    }

    fun deleteCustomRule(rule: CustomRule) = viewModelScope.launch { repo.deleteCustomRule(rule) }

    // ---------- Tus comercios ----------

    val customMerchants: StateFlow<List<com.midinero.control.data.CustomMerchant>> =
        repo.customMerchants.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [onResult] recibe false si ya hay otro comercio tuyo con ese nombre. */
    fun saveCustomMerchant(merchant: com.midinero.control.data.CustomMerchant, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(repo.saveCustomMerchant(merchant)) }
    }

    fun deleteCustomMerchant(merchant: com.midinero.control.data.CustomMerchant) =
        viewModelScope.launch { repo.deleteCustomMerchant(merchant) }

    fun categoryInsight(categoryId: Long): Flow<CategoryInsight> = _month
        .flatMapLatest { m -> repo.expensesBetween(m.minusMonths(5), m).map { Insights.category(categoryId, m, it) } }
        .flowOn(Dispatchers.Default)

    /** Ranking de comercios de los últimos [days] días (0 = todo el historial). */
    fun merchantRanking(days: Int): Flow<List<com.midinero.control.domain.MerchantStat>> = repo.allExpenses
        .map { all ->
            val today = java.time.LocalDate.now()
            val from = if (days <= 0) java.time.LocalDate.of(1970, 1, 1) else today.minusDays(days.toLong() - 1)
            com.midinero.control.domain.MerchantRanking.rank(all, from, today)
        }
        .flowOn(Dispatchers.Default)

    /** Gasto por etiqueta (#cumpleaños…) en todo el historial. */
    val tagTotals: Flow<List<com.midinero.control.domain.Tags.TagTotal>> = repo.allExpenses
        .map { com.midinero.control.domain.Tags.totals(it) }
        .flowOn(Dispatchers.Default)

    /** Movimientos con la etiqueta [key], del más reciente al más antiguo. */
    fun taggedExpenses(key: String): Flow<List<Expense>> = repo.allExpenses
        .map { all -> all.filter { key in com.midinero.control.domain.Tags.of(it.note) }.sortedByDescending { it.timestamp } }
        .flowOn(Dispatchers.Default)

    fun merchantInsight(merchantKey: String): Flow<MerchantInsight> = repo.allExpenses
        .map { Insights.merchant(merchantKey, it) }
        .flowOn(Dispatchers.Default)

    fun recategorizeMerchant(merchantKey: String, categoryId: Long) {
        viewModelScope.launch { repo.recategorizeMerchant(merchantKey, categoryId) }
    }

    val learnedRules: StateFlow<Int> =
        repo.learnedRulesCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recurring: StateFlow<List<RecurringExpense>> =
        repo.recurring.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveRecurring(recurring: RecurringExpense) {
        viewModelScope.launch {
            val withCategory = if (recurring.categoryId == null && !recurring.isIncome) {
                recurring.copy(categoryId = repo.categorize(recurring.name))
            } else recurring
            repo.saveRecurring(withCategory)
        }
    }

    fun deleteRecurring(recurring: RecurringExpense) {
        viewModelScope.launch { repo.deleteRecurring(recurring) }
    }

    fun skipNextCharge(recurring: RecurringExpense) {
        viewModelScope.launch { repo.skipNextCharge(recurring) }
    }

    fun chargeNow(recurring: RecurringExpense) {
        viewModelScope.launch { repo.chargeNow(recurring) }
    }

    suspend fun recurringHistory(id: Long): RecurringHistory = repo.recurringHistory(id)

    fun setRecurringActive(recurring: RecurringExpense, active: Boolean) {
        viewModelScope.launch { repo.setRecurringActive(recurring, active) }
    }

    val merchants: StateFlow<List<MerchantSuggestion>> =
        repo.merchants.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMonth(month: YearMonth) {
        if (month <= currentPeriod()) _month.value = month
    }

    /** Cambia el día de inicio del mes y vuelve al periodo actual. */
    fun setPeriodStartDay(day: Int) {
        prefs.periodStartDay = day
        Period.startDay = day
        _month.value = currentPeriod()
    }

    fun goToCurrentMonth() { _month.value = currentPeriod() }

    fun previousMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() {
        if (_month.value < currentPeriod()) _month.value = _month.value.plusMonths(1)
    }

    /**
     * Añade un gasto (o ingreso) manual. Si no se elige categoría, un gasto se intenta categorizar
     * automáticamente y un ingreso va a "Otros ingresos".
     */
    /** Guarda lo que devuelve el editor; si es en otra moneda recuerda el cambio para la próxima vez. */
    fun addExpense(d: ExpenseDraft) {
        d.rate?.let { r -> d.originalCurrency?.let { prefs.setRate(it, r) } }
        addExpense(
            d.amountCents, d.merchant, d.note, d.categoryId, d.timestamp, d.isIncome, d.originalCurrency, d.originalAmountCents,
            tripId = d.tripId, autoTrip = false, receipt = d.receipt,
        )
    }

    fun addExpense(
        amountCents: Long,
        merchant: String,
        note: String,
        categoryId: Long?,
        timestamp: Long,
        isIncome: Boolean = false,
        originalCurrency: String? = null,
        originalAmountCents: Long? = null,
        tripId: Long? = null,
        autoTrip: Boolean = true,
        receipt: String? = null,
    ) {
        viewModelScope.launch {
            val trip = tripId ?: if (autoTrip && !isIncome) repo.autoTripFor(timestamp) else null
            val finalCategory = categoryId ?: if (isIncome) {
                repo.categoryIdByName(DefaultCategories.OTROS_INGRESOS)
            } else repo.categorize(merchant)
            repo.addExpense(
                Expense(
                    amountCents = amountCents,
                    merchant = merchant.trim(),
                    note = note.trim(),
                    categoryId = finalCategory,
                    timestamp = timestamp,
                    source = ExpenseSource.MANUAL,
                    isIncome = isIncome,
                    originalCurrency = originalCurrency,
                    originalAmountCents = originalAmountCents,
                    tripId = trip,
                    receipt = receipt,
                )
            )
        }
    }

    /** Apunta una frase rápida ("12,50 café ayer"). Devuelve lo entendido o null si no hay importe. */
    fun quickAdd(text: String): QuickEntry? {
        val entry = QuickEntryParser.parse(text) ?: return null
        val time = if (entry.date == LocalDate.now()) java.time.LocalTime.now() else java.time.LocalTime.NOON
        addExpense(
            entry.amountCents, entry.merchant, "", null,
            entry.date.atTime(time).atZone(zone).toInstant().toEpochMilli(), entry.isIncome,
        )
        return entry
    }

    fun updateExpense(old: Expense, new: Expense, learnMerchant: Boolean) {
        viewModelScope.launch { repo.updateExpense(old, new, learnMerchant) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repo.deleteExpense(expense) }
    }

    fun restoreExpense(expense: Expense) {
        viewModelScope.launch { repo.restoreExpense(expense) }
    }

    /** Datos para la imagen del resumen del mes visto. */
    fun monthReportData(): MonthReportImage? {
        val m = _month.value
        val s = stats.value?.takeIf { it.month == m } ?: return null
        val cats = categories.value.associateBy { it.id }
        return MonthReportImage(
            monthLabel = m.label(),
            totalCents = s.totalCents,
            previousTotalCents = s.previousTotalCents,
            previousMonthName = m.minusMonths(1).format(com.midinero.control.domain.Lang.dateFormat("LLLL")),
            incomeCents = s.incomeCents,
            dailyAverageCents = s.dailyAverageCents,
            cumulative = com.midinero.control.domain.DailyCurve.cumulative(m, expenses.value),
            categories = s.byCategory.take(5).map { (id, cents) ->
                val c = id?.let { cats[it] }
                Triple("${c?.emoji ?: "❔"} ${c?.name ?: tr("Sin categoría", "Uncategorised")}", c?.let { com.midinero.control.ui.theme.parseHexColor(it.colorHex).toArgb() } ?: 0xFFE5484D.toInt(), cents)
            },
        )
    }

    /** Datos de la imagen del resumen del año que se está viendo en Análisis. */
    fun yearReportData(): YearReportImage? {
        val s = yearSummary.value?.takeIf { it.count > 0 } ?: return null
        val cats = categories.value.associateBy { it.id }
        return YearReportImage(
            year = s.year,
            totalCents = s.totalCents,
            previousCents = s.previousCents,
            partial = s.year == java.time.LocalDate.now().year,
            incomeCents = s.incomeCents,
            monthlyAverageCents = s.monthlyAverageCents,
            byMonth = s.byMonth.map { it.second },
            maxMonthName = s.maxMonth?.format(com.midinero.control.domain.Lang.dateFormat("LLLL")).orEmpty(),
            topMerchant = s.topMerchants.firstOrNull(),
            categories = s.byCategory.take(3).map { (id, cents) ->
                val c = id?.let { cats[it] }
                Triple("${c?.emoji ?: "❔"} ${c?.name ?: tr("Sin categoría", "Uncategorised")}", c?.let { com.midinero.control.ui.theme.parseHexColor(it.colorHex).toArgb() } ?: 0xFFE5484D.toInt(), cents)
            },
        )
    }

    /** Selección múltiple: borra varios movimientos de una vez. */
    fun deleteExpenses(list: List<Expense>) {
        viewModelScope.launch { list.forEach { repo.deleteExpense(it) } }
    }

    fun restoreExpenses(list: List<Expense>) {
        viewModelScope.launch { list.forEach { repo.restoreExpense(it) } }
    }

    /** Selección múltiple: pone la misma categoría a varios movimientos. */
    fun setCategory(ids: List<Long>, categoryId: Long) {
        viewModelScope.launch { repo.setCategory(ids, categoryId) }
    }

    /** Marca movimientos como «no son duplicados» para no volver a avisar de ellos. */
    fun markNotDuplicates(ids: Collection<Long>) {
        prefs.notDuplicates = prefs.notDuplicates + ids
    }

    /** Copia un gasto con fecha de ahora (útil para gastos que se repiten a mano). */
    fun addRefund(original: Expense, cents: Long) {
        viewModelScope.launch { repo.addRefund(original, cents) }
    }

    fun duplicateExpense(expense: Expense) {
        viewModelScope.launch {
            repo.addExpense(
                expense.copy(id = 0, timestamp = System.currentTimeMillis(), source = ExpenseSource.MANUAL, rawText = "", receipt = null)
            )
        }
    }

    fun setLimit(category: Category, limitCents: Long?) {
        viewModelScope.launch { repo.setLimit(category, limitCents) }
    }

    /** [onResult] recibe false si el nombre ya lo usa otra categoría. */
    fun saveCategory(category: Category, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(repo.saveCategory(category)) }
    }

    /** Crea una categoría desde un editor. [onResult] recibe su id, o null si el nombre ya existe. */
    fun createCategory(category: Category, onResult: (Long?) -> Unit) {
        viewModelScope.launch { onResult(repo.createCategory(category)) }
    }

    /** Borra [category]; con [moveTo], lo que la usaba pasa a esa otra categoría. */
    fun deleteCategory(category: Category, moveTo: Long? = null, snackbar: androidx.compose.material3.SnackbarHostState? = null) {
        // En el ámbito del ViewModel: el aviso con «Deshacer» sigue aunque se cierre la pantalla de la categoría
        viewModelScope.launch {
            val deleted = repo.deleteCategory(category, moveTo)
            snackbar?.showUndo(tr("Categoría «${category.name}» eliminada", "Category “${category.name}” deleted")) {
                viewModelScope.launch { repo.restoreCategory(deleted) }
            }
        }
    }

    fun reorderCategories(ids: List<Long>) {
        viewModelScope.launch { repo.reorderCategories(ids) }
    }

    fun forgetLearnedRules() {
        viewModelScope.launch { repo.forgetLearnedRules() }
    }

    // ---------- Tus datos ----------

    val rules: StateFlow<List<MerchantRule>> =
        repo.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteRule(rule: MerchantRule) {
        viewModelScope.launch { repo.deleteRule(rule) }
    }

    fun loadDemo(onDone: () -> Unit = {}) {
        viewModelScope.launch { repo.loadDemo(); goToCurrentMonth(); onDone() }
    }

    fun clearDemo(onDone: () -> Unit = {}) {
        viewModelScope.launch { repo.clearDemo(); onDone() }
    }


}

