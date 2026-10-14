package com.midinero.control.data

import com.midinero.control.domain.Lang
import com.midinero.control.domain.PaymentParser
import com.midinero.control.domain.tr
import com.midinero.control.domain.CategoryNames
import com.midinero.control.domain.currentPeriod
import android.content.Context
import androidx.core.content.edit
import com.midinero.control.domain.Categorizer
import com.midinero.control.domain.MerchantCatalog
import com.midinero.control.domain.ImportedRow
import com.midinero.control.domain.Currencies
import com.midinero.control.domain.Period
import com.midinero.control.domain.Split
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.RuleMatcher
import com.midinero.control.domain.ParsedPayment
import com.midinero.control.domain.Recurrence
import com.midinero.control.domain.zone
import com.midinero.control.domain.endMillis
import com.midinero.control.domain.monthOf
import com.midinero.control.domain.startMillis
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.shortLabel
import com.midinero.control.service.AppNotifications
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.midinero.control.domain.CustomMerchants
import com.midinero.control.domain.TripStats
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth

/** Marca de los gastos de ejemplo (en rawText) para poder borrarlos sin tocar los reales. */
const val DEMO_MARKER = "__demo__"

class ExpenseRepository(
    private val db: AppDatabase,
    private val context: Context,
    private val prefs: AppPrefs = AppPrefs(context),
) {
    private val categoryDao = db.categoryDao()
    private val expenseDao = db.expenseDao()
    private val ruleDao = db.merchantRuleDao()
    private val recurringDao = db.recurringDao()
    private val goalDao = db.goalDao()
    private val customRuleDao = db.ruleDao()
    private val debtDao = db.debtDao()
    private val tripDao = db.tripDao()
    private val customMerchantDao = db.customMerchantDao()
    private val accountDao = db.accountDao()
    val receipts = ReceiptStore(context)
    private val detectionLock = Mutex()
    private val recurringLock = Mutex()

    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val learnedRulesCount: Flow<Int> = ruleDao.observeCount()
    val recurring: Flow<List<RecurringExpense>> = recurringDao.observeAll()
    val merchants: Flow<List<MerchantSuggestion>> = expenseDao.observeMerchants()

    fun expensesIn(month: YearMonth): Flow<List<Expense>> =
        expenseDao.observeBetween(month.startMillis(), month.endMillis())

    fun expensesBetween(from: YearMonth, to: YearMonth): Flow<List<Expense>> =
        expenseDao.observeBetween(from.startMillis(), to.endMillis())

    fun totalsIn(month: YearMonth): Flow<List<CategoryTotal>> =
        expenseDao.observeTotalsBetween(month.startMillis(), month.endMillis())

    suspend fun ensureDefaults() {
        if (categoryDao.count() == 0) categoryDao.insertAll(DefaultCategories.all)
        if (categoryDao.countIncome() == 0) categoryDao.insertAll(DefaultCategories.income)
    }

    /** Busca la categoría para un comercio: tus reglas, luego lo aprendido y luego palabras clave. */
    suspend fun categorize(merchant: String): Long? = categorize(merchant, customRuleDao.getAll())

    /** Igual, con tus reglas ya leídas (para clasificar muchos movimientos seguidos sin releerlas cada vez). */
    private suspend fun categorize(merchant: String, rules: List<CustomRule>): Long? {
        RuleMatcher.find(rules, merchant)?.categoryId?.let { return it }
        CustomMerchants.find(customMerchantDao.getAll(), merchant)?.categoryId?.let { return it }
        // Lo aprendido se guarda con el nombre que ves: el limpio del catálogo o el texto original.
        for (name in listOfNotNull(MerchantCatalog.find(merchant)?.name, merchant).distinct()) {
            val key = Categorizer.merchantKey(name)
            if (key.isNotBlank()) ruleDao.get(key)?.let { return it.categoryId }
        }
        val name = Categorizer.suggestCategoryName(merchant) ?: return null
        // Una categoría oculta no se usa para adivinar: mejor preguntar que esconder el gasto ahí
        return defaultCategory(name)?.takeIf { !it.hidden }?.id
    }

    val allExpenses: Flow<List<Expense>> = expenseDao.observeAll()

    /**
     * Pone categoría a los gastos que no la tienen usando tus reglas, lo aprendido y el catálogo de comercios.
     * Se usa una vez al estrenar el catálogo, para que los pagos ya guardados queden clasificados.
     * Devuelve cuántos gastos ha clasificado.
     */
    suspend fun categorizeUncategorized(): Int {
        ensureDefaults()
        val rules = customRuleDao.getAll()
        val byCategory = expenseDao.getUncategorizedSpending()
            .filter { it.merchant.isNotBlank() }
            .mapNotNull { e -> categorize(e.merchant, rules)?.let { it to e.id } }
            .groupBy({ it.first }, { it.second })
        byCategory.forEach { (categoryId, ids) -> expenseDao.setCategory(ids, categoryId) }
        return byCategory.values.sumOf { it.size }
    }

    /** Cambia la categoría de todos los gastos de un comercio (pasados y futuros). */
    suspend fun recategorizeMerchant(merchantKey: String, categoryId: Long) {
        if (merchantKey.isBlank()) return
        ruleDao.upsert(MerchantRule(merchantKey, categoryId))
        val ids = expenseDao.getSpending()
            .filter { Categorizer.merchantKey(it.merchant) == merchantKey }
            .map { it.id }
        if (ids.isNotEmpty()) expenseDao.setCategory(ids, categoryId)
    }

    suspend fun categoryIdByName(name: String): Long? = defaultCategory(name)?.id

    /** Categoría por defecto por su nombre en español, aunque se creara en inglés. */
    private suspend fun defaultCategory(spanish: String): Category? =
        categoryDao.getByName(spanish) ?: CategoryNames.english[spanish]?.let { categoryDao.getByName(it) }

    suspend fun addExpense(expense: Expense): Long {
        val id = expenseDao.insert(expense)
        if (!expense.isIncome) {
            checkBudget(expense.categoryId, expense.timestamp)
            checkTripBudget(expense.tripId)
        }
        return id
    }

    /**
     * Guarda cambios de un gasto. Si [learnMerchant] es true y la categoría ha cambiado,
     * la app recuerda la categoría para ese comercio y la aplica a sus gastos sin categorizar.
     */
    suspend fun updateExpense(old: Expense, new: Expense, learnMerchant: Boolean) {
        expenseDao.update(new)
        val catId = new.categoryId
        if (learnMerchant && catId != null && old.categoryId != catId) {
            learn(new.merchant, catId)
        }
        if (!new.isIncome) {
            checkBudget(catId, new.timestamp)
            if (new.tripId != old.tripId || new.amountCents != old.amountCents) checkTripBudget(new.tripId)
        }
    }

    suspend fun learn(merchant: String, categoryId: Long) {
        val key = Categorizer.merchantKey(merchant)
        if (key.isBlank()) return
        ruleDao.upsert(MerchantRule(key, categoryId))
        // Aplica la regla a los gastos sin categoría del mismo comercio ("MERCADONA 1234" = "Mercadona 5678").
        val ids = expenseDao.getUncategorized()
            .filter { Categorizer.merchantKey(it.merchant) == key }
            .map { it.id }
        if (ids.isNotEmpty()) expenseDao.setCategory(ids, categoryId)
    }

    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    suspend fun setCategory(ids: List<Long>, categoryId: Long) {
        if (ids.isNotEmpty()) expenseDao.setCategory(ids, categoryId)
    }

    /** Vuelve a guardar un gasto borrado (deshacer), con su mismo id. */
    suspend fun restoreExpense(expense: Expense) {
        expenseDao.insert(expense)
    }

    /** Registra un pago detectado en una notificación. Devuelve null si es un duplicado. */
    suspend fun addDetectedPayment(payment: ParsedPayment, rawText: String, timestamp: Long, eventId: String? = null): Pair<Expense, Category?>? =
        detectionLock.withLock {
            val result = db.withTransaction {
            if (eventId != null && db.ingestionDao().contains(eventId)) return@withTransaction null
            ensureDefaults()
            // Google Wallet a veces actualiza la misma notificación: ignorar repetidos en 3 minutos.
            // Pagos en otra moneda se guardan en euros al último cambio usado (o uno aproximado)
            val foreign = payment.currency != Currencies.base
            val rate = if (foreign) prefs.rateFor(payment.currency) ?: Currencies.approxRate(payment.currency) else 1.0
            val eurCents = if (foreign) Currencies.toBaseCents(payment.amountCents, rate) else payment.amountCents
            val customName = RuleMatcher.displayName(customRuleDao.getAll(), payment.merchant)
            // Tus reglas de nombre mandan; luego tus comercios y, si no, la marca conocida («MERCADONA 4432 VALENCIA» → «Mercadona»).
            val merchantName = if (customName != payment.merchant) customName
                else CustomMerchants.find(customMerchantDao.getAll(), payment.merchant)?.name ?: MerchantCatalog.displayName(payment.merchant)
            // Una devolución se guarda en negativo: resta del gasto de su categoría y su mes
            val sign = if (payment.refund) -1 else 1
            val duplicate = expenseDao.detectedBetween(timestamp - 90_000, timestamp + 90_000).any { e ->
                (e.originalCurrency ?: Currencies.base) == payment.currency &&
                    (e.originalAmountCents ?: e.amountCents) == sign * payment.amountCents &&
                    com.midinero.control.domain.DetectedDuplicates.sameMerchant(e.merchant, merchantName)
            } || (!payment.refund && merchantName.equals(tr("Pago con Google Pay", "Google Pay payment"), ignoreCase = true) &&
                expenseDao.countRecentWalletPayments(eurCents, timestamp - 90_000) > 0)
            if (eventId != null) db.ingestionDao().insert(IngestionReceipt(eventId))
            if (duplicate) return@withTransaction null
            val original = if (payment.refund) refundedPurchase(merchantName, eurCents, timestamp) else null
            val categoryId = original?.categoryId ?: categorize(payment.merchant)
            val rateNote = if (foreign) tr("Cambio aproximado", "Approximate rate") + ": 1 ${payment.currency} = ${"%.4f".format(Lang.locale, rate).trimEnd('0').trimEnd('.', ',')} ${Currencies.symbol()}" else ""
            val expense = Expense(
                tripId = original?.tripId ?: autoTripFor(timestamp),
                amountCents = sign * eurCents,
                originalCurrency = if (foreign) payment.currency else null,
                originalAmountCents = if (foreign) sign * payment.amountCents else null,
                note = if (payment.refund) listOf(refundNote(original), rateNote).filter { it.isNotBlank() }.joinToString(" · ") else rateNote,
                merchant = original?.merchant ?: merchantName,
                categoryId = categoryId,
                timestamp = timestamp,
                source = ExpenseSource.GOOGLE_PAY,
                rawText = rawText.take(500),
            )
            val id = expenseDao.insert(expense)
            val saved = expense.copy(id = id)
            val category = categoryId?.let { categoryDao.getById(it) }
            // Devolución sin la compra original: la categoría es solo una suposición
            Triple(saved, category, payment.refund && original == null)
            } ?: return@withLock null
            val (saved, category, unmatchedRefund) = result
            val categoryId = saved.categoryId
            val ask = category == null || unmatchedRefund
            val quick = if (ask) quickCategories() else emptyList()
            // Sin categoría (o devolución sin compra) se avisa siempre, para poder elegirla desde la notificación.
            if (prefs.notifyDetected || ask) AppNotifications.paymentDetected(context, saved, category, quick, askCategory = unmatchedRefund)
            checkBudget(categoryId, timestamp)
            checkTripBudget(saved.tripId)
            saved to category
        }

    /**
     * Compra que se devuelve: la más reciente del mismo comercio en los últimos 6 meses por al menos ese importe
     * (o, si no hay, la más reciente de ese comercio). Así la devolución resta de la misma categoría y el mismo viaje.
     */
    private suspend fun refundedPurchase(merchant: String, cents: Long, timestamp: Long): Expense? {
        val key = Categorizer.merchantKey(merchant)
        if (key.isBlank()) return null
        val sameMerchant = expenseDao.purchasesBetween(timestamp - 183L * 24 * 3600 * 1000, timestamp)
            .filter { Categorizer.merchantKey(it.merchant) == key }
        return sameMerchant.firstOrNull { it.amountCents >= cents } ?: sameMerchant.firstOrNull()
    }

    private fun refundNote(original: Expense?): String =
        if (original == null) tr("Devolución", "Refund")
        else tr("Devolución de la compra del ${dateOf(original.timestamp).shortLabel()}", "Refund of the purchase on ${dateOf(original.timestamp).shortLabel()}")

    /**
     * Apunta a mano la devolución de [original] por [cents] (en positivo, como mucho su importe) con la fecha de hoy.
     * Queda en su misma categoría y viaje, restando de su gasto.
     */
    suspend fun addRefund(original: Expense, cents: Long, timestamp: Long = System.currentTimeMillis()): Expense {
        require(!original.isIncome && original.amountCents > 0 && cents > 0)
        val amount = cents.coerceAtMost(original.amountCents)
        val refund = Expense(
            amountCents = -amount,
            merchant = original.merchant,
            note = refundNote(original),
            categoryId = original.categoryId,
            timestamp = timestamp,
            tripId = original.tripId,
            source = ExpenseSource.MANUAL,
        )
        return refund.copy(id = expenseDao.insert(refund))
    }

    /**
     * Gasto apuntado en el reloj. [eventId] es único por apunte: si el reloj lo reenvía (sin conexión, reinicios)
     * no se guarda dos veces. Devuelve false si ya estaba.
     */
    suspend fun addFromWatch(eventId: String, amountCents: Long, categoryId: Long?, note: String, timestamp: Long): Boolean {
        val saved = detectionLock.withLock {
            db.withTransaction {
                if (db.ingestionDao().contains(eventId)) return@withTransaction null
                ensureDefaults()
                val category = categoryId?.let { categoryDao.getById(it) }
                val merchant = note.trim().ifBlank { category?.name ?: tr("Gasto desde el reloj", "Expense from watch") }.take(60)
                val expense = Expense(
                    amountCents = amountCents,
                    merchant = merchant,
                    categoryId = category?.id ?: categorize(merchant),
                    timestamp = timestamp,
                    tripId = autoTripFor(timestamp),
                    source = ExpenseSource.MANUAL,
                )
                db.ingestionDao().insert(IngestionReceipt(eventId))
                expense.copy(id = expenseDao.insert(expense))
            }
        } ?: return false
        checkBudget(saved.categoryId, saved.timestamp)
        checkTripBudget(saved.tripId)
        return true
    }

    /** Lo que se suma este [month] al presupuesto total por arrastre del mes anterior (0 si no está activado). */
    suspend fun totalRolloverBonus(limit: Long, month: YearMonth): Long = rolloverBonus(limit, null, month)

    /**
     * Borra las recargas de Revolut que se apuntaron solas antes de que la app las ignorara
     * (pagos detectados cuyo comercio menciona Revolut). Los apuntados a mano no se tocan.
     */
    suspend fun removeDetectedTopUps(): Int = db.withTransaction {
        val topUps = expenseDao.getDetectedRevolut().filter { PaymentParser.isTopUp(it.merchant) }
        topUps.forEach { expenseDao.delete(it) }
        topUps.size
    }

    /** Las 3 categorías más usadas, para el widget de añadir rápido. */
    suspend fun quickCategoriesForWidget(): List<Category> = quickCategories()

    /** Hasta 3 categorías para asignar desde la notificación: las más usadas, o unas habituales. */
    private suspend fun quickCategories(): List<Category> {
        val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val used = expenseDao.topCategoryIds(since, 6).mapNotNull { categoryDao.getById(it) }.filter { !it.hidden }.take(3)
        if (used.size >= 3) return used
        val fallback = listOf(DefaultCategories.SUPERMERCADO, DefaultCategories.RESTAURANTES, DefaultCategories.COMPRAS)
            .mapNotNull { defaultCategory(it) }.filter { !it.hidden }
        return (used + fallback).distinctBy { it.id }.take(3)
    }

    /** Asigna una categoría a un gasto (desde la notificación) y la recuerda para ese comercio. */
    suspend fun assignCategory(expenseId: Long, categoryId: Long): Boolean {
        val old = expenseDao.getById(expenseId) ?: return false
        if (categoryDao.getById(categoryId) == null) return false
        updateExpense(old, old.copy(categoryId = categoryId), learnMerchant = true)
        return true
    }

    suspend fun setLimit(category: Category, limitCents: Long?) {
        categoryDao.update(category.copy(monthlyLimitCents = limitCents))
    }

    /** Devuelve false si ya existe otra categoría con ese nombre. */
    suspend fun saveCategory(category: Category): Boolean {
        val sameName = categoryDao.getByName(category.name.trim())
        if (sameName != null && sameName.id != category.id) return false
        return if (category.id == 0L) {
            categoryDao.insert(category.withNextSortOrder()) > 0
        } else {
            categoryDao.update(category); true
        }
    }

    /** Crea una categoría nueva y devuelve su id, o null si ya hay otra con ese nombre. */
    suspend fun createCategory(category: Category): Long? {
        val named = category.copy(id = 0, name = category.name.trim())
        if (named.name.isEmpty() || categoryDao.getByName(named.name) != null) return null
        return categoryDao.insert(named.withNextSortOrder()).takeIf { it > 0 }
    }

    /** Si ya has ordenado tus categorías, una nueva va al final en lugar de colarse arriba. */
    private suspend fun Category.withNextSortOrder(): Category =
        categoryDao.maxSortOrder().let { max -> if (max > 0) copy(sortOrder = max + 1) else this }

    /**
     * Borra una categoría. Con [moveTo], sus movimientos, cargos fijos, reglas y lo aprendido pasan a esa otra
     * categoría; sin ella, los movimientos quedan sin categoría y lo aprendido se olvida.
     */
    suspend fun deleteCategory(category: Category, moveTo: Long? = null): DeletedCategory = db.withTransaction {
        // Foto de lo que la usaba, para poder deshacerlo
        val deleted = DeletedCategory(
            category = categoryDao.getById(category.id) ?: category,
            expenseIds = categoryDao.expenseIdsIn(category.id),
            recurringIds = categoryDao.recurringIdsIn(category.id),
            customRuleIds = categoryDao.customRuleIdsIn(category.id),
            merchantRules = categoryDao.merchantRulesIn(category.id),
        )
        if (moveTo != null && moveTo != category.id && categoryDao.getById(moveTo) != null) {
            categoryDao.moveExpenses(category.id, moveTo)
            categoryDao.moveRecurring(category.id, moveTo)
            categoryDao.moveCustomRules(category.id, moveTo)
            categoryDao.moveMerchantRules(category.id, moveTo)
        }
        categoryDao.delete(category)
        deleted
    }

    /** Deshace [deleteCategory]: vuelve la categoría (con su id) y todo lo que la usaba vuelve a ella. */
    suspend fun restoreCategory(deleted: DeletedCategory) = db.withTransaction {
        val id = deleted.category.id
        if (categoryDao.getById(id) != null) return@withTransaction
        // Si mientras tanto se creó otra con el mismo nombre, la restaurada se distingue
        val name = deleted.category.name
        val free = if (categoryDao.getByName(name) == null) name else "$name (2)"
        categoryDao.insert(deleted.category.copy(name = free))
        // Por tandas, para no pasar del límite de variables de SQLite
        deleted.expenseIds.chunked(500).forEach { categoryDao.setExpensesCategory(it, id) }
        deleted.recurringIds.chunked(500).forEach { categoryDao.setRecurringCategory(it, id) }
        deleted.customRuleIds.chunked(500).forEach { categoryDao.setCustomRulesCategory(it, id) }
        deleted.merchantRules.forEach { ruleDao.upsert(it) }
    }

    /** Lo necesario para deshacer el borrado de una categoría. */
    data class DeletedCategory(
        val category: Category,
        val expenseIds: List<Long>,
        val recurringIds: List<Long>,
        val customRuleIds: List<Long>,
        val merchantRules: List<MerchantRule>,
    )

    /** Guarda el orden de las categorías: [ids] de la primera a la última. */
    suspend fun reorderCategories(ids: List<Long>) = db.withTransaction {
        ids.forEachIndexed { i, id -> categoryDao.setSortOrder(id, i + 1) }
    }

    suspend fun forgetLearnedRules() = ruleDao.clear()

    val rules: Flow<List<MerchantRule>> = ruleDao.observeAll()

    suspend fun deleteRule(rule: MerchantRule) = ruleDao.delete(rule)

    // ---------- Tickets ----------

    /** Borra las fotos de tickets de gastos que ya no existen. */
    suspend fun cleanReceipts(): Int = receipts.cleanOrphans(expenseDao.receiptNames().toSet())

    // ---------- Viajes y eventos ----------

    val trips: Flow<List<Trip>> = tripDao.observeAll()

    val tripTotals: Flow<Map<Long, Long>> = expenseDao.observeTripTotals().map { list -> list.associate { it.tripId to it.totalCents } }

    fun tripExpenses(tripId: Long): Flow<List<Expense>> = expenseDao.observeByTrip(tripId)

    suspend fun trip(id: Long): Trip? = tripDao.getById(id)

    /** Viaje activo que recoge solos los gastos de ese momento, si hay alguno. */
    suspend fun autoTripFor(timestamp: Long): Long? = tripDao.activeOn(dateOf(timestamp).toEpochDay())?.id

    /** Crea o guarda un viaje. Si es nuevo, recoge los gastos ya apuntados en sus fechas. Devuelve su id y cuántos recogió. */
    suspend fun saveTrip(trip: Trip, collectExisting: Boolean): Pair<Long, Int> = db.withTransaction {
        val id = if (trip.id == 0L) tripDao.insert(trip) else trip.id.also { tripDao.update(trip) }
        val collected = if (collectExisting) {
            val from = LocalDate.ofEpochDay(trip.startDay).atStartOfDay(zone).toInstant().toEpochMilli()
            val endDay = trip.endDay ?: LocalDate.now().toEpochDay()
            val to = LocalDate.ofEpochDay(endDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()
            expenseDao.assignTripBetween(id, from, to)
        } else 0
        id to collected
    }

    /** Borra el viaje; sus gastos se quedan, solo pierden la etiqueta. */
    suspend fun deleteTrip(trip: Trip) = db.withTransaction {
        expenseDao.clearTrip(trip.id)
        tripDao.delete(trip)
    }

    /** Aviso al llegar al 80 % y al 100 % del presupuesto de un viaje (una vez cada uno). */
    private suspend fun checkTripBudget(tripId: Long?) {
        val trip = tripId?.let { tripDao.getById(it) } ?: return
        val budget = trip.budgetCents?.takeIf { it > 0 } ?: return
        val spent = expenseDao.tripTotal(trip.id)
        val lvl = TripStats.alertLevel(spent, budget) ?: return
        val alerts = context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
        val key = "alert_trip_${trip.id}_$lvl"
        if (alerts.getBoolean(key, false)) return
        alerts.edit { putBoolean(key, true) }
        AppNotifications.tripBudgetAlert(context, trip, spent, budget)
    }

    // ---------- Cuentas con amigos ----------

    val debts: Flow<List<Debt>> = debtDao.observeAll()

    /** Apunta una deuda nueva o guarda los cambios de una existente. */
    suspend fun saveDebt(debt: Debt) { if (debt.id == 0L) debtDao.insert(debt) else debtDao.update(debt) }

    suspend fun deleteDebt(debt: Debt) = debtDao.delete(debt)

    /** Deshacer el borrado: vuelve con su mismo id. */
    suspend fun restoreDebt(debt: Debt) { debtDao.insert(debt) }

    suspend fun settlePerson(person: String) = debtDao.settlePerson(person, System.currentTimeMillis())

    /**
     * Comparte un gasto a partes iguales: el gasto se queda con tu parte y se apunta
     * lo que te debe cada persona.
     */
    suspend fun shareExpense(expense: Expense, people: List<String>) {
        if (people.isEmpty() || expense.isIncome) return
        val (mine, others) = Split.equal(expense.amountCents, people)
        db.withTransaction {
            val names = people.joinToString(", ")
            val note = listOf(expense.note, tr("Compartido con $names (total ${formatCents(expense.amountCents)})", "Shared with $names (total ${formatCents(expense.amountCents)})")).filter { it.isNotBlank() }.joinToString(" · ")
            expenseDao.update(expense.copy(amountCents = mine, note = note.take(300)))
            others.forEach { (person, cents) ->
                debtDao.insert(Debt(person = person, amountCents = cents, note = expense.merchant, expenseId = expense.id))
            }
        }
    }

    // ---------- Datos de ejemplo ----------

    /** Carga unos meses de gastos, fijos y metas de ejemplo para enseñar la app. */
    suspend fun loadDemo() {
        ensureDefaults()
        val rnd = kotlin.random.Random(7)
        val today = LocalDate.now()
        val samples = listOf(
            "Mercadona" to (2500L..9000L), "Lidl" to (1500L..5000L), "Glovo" to (1500L..3500L),
            "Repsol" to (4000L..7000L), "Bar Casa Pepe" to (300L..2500L), "Cinesa" to (800L..2000L),
            "Zara" to (2000L..6000L), "Farmacia Sol" to (500L..2500L), "Renfe" to (500L..3000L),
            "Starbucks" to (350L..700L), "Ikea" to (1500L..12000L),
        )
        val ids = mutableSetOf<String>()
        db.withTransaction {
            for (daysAgo in 0L..110L) {
                val day = today.minusDays(daysAgo)
                repeat(rnd.nextInt(0, 3)) {
                    val (name, range) = samples[rnd.nextInt(samples.size)]
                    expenseDao.insert(
                        Expense(
                            amountCents = rnd.nextLong(range.first, range.last),
                            merchant = name,
                            categoryId = categorize(name),
                            timestamp = day.atTime(rnd.nextInt(9, 22), rnd.nextInt(0, 59)).atZone(zone).toInstant().toEpochMilli(),
                            source = if (rnd.nextBoolean()) ExpenseSource.GOOGLE_PAY else ExpenseSource.MANUAL,
                            rawText = DEMO_MARKER,
                        )
                    )
                }
            }
            val nomina = defaultCategory(DefaultCategories.NOMINA)?.id
            for (m in 0L..3L) {
                val d = today.minusMonths(m).withDayOfMonth(1)
                if (d <= today) {
                    expenseDao.insert(
                        Expense(
                            amountCents = 185000, merchant = tr("Nómina (ejemplo)", "Salary (sample)"), categoryId = nomina, isIncome = true,
                            timestamp = d.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), rawText = DEMO_MARKER,
                        )
                    )
                }
            }
            val next = today.plusDays(5)
            listOf("Netflix" to 1299L, "Spotify" to 1099L, tr("Alquiler cochera", "Parking rent") to 6000L).forEach { (name, cents) ->
                ids += "r" + recurringDao.insert(
                    RecurringExpense(
                        name = name, amountCents = cents, categoryId = categorize(name),
                        dayOfMonth = next.dayOfMonth, nextDueEpochDay = next.toEpochDay(),
                    )
                )
            }
            val goal = goalDao.insert(SavingsGoal(name = tr("Viaje (ejemplo)", "Trip (sample)"), emoji = "✈️", targetCents = 150000, deadlineEpochDay = today.plusMonths(8).toEpochDay()))
            goalDao.insert(GoalContribution(goalId = goal, amountCents = 30000, timestamp = System.currentTimeMillis()))
            ids += "g$goal"
            // Una escapada del mes pasado, con gastos en libras, para enseñar viajes y monedas
            val tripStart = today.minusDays(40)
            val trip = tripDao.insert(
                Trip(name = tr("Londres (ejemplo)", "London (sample)"), emoji = "🇬🇧", budgetCents = 60000, startDay = tripStart.toEpochDay(),
                    endDay = tripStart.plusDays(3).toEpochDay(), autoAssign = false)
            )
            ids += "t$trip"
            listOf("Pret A Manger" to 1250L, "Transport for London" to 2400L, "Hotel Camden" to 32000L, "Dishoom" to 5600L)
                .forEachIndexed { i, (name, pence) ->
                    val rate = Currencies.approxRate("GBP")
                    expenseDao.insert(
                        Expense(
                            amountCents = Currencies.toBaseCents(pence, rate), merchant = name, categoryId = categorize(name),
                            timestamp = tripStart.plusDays(i.toLong()).atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
                            rawText = DEMO_MARKER, originalCurrency = "GBP", originalAmountCents = pence, tripId = trip,
                        )
                    )
                }
        }
        defaultCategory(DefaultCategories.COMIDA_DOMICILIO)?.let { if (it.monthlyLimitCents == null) categoryDao.update(it.copy(monthlyLimitCents = 6000)) }
        prefs.demoIds = ids
    }

    /** Borra solo los datos de ejemplo; lo que haya apuntado el usuario se queda. */
    suspend fun clearDemo() {
        db.withTransaction {
            expenseDao.deleteByRawText(DEMO_MARKER)
            val ids = prefs.demoIds
            recurringDao.getAll().filter { "r${it.id}" in ids }.forEach { recurringDao.delete(it) }
            goalDao.getGoals().filter { "g${it.id}" in ids }.forEach { goalDao.delete(it) }
            tripDao.getAll().filter { "t${it.id}" in ids }.forEach { tripDao.delete(it) }
        }
        prefs.demoIds = emptySet()
    }

    // ---------- Importar del banco ----------

    /** Resultado de importar: cuántos se han añadido y cuántos ya estaban. */
    data class ImportResult(val added: Int, val duplicates: Int, val skippedIncome: Int)

    /**
     * Añade los movimientos de un CSV del banco. Los importes negativos son gastos y los positivos ingresos
     * (solo si [includeIncome]). No duplica lo que ya existe (mismo día, importe y comercio).
     */
    suspend fun importRows(rows: List<ImportedRow>, includeIncome: Boolean): ImportResult = db.withTransaction {
        // Lo que ya hay en la app. Cada movimiento existente solo «tapa» una fila del CSV: dos cafés iguales el mismo
        // día en el fichero son dos gastos. Un pago detectado del móvil («Mercadona») cuenta como la misma compra que
        // la fila del banco («COMPRA TARJ. 5234XXXX MERCADONA VALENCIA») si coincide el importe y el día (±1).
        class Seen(val date: java.time.LocalDate, val cents: Long, val key: String, val imported: Boolean)
        // Solo los movimientos de las fechas del fichero (±2 días), no todo el historial
        val dates = rows.map { it.date }
        val pool = (if (dates.isEmpty()) emptyList() else expenseDao.getBetween(
            dates.min().minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli(),
            dates.max().plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli(),
        ))
            .map { Seen(dateOf(it.timestamp), it.amountCents, Categorizer.merchantKey(it.merchant), it.source == ExpenseSource.IMPORTED) }
            .toMutableList()
        fun takeExisting(date: java.time.LocalDate, cents: Long, keys: List<String>): Boolean {
            val i = pool.indexOfFirst { e ->
                e.cents == cents && (
                    (e.date == date && e.key in keys) ||
                        (!e.imported && kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(e.date, date)) <= 1 &&
                            keys.any { k -> k.length >= 3 && e.key.length >= 3 && (k.contains(e.key) || e.key.contains(k)) })
                    )
            }
            if (i < 0) return false
            pool.removeAt(i)
            return true
        }
        val rules = customRuleDao.getAll()
        val otherIncome = defaultCategory(DefaultCategories.OTROS_INGRESOS)?.id
        var added = 0; var dupes = 0; var skipped = 0
        for (row in rows) {
            // Un abono que es la devolución de una compra resta del gasto (no es un ingreso)
            val refund = row.amountCents > 0 && PaymentParser.isRefundText(row.description)
            val income = row.amountCents > 0 && !refund
            if (income && !includeIncome) { skipped++; continue }
            val abs = kotlin.math.abs(row.amountCents)
            val cents = if (refund) -abs else abs
            val name = RuleMatcher.displayName(rules, row.description)
            if (takeExisting(row.date, cents, listOf(Categorizer.merchantKey(name), Categorizer.merchantKey(row.description)))) { dupes++; continue }
            val timestamp = row.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val original = if (refund) refundedPurchase(name, abs, timestamp) else null
            expenseDao.insert(
                Expense(
                    amountCents = cents,
                    merchant = original?.merchant ?: name,
                    categoryId = when {
                        income -> otherIncome
                        original != null -> original.categoryId
                        else -> categorize(row.description, rules)
                    },
                    timestamp = timestamp,
                    source = ExpenseSource.IMPORTED,
                    isIncome = income,
                    rawText = row.description,
                    note = if (refund) refundNote(original) else "",
                    tripId = original?.tripId,
                )
            )
            added++
        }
        ImportResult(added, dupes, skipped)
    }

    // ---------- Reglas personalizadas ----------

    val customRules: Flow<List<CustomRule>> = customRuleDao.observeAll()

    // ---------- Retos de ahorro ----------

    /** Avisa de los retos cumplidos que aún no se habían avisado. */
    suspend fun checkChallenges() {
        val list = prefs.challenges
        if (list.isEmpty()) return
        val expenses = expenseDao.getAll()
        val categories = categoryDao.getAll().associateBy { it.id }
        val notified = prefs.notifiedAchievements
        val fresh = com.midinero.control.domain.Challenges.evaluateAll(list, expenses).filter { it.achievementKey != null && it.achievementKey !in notified }
        if (fresh.isEmpty()) return
        fresh.forEach { p ->
            val title = com.midinero.control.domain.Challenges.title(p.challenge, p.challenge.categoryId?.let { categories[it]?.name })
            com.midinero.control.service.AppNotifications.challengeAchieved(context, p.achievementKey!!, title)
        }
        prefs.notifiedAchievements = notified + fresh.mapNotNull { it.achievementKey }
    }

    // ---------- Tus comercios ----------

    val customMerchants: Flow<List<CustomMerchant>> = customMerchantDao.observeAll()

    /** Guarda un comercio tuyo. Devuelve false si ya hay otro con ese nombre. */
    suspend fun saveCustomMerchant(merchant: CustomMerchant): Boolean {
        val name = merchant.name.trim()
        if (name.isBlank()) return false
        val clash = customMerchantDao.getAll().any { it.id != merchant.id && it.name.equals(name, ignoreCase = true) }
        if (clash) return false
        val clean = merchant.copy(name = name, aliases = CustomMerchants.cleanAliases(merchant.aliases))
        if (clean.id == 0L) customMerchantDao.insert(clean) else customMerchantDao.update(clean)
        // Con categoría, los gastos suyos que aún no tenían ninguna la toman (los que ya clasificaste no se tocan)
        clean.categoryId?.let { categoryId ->
            val ids = expenseDao.getUncategorizedSpending()
                .filter { CustomMerchants.find(listOf(clean), it.merchant) != null }
                .map { it.id }
            if (ids.isNotEmpty()) expenseDao.setCategory(ids, categoryId)
        }
        return true
    }

    suspend fun deleteCustomMerchant(merchant: CustomMerchant) = customMerchantDao.delete(merchant)

    suspend fun saveCustomRule(rule: CustomRule) {
        if (rule.id == 0L) customRuleDao.insert(rule) else customRuleDao.update(rule)
    }

    suspend fun deleteCustomRule(rule: CustomRule) = customRuleDao.delete(rule)

    /** Aplica una regla a los gastos ya guardados. Devuelve cuántos ha cambiado. */
    suspend fun applyRuleToHistory(rule: CustomRule): Int = db.withTransaction {
        var changed = 0
        expenseDao.getSpending().filter { RuleMatcher.matches(rule, it.merchant) }.forEach { e ->
            val updated = e.copy(
                categoryId = rule.categoryId ?: e.categoryId,
                merchant = rule.renameTo?.takeIf { it.isNotBlank() } ?: e.merchant,
            )
            if (updated != e) {
                expenseDao.update(updated)
                changed++
            }
        }
        changed
    }

    // ---------- Metas de ahorro ----------

    val goals: Flow<List<SavingsGoal>> = goalDao.observeGoals()
    val contributions: Flow<List<GoalContribution>> = goalDao.observeContributions()

    suspend fun saveGoal(goal: SavingsGoal) {
        if (goal.id == 0L) goalDao.insert(goal) else goalDao.update(goal)
    }

    suspend fun deleteGoal(goal: SavingsGoal) = goalDao.delete(goal)

    suspend fun contribute(goalId: Long, amountCents: Long, note: String = "") {
        goalDao.insert(GoalContribution(goalId = goalId, amountCents = amountCents, timestamp = System.currentTimeMillis(), note = note))
    }

    suspend fun deleteContribution(c: GoalContribution) = goalDao.delete(c)

    // ---------- Copias de seguridad ----------

    suspend fun exportBackup(): BackupData = db.withTransaction {
        BackupData(
            categories = categoryDao.getAll(),
            expenses = expenseDao.getAll(),
            rules = ruleDao.getAll(),
            recurring = recurringDao.getAll(),
            customRules = customRuleDao.getAll(),
            debts = debtDao.getAll(),
            trips = tripDao.getAll(),
            customMerchants = customMerchantDao.getAll(),
            accounts = accountDao.getAll(),
            transfers = accountDao.getTransfers(),
            goals = goalDao.getGoals(),
            contributions = goalDao.getContributions(),
            settings = BackupSettings(
                monthlyBudgetCents = prefs.monthlyBudgetCents,
                alertThreshold = prefs.alertThreshold,
                periodStartDay = prefs.periodStartDay,
                weeklySummary = prefs.weeklySummary,
                enabledPaymentApps = prefs.enabledPaymentApps,
                challengesJson = AppPrefs.challengesToJson(prefs.challenges),
                mainCurrency = prefs.mainCurrency,
            ),
        )
    }

    /** Sustituye todos los datos por los de la copia. */
    suspend fun restoreBackup(data: BackupData) {
        db.withTransaction {
            wipe()
            categoryDao.insertAll(data.categories)
            expenseDao.insertAll(data.expenses)
            ruleDao.insertAll(data.rules)
            recurringDao.insertAll(data.recurring)
            customRuleDao.insertAll(data.customRules)
            debtDao.insertAll(data.debts)
            tripDao.insertAll(data.trips)
            customMerchantDao.insertAll(data.customMerchants)
            accountDao.insertAll(data.accounts)
            accountDao.insertTransfers(data.transfers)
            goalDao.insertGoals(data.goals)
            goalDao.insertContributions(data.contributions)
        }
        data.settings?.let { st ->
            prefs.monthlyBudgetCents = st.monthlyBudgetCents
            prefs.alertThreshold = st.alertThreshold
            prefs.periodStartDay = st.periodStartDay
            Period.startDay = st.periodStartDay
            prefs.weeklySummary = st.weeklySummary
            prefs.enabledPaymentApps = st.enabledPaymentApps
            st.challengesJson?.let { json -> runCatching { prefs.challenges = AppPrefs.challengesFromJson(json) } }
            st.mainCurrency?.let { prefs.mainCurrency = it }
        }
        ensureDefaults()
    }

    /** Borra todos los gastos, recurrentes y reglas, y restaura las categorías por defecto. */
    suspend fun deleteAllData() {
        db.withTransaction { wipe() }
        receipts.deleteAll()
        context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE).edit { clear() }
        prefs.monthlyBudgetCents = 0
        prefs.lastDetectionAt = 0
        prefs.clearUnrecognized()
        ensureDefaults()
    }

    private suspend fun wipe() {
        accountDao.deleteTransfers()
        accountDao.deleteAll()
        customMerchantDao.deleteAll()
        goalDao.deleteAllGoals()
        customRuleDao.deleteAll()
        debtDao.deleteAll()
        tripDao.deleteAll()
        expenseDao.deleteAll()
        ruleDao.clear()
        recurringDao.deleteAll()
        categoryDao.deleteAll()
    }

    /**
     * Avisa al llegar al umbral elegido (80 % por defecto) y al superar el límite mensual de la categoría
     * y del presupuesto global. Una vez por umbral y mes.
     */
    /** Lo que sobró del [limit] el mes anterior, si está activado arrastrarlo ([categoryId] null = presupuesto total). */
    private suspend fun rolloverBonus(limit: Long, categoryId: Long?, month: java.time.YearMonth): Long {
        if (!prefs.rolloverEnabled) return 0L
        val prev = month.minusMonths(1)
        val active = expenseDao.totalSpent(prev.startMillis(), prev.endMillis()) > 0
        val spent = if (categoryId == null) expenseDao.totalSpent(prev.startMillis(), prev.endMillis())
        else expenseDao.totalForCategory(categoryId, prev.startMillis(), prev.endMillis())
        return com.midinero.control.domain.Rollover.bonus(limit, spent, active)
    }

    private suspend fun checkBudget(categoryId: Long?, timestamp: Long) {
        val month = monthOf(timestamp)
        if (month != currentPeriod()) return
        val threshold = prefs.alertThreshold
        val alerts = context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
        fun level(spent: Long, limit: Long): Int? = when {
            spent >= limit -> 100
            threshold in 1..99 && spent * 100 >= limit * threshold -> threshold
            else -> null
        }
        fun firstTime(key: String): Boolean {
            if (alerts.getBoolean(key, false)) return false
            alerts.edit { putBoolean(key, true) }
            return true
        }

        val category = categoryId?.let { categoryDao.getById(it) }
        val baseLimit = category?.monthlyLimitCents
        val limit = baseLimit?.let { it + rolloverBonus(it, category!!.id, month) }
        if (category != null && !category.isIncome && limit != null && limit > 0) {
            val spent = expenseDao.totalForCategory(category.id, month.startMillis(), month.endMillis())
            level(spent, limit)?.let { lvl ->
                if (firstTime("alert_${category.id}_${month}_$lvl")) AppNotifications.budgetAlert(context, category, spent, limit)
            }
        }

        val budget = prefs.monthlyBudgetCents.let { if (it > 0) it + rolloverBonus(it, null, month) else it }
        if (budget > 0) {
            val spent = expenseDao.totalSpent(month.startMillis(), month.endMillis())
            level(spent, budget)?.let { lvl ->
                if (firstTime("alert_global_${month}_$lvl")) AppNotifications.globalBudgetAlert(context, spent, budget)
            }
        }
    }

    /** Total gastado en un periodo, observable (para el widget). */
    fun observeSpent(month: YearMonth): Flow<Long> = expenseDao.observeTotalSpent(month.startMillis(), month.endMillis())

    /** Gastos entre dos instantes (para el resumen semanal). */
    suspend fun spentBetween(from: Long, to: Long): Long = expenseDao.totalSpent(from, to)

    // ---------- Gastos recurrentes ----------

    /** Crea o actualiza un recurrente y apunta los cargos que ya tocaban. */
    suspend fun saveRecurring(recurring: RecurringExpense) {
        if (recurring.id == 0L) recurringDao.insert(recurring) else recurringDao.update(recurring)
        generateDueRecurring()
    }

    suspend fun deleteRecurring(recurring: RecurringExpense) = recurringDao.delete(recurring)

    /** Pausa o reactiva. Al reactivar no se apuntan los meses en pausa: salta al próximo cargo. */
    suspend fun setRecurringActive(recurring: RecurringExpense, active: Boolean) {
        val updated = if (active) {
            val today = LocalDate.now()
            val next = Recurrence.firstOnOrAfter(
                LocalDate.ofEpochDay(recurring.nextDueEpochDay), today, recurring.frequency, recurring.dayOfMonth
            )
            recurring.copy(active = true, nextDueEpochDay = next.toEpochDay())
        } else {
            recurring.copy(active = false)
        }
        recurringDao.update(updated)
        if (active) generateDueRecurring()
    }

    /**
     * Apunta como gasto cada cargo recurrente cuya fecha ya ha llegado.
     * Se llama al abrir la app y periódicamente en segundo plano. Devuelve cuántos ha apuntado.
     * Cada recurrente se procesa en una transacción: o se apuntan sus cargos y avanza la fecha, o nada.
     */
    suspend fun generateDueRecurring(): Int = recurringLock.withLock {
        ensureDefaults()
        val today = LocalDate.now()
        var created = 0
        for (r in recurringDao.getDue(today.toEpochDay())) {
            val charged = mutableListOf<Expense>()
            val updated = db.withTransaction {
                var next = LocalDate.ofEpochDay(r.nextDueEpochDay)
                var remaining = r.remainingCharges
                var guard = 0
                while (next <= today && guard < 120 && (remaining == null || remaining > 0)) {
                    val ts = next.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                    val expense = Expense(
                        amountCents = r.amountCents,
                        merchant = r.name,
                        note = r.note,
                        categoryId = r.categoryId,
                        timestamp = ts,
                        source = ExpenseSource.RECURRING,
                        isIncome = r.isIncome,
                        recurringId = r.id,
                    )
                    charged += expense.copy(id = expenseDao.insert(expense))
                    next = Recurrence.next(next, r.frequency, r.dayOfMonth)
                    remaining = remaining?.minus(1)
                    guard++
                }
                val u = r.copy(
                    nextDueEpochDay = next.toEpochDay(),
                    remainingCharges = remaining,
                    // Al terminar las cuotas, el recurrente se pausa solo.
                    active = remaining == null || remaining > 0,
                )
                recurringDao.update(u)
                u
            }
            created += charged.size
            val category = r.categoryId?.let { categoryDao.getById(it) }
            charged.lastOrNull()?.let { last ->
                if (dateOf(last.timestamp) == today) AppNotifications.recurringCharged(context, updated, category)
            }
            if (!r.isIncome && charged.isNotEmpty()) checkBudget(r.categoryId, System.currentTimeMillis())
        }
        created
    }

    /** Salta el próximo cargo sin apuntarlo (p. ej. un mes que no se paga). */
    suspend fun skipNextCharge(r: RecurringExpense) {
        val next = Recurrence.next(LocalDate.ofEpochDay(r.nextDueEpochDay), r.frequency, r.dayOfMonth)
        recurringDao.update(r.copy(nextDueEpochDay = next.toEpochDay()))
    }

    /** Apunta ya el próximo cargo (con fecha de hoy) y avanza la fecha al siguiente. */
    suspend fun chargeNow(r: RecurringExpense) {
        val today = LocalDate.now()
        val due = LocalDate.ofEpochDay(r.nextDueEpochDay)
        db.withTransaction {
            expenseDao.insert(
                Expense(
                    amountCents = r.amountCents, merchant = r.name, note = r.note, categoryId = r.categoryId,
                    timestamp = System.currentTimeMillis(), source = ExpenseSource.RECURRING,
                    isIncome = r.isIncome, recurringId = r.id,
                )
            )
            val remaining = r.remainingCharges?.minus(1)
            val next = if (due > today) Recurrence.next(due, r.frequency, r.dayOfMonth) else Recurrence.next(today, r.frequency, r.dayOfMonth)
            recurringDao.update(
                r.copy(nextDueEpochDay = next.toEpochDay(), remainingCharges = remaining, active = remaining == null || remaining > 0)
            )
        }
        if (!r.isIncome) checkBudget(r.categoryId, System.currentTimeMillis())
    }

    suspend fun recurringHistory(id: Long): RecurringHistory = expenseDao.recurringHistory(id)

    /** Avisa el día antes de los cargos que lo tienen activado (una vez por cargo). */
    suspend fun remindUpcoming() {
        val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
        val prefs = context.getSharedPreferences("recurring_reminders", Context.MODE_PRIVATE)
        // Renovaciones anuales: aviso una semana antes, para poder darse de baja a tiempo
        com.midinero.control.domain.Renewals.dueIn(recurringDao.getAll(), 7).forEach { r ->
            val key = "renewal_${r.id}_${r.nextDueEpochDay}"
            if (!prefs.getBoolean(key, false)) {
                prefs.edit { putBoolean(key, true) }
                AppNotifications.renewalReminder(context, r)
            }
        }
        recurringDao.getAll()
            .filter { it.active && it.remindDayBefore && it.nextDueEpochDay == tomorrow }
            .forEach { r ->
                val key = "remind_${r.id}_${r.nextDueEpochDay}"
                if (!prefs.getBoolean(key, false)) {
                    prefs.edit { putBoolean(key, true) }
                    AppNotifications.recurringReminder(context, r)
                }
            }
    }
}

