package com.midinero.control.data

import com.midinero.control.domain.tr
import org.json.JSONArray
import org.json.JSONObject

/** Ajustes que viajan en la copia (los de aspecto y seguridad se quedan en cada móvil). */
data class BackupSettings(
    val monthlyBudgetCents: Long,
    val alertThreshold: Int,
    val periodStartDay: Int,
    val weeklySummary: Boolean,
    val enabledPaymentApps: Set<String>,
)

/** Contenido completo de una copia de seguridad. */
data class BackupData(
    val categories: List<Category>,
    val expenses: List<Expense>,
    val rules: List<MerchantRule>,
    val recurring: List<RecurringExpense>,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: BackupSettings? = null,
    val goals: List<SavingsGoal> = emptyList(),
    val contributions: List<GoalContribution> = emptyList(),
    val customRules: List<CustomRule> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val trips: List<Trip> = emptyList(),
)

/**
 * Copia de seguridad en JSON legible. [FORMAT] sube si cambia la estructura;
 * al leer, los campos que falten toman su valor por defecto (copias antiguas siguen valiendo).
 */
object Backup {
    const val FORMAT = 1
    private const val APP = "MiDinero"

    fun toJson(data: BackupData): String = JSONObject()
        .put("app", APP)
        .put("format", FORMAT)
        .put("exportedAt", data.exportedAt)
        .put("categories", JSONArray().apply {
            data.categories.forEach { c ->
                put(
                    JSONObject()
                        .put("id", c.id).put("name", c.name).put("emoji", c.emoji).put("colorHex", c.colorHex)
                        .put("monthlyLimitCents", c.monthlyLimitCents ?: JSONObject.NULL)
                        .put("isDefault", c.isDefault).put("isIncome", c.isIncome)
                        .put("hidden", c.hidden).put("sortOrder", c.sortOrder)
                )
            }
        })
        .put("expenses", JSONArray().apply {
            data.expenses.forEach { e ->
                put(
                    JSONObject()
                        .put("id", e.id).put("amountCents", e.amountCents).put("merchant", e.merchant).put("note", e.note)
                        .put("categoryId", e.categoryId ?: JSONObject.NULL).put("timestamp", e.timestamp)
                        .put("source", e.source.name).put("rawText", e.rawText).put("isIncome", e.isIncome)
                        .put("recurringId", e.recurringId ?: JSONObject.NULL)
                        .put("originalCurrency", e.originalCurrency ?: JSONObject.NULL)
                        .put("originalAmountCents", e.originalAmountCents ?: JSONObject.NULL)
                        .put("tripId", e.tripId ?: JSONObject.NULL)
                        .put("receipt", e.receipt ?: JSONObject.NULL)
                )
            }
        })
        .put("rules", JSONArray().apply {
            data.rules.forEach { r -> put(JSONObject().put("merchantKey", r.merchantKey).put("categoryId", r.categoryId)) }
        })
        .put("recurring", JSONArray().apply {
            data.recurring.forEach { r ->
                put(
                    JSONObject()
                        .put("id", r.id).put("name", r.name).put("amountCents", r.amountCents)
                        .put("categoryId", r.categoryId ?: JSONObject.NULL).put("frequency", r.frequency.name)
                        .put("dayOfMonth", r.dayOfMonth).put("nextDueEpochDay", r.nextDueEpochDay)
                        .put("active", r.active).put("note", r.note).put("isIncome", r.isIncome)
                        .put("remainingCharges", r.remainingCharges ?: JSONObject.NULL)
                        .put("remindDayBefore", r.remindDayBefore)
                )
            }
        })
        .put("goals", JSONArray().apply {
            data.goals.forEach { g ->
                put(
                    JSONObject()
                        .put("id", g.id).put("name", g.name).put("emoji", g.emoji).put("targetCents", g.targetCents)
                        .put("deadlineEpochDay", g.deadlineEpochDay ?: JSONObject.NULL)
                        .put("createdAt", g.createdAt).put("archived", g.archived)
                )
            }
        })
        .put("contributions", JSONArray().apply {
            data.contributions.forEach { c ->
                put(
                    JSONObject()
                        .put("id", c.id).put("goalId", c.goalId).put("amountCents", c.amountCents)
                        .put("timestamp", c.timestamp).put("note", c.note)
                )
            }
        })
        .put("customRules", JSONArray().apply {
            data.customRules.forEach { r ->
                put(
                    JSONObject()
                        .put("id", r.id).put("pattern", r.pattern).put("match", r.match.name)
                        .put("categoryId", r.categoryId ?: JSONObject.NULL)
                        .put("renameTo", r.renameTo ?: JSONObject.NULL).put("priority", r.priority)
                )
            }
        })
        .put("debts", JSONArray().apply {
            data.debts.forEach { d ->
                put(
                    JSONObject()
                        .put("id", d.id).put("person", d.person).put("amountCents", d.amountCents).put("note", d.note)
                        .put("createdAt", d.createdAt).put("settledAt", d.settledAt ?: JSONObject.NULL)
                        .put("expenseId", d.expenseId ?: JSONObject.NULL)
                )
            }
        })
        .put("trips", JSONArray().apply {
            data.trips.forEach { t ->
                put(
                    JSONObject()
                        .put("id", t.id).put("name", t.name).put("emoji", t.emoji)
                        .put("budgetCents", t.budgetCents ?: JSONObject.NULL)
                        .put("startDay", t.startDay).put("endDay", t.endDay ?: JSONObject.NULL)
                        .put("autoAssign", t.autoAssign).put("createdAt", t.createdAt)
                )
            }
        })
        .apply {
            data.settings?.let { st ->
                put(
                    "settings",
                    JSONObject()
                        .put("monthlyBudgetCents", st.monthlyBudgetCents)
                        .put("alertThreshold", st.alertThreshold)
                        .put("periodStartDay", st.periodStartDay)
                        .put("weeklySummary", st.weeklySummary)
                        .put("enabledPaymentApps", JSONArray(st.enabledPaymentApps.sorted())),
                )
            }
        }
        .toString(2)

