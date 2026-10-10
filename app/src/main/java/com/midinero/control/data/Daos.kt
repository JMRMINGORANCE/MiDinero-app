package com.midinero.control.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Category?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    // Lo que usa una categoría, para poder deshacer su borrado
    @Query("SELECT id FROM expenses WHERE categoryId = :id")
    suspend fun expenseIdsIn(id: Long): List<Long>

    @Query("SELECT id FROM recurring_expenses WHERE categoryId = :id")
    suspend fun recurringIdsIn(id: Long): List<Long>

    @Query("SELECT id FROM custom_rules WHERE categoryId = :id")
    suspend fun customRuleIdsIn(id: Long): List<Long>

    @Query("SELECT * FROM merchant_rules WHERE categoryId = :id")
    suspend fun merchantRulesIn(id: Long): List<MerchantRule>

    @Query("UPDATE expenses SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setExpensesCategory(ids: List<Long>, categoryId: Long)

    @Query("UPDATE recurring_expenses SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setRecurringCategory(ids: List<Long>, categoryId: Long)

    @Query("UPDATE custom_rules SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setCustomRulesCategory(ids: List<Long>, categoryId: Long)

    // Al borrar una categoría, lo que la usaba puede pasar a otra
    @Query("UPDATE expenses SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveExpenses(from: Long, to: Long)

    @Query("UPDATE recurring_expenses SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveRecurring(from: Long, to: Long)

    @Query("UPDATE custom_rules SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCustomRules(from: Long, to: Long)

    @Query("UPDATE merchant_rules SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveMerchantRules(from: Long, to: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE isIncome = 1")
    suspend fun countIncome(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE source = 'GOOGLE_PAY' AND timestamp BETWEEN :from AND :to")
    suspend fun detectedBetween(from: Long, to: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Expense>>

    @Query(
        "SELECT categoryId, SUM(amountCents) AS totalCents FROM expenses " +
            "WHERE isIncome = 0 AND timestamp >= :from AND timestamp < :to GROUP BY categoryId"
    )
    fun observeTotalsBetween(from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM expenses " +
            "WHERE isIncome = 0 AND categoryId = :categoryId AND timestamp >= :from AND timestamp < :to"
    )
    suspend fun totalForCategory(categoryId: Long, from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE isIncome = 0 AND timestamp >= :from AND timestamp < :to")
    suspend fun totalSpent(from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE isIncome = 0 AND timestamp >= :from AND timestamp < :to")
    fun observeTotalSpent(from: Long, to: Long): Flow<Long>

    /** Comercios más usados. Al haber un solo MAX(), SQLite toma categoryId de la fila más reciente. */
    @Query(
        "SELECT merchant, categoryId, COUNT(*) AS uses, MAX(timestamp) AS lastUsed FROM expenses " +
            "GROUP BY merchant COLLATE NOCASE ORDER BY uses DESC, lastUsed DESC LIMIT 300"
    )
    fun observeMerchants(): Flow<List<MerchantSuggestion>>

    @Insert
    suspend fun insert(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY timestamp")
    suspend fun getAll(): List<Expense>

    // Consultas acotadas, para no leer todo el historial cuando solo hace falta una parte
    @Query("SELECT * FROM expenses WHERE timestamp >= :from AND timestamp < :to")
    suspend fun getBetween(from: Long, to: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE isIncome = 0")
    suspend fun getSpending(): List<Expense>

    @Query("SELECT * FROM expenses WHERE isIncome = 0 AND categoryId IS NULL AND merchant != ''")
    suspend fun getUncategorizedSpending(): List<Expense>

    @Query("SELECT * FROM expenses WHERE source = 'GOOGLE_PAY' AND LOWER(merchant) LIKE '%revolut%'")
    suspend fun getDetectedRevolut(): List<Expense>

    @Query("DELETE FROM expenses WHERE rawText = :rawText")
    suspend fun deleteByRawText(rawText: String)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    /** Pagos de Google Pay con el mismo importe (Wallet y Play Services pueden avisar del mismo pago). */
    @Query(
        "SELECT COUNT(*) FROM expenses WHERE amountCents = :amountCents AND source = 'GOOGLE_PAY' " +
            "AND timestamp >= :since"
    )
    suspend fun countRecentWalletPayments(amountCents: Long, since: Long): Int

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(amountCents), 0) AS totalCents FROM expenses WHERE recurringId = :id")
    suspend fun recurringHistory(id: Long): RecurringHistory

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    /** Compras (gastos en positivo) entre dos instantes, de la más reciente a la más antigua. */
    @Query("SELECT * FROM expenses WHERE isIncome = 0 AND amountCents > 0 AND timestamp >= :from AND timestamp <= :to ORDER BY timestamp DESC")
    suspend fun purchasesBetween(from: Long, to: Long): List<Expense>

    /** Categorías más usadas desde [since], para ofrecerlas como acción rápida. */
    @Query(
        "SELECT categoryId FROM expenses WHERE categoryId IS NOT NULL AND isIncome = 0 AND timestamp >= :since " +
            "GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT :limit"
    )
    suspend fun topCategoryIds(since: Long, limit: Int): List<Long>

    @Query("SELECT * FROM expenses WHERE categoryId IS NULL")
    suspend fun getUncategorized(): List<Expense>

    @Query("UPDATE expenses SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long)

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY timestamp DESC")
    fun observeByTrip(tripId: Long): Flow<List<Expense>>

    /** Gasto (sin ingresos) de cada viaje. */
    @Query("SELECT tripId, COALESCE(SUM(amountCents), 0) AS totalCents FROM expenses WHERE tripId IS NOT NULL AND isIncome = 0 GROUP BY tripId")
    fun observeTripTotals(): Flow<List<TripTotal>>

    /** Asigna al viaje los gastos entre esas fechas que aún no tienen viaje. Devuelve cuántos. */
    @Query("UPDATE expenses SET tripId = :tripId WHERE tripId IS NULL AND isIncome = 0 AND timestamp >= :from AND timestamp < :to")
    suspend fun assignTripBetween(tripId: Long, from: Long, to: Long): Int

    @Query("SELECT receipt FROM expenses WHERE receipt IS NOT NULL")
    suspend fun receiptNames(): List<String>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE tripId = :tripId AND isIncome = 0")
    suspend fun tripTotal(tripId: Long): Long

    @Query("UPDATE expenses SET tripId = NULL WHERE tripId = :tripId")
    suspend fun clearTrip(tripId: Long)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key LIMIT 1")
    suspend fun get(key: String): MerchantRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)

    @Query("SELECT COUNT(*) FROM merchant_rules")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM merchant_rules")
    suspend fun clear()

    @Query("SELECT * FROM merchant_rules ORDER BY merchantKey")
    fun observeAll(): Flow<List<MerchantRule>>

    @Query("SELECT * FROM merchant_rules")
    suspend fun getAll(): List<MerchantRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MerchantRule>)

    @Delete
    suspend fun delete(rule: MerchantRule)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecurringExpense>>

    @Query("SELECT * FROM recurring_expenses WHERE active = 1 AND nextDueEpochDay <= :today")
    suspend fun getDue(today: Long): List<RecurringExpense>

    @Insert
    suspend fun insert(recurring: RecurringExpense): Long

    @Query("SELECT * FROM recurring_expenses")
    suspend fun getAll(): List<RecurringExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recurring: List<RecurringExpense>)

    @Query("DELETE FROM recurring_expenses")
    suspend fun deleteAll()

    @Update
    suspend fun update(recurring: RecurringExpense)

    @Delete
    suspend fun delete(recurring: RecurringExpense)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY archived, createdAt")
    fun observeGoals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM goal_contributions ORDER BY timestamp DESC")
    fun observeContributions(): Flow<List<GoalContribution>>

    @Query("SELECT * FROM savings_goals")
    suspend fun getGoals(): List<SavingsGoal>

    @Query("SELECT * FROM goal_contributions")
    suspend fun getContributions(): List<GoalContribution>

    @Insert
    suspend fun insert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)

    @Insert
    suspend fun insert(contribution: GoalContribution): Long

    @Delete
    suspend fun delete(contribution: GoalContribution)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<SavingsGoal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributions(contributions: List<GoalContribution>)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllGoals()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM custom_rules ORDER BY priority DESC, pattern COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomRule>>

    @Query("SELECT * FROM custom_rules")
    suspend fun getAll(): List<CustomRule>

    @Insert
    suspend fun insert(rule: CustomRule): Long

    @Update
    suspend fun update(rule: CustomRule)

    @Delete
    suspend fun delete(rule: CustomRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<CustomRule>)

    @Query("DELETE FROM custom_rules")
    suspend fun deleteAll()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY settledAt IS NOT NULL, createdAt DESC")
    fun observeAll(): Flow<List<Debt>>

    @Query("SELECT * FROM debts")
    suspend fun getAll(): List<Debt>

    @Insert
    suspend fun insert(debt: Debt): Long

    @Insert
    suspend fun insertAll(debts: List<Debt>)

    @Update
    suspend fun update(debt: Debt)

    @Delete
    suspend fun delete(debt: Debt)

    @Query("UPDATE debts SET settledAt = :at WHERE settledAt IS NULL AND LOWER(TRIM(person)) = LOWER(TRIM(:person))")
    suspend fun settlePerson(person: String, at: Long)

    @Query("DELETE FROM debts")
    suspend fun deleteAll()
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDay DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips")
    suspend fun getAll(): List<Trip>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): Trip?

    /** Viaje que recoge solo los gastos de ese día (el más reciente si se solapan). */
    @Query(
        "SELECT * FROM trips WHERE autoAssign = 1 AND startDay <= :day AND (endDay IS NULL OR endDay >= :day) " +
            "ORDER BY startDay DESC LIMIT 1"
    )
    suspend fun activeOn(day: Long): Trip?

    @Insert
    suspend fun insert(trip: Trip): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<Trip>)

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}