    /** Texto del fichero de copia: cifrado con [password] si hay una, y si no en JSON legible. */
    fun toFile(data: BackupData, password: String?): String =
        toJson(data).let { if (password.isNullOrEmpty()) it else BackupCrypto.encrypt(it, password) }

    /**
     * Lee el fichero de una copia, cifrada o no. Lanza [BackupPasswordException] si está cifrada y [password]
     * falta o no es la correcta, e [IllegalArgumentException] si no es una copia de MiDinero.
     */
    fun fromFile(text: String, password: String?): BackupData =
        fromJson(if (BackupCrypto.isEncrypted(text)) BackupCrypto.decrypt(text, password) else text)

    /** Lee una copia. Lanza [IllegalArgumentException] si el fichero no es una copia de MiDinero. */
    fun fromJson(text: String): BackupData {
        val root = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException(tr("El fichero no es un JSON válido", "The file isn't valid JSON")) }
        require(root.optString("app") == APP) { tr("El fichero no es una copia de MiDinero", "The file isn't a MiDinero backup") }
        require(root.optInt("format", 0) in 1..FORMAT) { tr("La copia es de una versión más nueva de MiDinero", "The backup is from a newer version of MiDinero") }

        val categories = root.optJSONArray("categories").objects().map { o ->
            Category(
                id = o.getLong("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "💶"),
                colorHex = o.optString("colorHex", "#495057"),
                monthlyLimitCents = o.optLongOrNull("monthlyLimitCents"),
                isDefault = o.optBoolean("isDefault"),
                isIncome = o.optBoolean("isIncome"),
                hidden = o.optBoolean("hidden"),
                sortOrder = o.optInt("sortOrder"),
            )
        }
        val categoryIds = categories.map { it.id }.toSet()
        val expenses = root.optJSONArray("expenses").objects().map { o ->
            Expense(
                id = o.getLong("id"),
                amountCents = o.getLong("amountCents"),
                merchant = o.optString("merchant"),
                note = o.optString("note"),
                categoryId = o.optLongOrNull("categoryId")?.takeIf { it in categoryIds },
                timestamp = o.getLong("timestamp"),
                source = runCatching { ExpenseSource.valueOf(o.optString("source")) }.getOrDefault(ExpenseSource.MANUAL),
                rawText = o.optString("rawText"),
                isIncome = o.optBoolean("isIncome"),
                recurringId = o.optLongOrNull("recurringId"),
                originalCurrency = if (o.isNull("originalCurrency")) null else o.optString("originalCurrency").ifBlank { null },
                originalAmountCents = o.optLongOrNull("originalAmountCents"),
                tripId = o.optLongOrNull("tripId"),
                receipt = if (o.isNull("receipt")) null else o.optString("receipt").ifBlank { null },
            )
        }
        val rules = root.optJSONArray("rules").objects()
            .map { o -> MerchantRule(o.getString("merchantKey"), o.getLong("categoryId")) }
            .filter { it.categoryId in categoryIds }
        val recurring = root.optJSONArray("recurring").objects().map { o ->
            RecurringExpense(
                id = o.getLong("id"),
                name = o.getString("name"),
                amountCents = o.getLong("amountCents"),
                categoryId = o.optLongOrNull("categoryId")?.takeIf { it in categoryIds },
                frequency = runCatching { Frequency.valueOf(o.optString("frequency")) }.getOrDefault(Frequency.MONTHLY),
                dayOfMonth = o.optInt("dayOfMonth", 1),
                nextDueEpochDay = o.getLong("nextDueEpochDay"),
                active = o.optBoolean("active", true),
                note = o.optString("note"),
                isIncome = o.optBoolean("isIncome"),
                remainingCharges = o.optLongOrNull("remainingCharges")?.toInt(),
                remindDayBefore = o.optBoolean("remindDayBefore"),
            )
        }
        val settings = root.optJSONObject("settings")?.let { o ->
            BackupSettings(
                monthlyBudgetCents = o.optLong("monthlyBudgetCents"),
                alertThreshold = o.optInt("alertThreshold", 80),
                periodStartDay = o.optInt("periodStartDay", 1).coerceIn(1, 28),
                weeklySummary = o.optBoolean("weeklySummary", true),
                enabledPaymentApps = o.optJSONArray("enabledPaymentApps")
                    ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
                    ?: com.midinero.control.domain.PaymentApps.defaultEnabled,
            )
        }
        val goals = root.optJSONArray("goals").objects().map { o ->
            SavingsGoal(
                id = o.getLong("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "🎯"),
                targetCents = o.getLong("targetCents"),
                deadlineEpochDay = o.optLongOrNull("deadlineEpochDay"),
                createdAt = o.optLong("createdAt"),
                archived = o.optBoolean("archived"),
            )
        }
        val goalIds = goals.map { it.id }.toSet()
        val contributions = root.optJSONArray("contributions").objects().map { o ->
            GoalContribution(
                id = o.getLong("id"),
                goalId = o.getLong("goalId"),
                amountCents = o.getLong("amountCents"),
                timestamp = o.getLong("timestamp"),
                note = o.optString("note"),
            )
        }.filter { it.goalId in goalIds }
        val customRules = root.optJSONArray("customRules").objects().map { o ->
            CustomRule(
                id = o.getLong("id"),
                pattern = o.getString("pattern"),
                match = runCatching { RuleMatch.valueOf(o.optString("match")) }.getOrDefault(RuleMatch.CONTAINS),
                categoryId = o.optLongOrNull("categoryId")?.takeIf { it in categoryIds },
                renameTo = if (o.isNull("renameTo")) null else o.optString("renameTo"),
                priority = o.optInt("priority"),
            )
        }
        val debts = root.optJSONArray("debts").objects().map { o ->
            Debt(
                id = o.getLong("id"),
                person = o.getString("person"),
                amountCents = o.getLong("amountCents"),
                note = o.optString("note"),
                createdAt = o.optLong("createdAt"),
                settledAt = o.optLongOrNull("settledAt"),
                expenseId = o.optLongOrNull("expenseId"),
            )
        }
        val trips = root.optJSONArray("trips").objects().map { o ->
            Trip(
                id = o.getLong("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji").ifBlank { "✈️" },
                budgetCents = o.optLongOrNull("budgetCents"),
                startDay = o.getLong("startDay"),
                endDay = o.optLongOrNull("endDay"),
                autoAssign = o.optBoolean("autoAssign", true),
                createdAt = o.optLong("createdAt"),
            )
        }
        val tripIds = trips.map { it.id }.toSet()
        return BackupData(
            categories, expenses.map { if (it.tripId != null && it.tripId !in tripIds) it.copy(tripId = null) else it },
            rules, recurring, root.optLong("exportedAt"), settings, goals, contributions, customRules, debts, trips,
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
}
