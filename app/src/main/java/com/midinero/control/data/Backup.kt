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
    /** Retos de ahorro (JSON de AppPrefs); null en copias antiguas. */
    val challengesJson: String? = null,
    /** Moneda principal; null en copias antiguas (euros). */
    val mainCurrency: String? = null,
    /** Método de pago por defecto y el de cada app de pago. */
    val defaultPaymentMethod: Long? = null,
    val paymentMethodApps: Map<String, Long> = emptyMap(),
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
    val customMerchants: List<CustomMerchant> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val transfers: List<Transfer> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val policies: List<Policy> = emptyList(),
    val policyPrices: List<PolicyPrice> = emptyList(),
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
                        .put("kind", c.kind ?: JSONObject.NULL)
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
                        .put("accountId", e.accountId ?: JSONObject.NULL)
                        .put("details", e.details ?: JSONObject.NULL)
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
        .put("accounts", JSONArray().apply {
            data.accounts.forEach { a ->
                put(
                    JSONObject().put("id", a.id).put("name", a.name).put("emoji", a.emoji)
                        .put("initialBalanceCents", a.initialBalanceCents).put("balanceSince", a.balanceSince)
                        .put("archived", a.archived).put("sortOrder", a.sortOrder)
                )
            }
        })
        .put("transfers", JSONArray().apply {
            data.transfers.forEach { t ->
                put(
                    JSONObject().put("id", t.id).put("fromAccountId", t.fromAccountId).put("toAccountId", t.toAccountId)
                        .put("amountCents", t.amountCents).put("timestamp", t.timestamp).put("note", t.note)
                )
            }
        })
        .put("vehicles", JSONArray().apply {
            data.vehicles.forEach { v ->
                put(
                    JSONObject().put("id", v.id).put("name", v.name).put("emoji", v.emoji).put("plate", v.plate)
                        .put("oilReminderMonths", v.oilReminderMonths ?: JSONObject.NULL)
                        .put("itvDueEpochDay", v.itvDueEpochDay ?: JSONObject.NULL)
                        .put("itvNoticeDays", v.itvNoticeDays).put("archived", v.archived).put("createdAt", v.createdAt)
                        .put("kind", v.kind)
                )
            }
        })
        .put("policies", JSONArray().apply {
            data.policies.forEach { p ->
                put(
                    JSONObject().put("id", p.id).put("name", p.name).put("type", p.type).put("company", p.company)
                        .put("vehicleId", p.vehicleId ?: JSONObject.NULL).put("renewalEpochDay", p.renewalEpochDay)
                        .put("noticeDays", p.noticeDays).put("cancelDays", p.cancelDays).put("note", p.note)
                        .put("archived", p.archived).put("createdAt", p.createdAt)
                        .put("policyNumber", p.policyNumber).put("phone", p.phone)
                )
            }
        })
        .put("policyPrices", JSONArray().apply {
            data.policyPrices.forEach { p ->
                put(
                    JSONObject().put("id", p.id).put("policyId", p.policyId).put("startEpochDay", p.startEpochDay)
                        .put("amountCents", p.amountCents).put("note", p.note)
                )
            }
        })
        .put("customMerchants", JSONArray().apply {
            data.customMerchants.forEach { m ->
                put(
                    JSONObject()
                        .put("id", m.id).put("name", m.name).put("aliases", m.aliases)
                        .put("categoryId", m.categoryId ?: JSONObject.NULL).put("createdAt", m.createdAt)
                        .put("photo", m.photo?.let { java.util.Base64.getEncoder().encodeToString(it) } ?: JSONObject.NULL)
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
                        .put("enabledPaymentApps", JSONArray(st.enabledPaymentApps.sorted()))
                        .apply { st.challengesJson?.let { put("challenges", JSONArray(it)) } }
                        .apply { st.mainCurrency?.let { put("mainCurrency", it) } }
                        .apply { st.defaultPaymentMethod?.let { put("defaultPaymentMethod", it) } }
                        .apply { if (st.paymentMethodApps.isNotEmpty()) put("paymentMethodApps", JSONObject(st.paymentMethodApps as Map<*, *>)) },
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
                kind = if (o.isNull("kind")) null else o.optString("kind").ifBlank { null },
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
                accountId = o.optLongOrNull("accountId"),
                details = if (o.isNull("details")) null else o.optString("details").ifBlank { null },
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
                challengesJson = o.optJSONArray("challenges")?.toString(),
                mainCurrency = o.optString("mainCurrency").takeIf { it in com.midinero.control.domain.Currencies.mainChoices },
                defaultPaymentMethod = o.optLongOrNull("defaultPaymentMethod"),
                paymentMethodApps = o.optJSONObject("paymentMethodApps")?.let { m -> m.keys().asSequence().associateWith { m.getLong(it) } }.orEmpty(),
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
        val accounts = root.optJSONArray("accounts").objects().map { o ->
            Account(
                id = o.getLong("id"), name = o.getString("name"), emoji = o.optString("emoji").ifBlank { "💳" },
                initialBalanceCents = o.optLong("initialBalanceCents"), balanceSince = o.optLong("balanceSince"),
                archived = o.optBoolean("archived"), sortOrder = o.optInt("sortOrder"),
            )
        }
        val accountIds = accounts.map { it.id }.toSet()
        val transfers = root.optJSONArray("transfers").objects().map { o ->
            Transfer(
                id = o.getLong("id"), fromAccountId = o.getLong("fromAccountId"), toAccountId = o.getLong("toAccountId"),
                amountCents = o.getLong("amountCents"), timestamp = o.getLong("timestamp"), note = o.optString("note"),
            )
        }.filter { it.fromAccountId in accountIds && it.toAccountId in accountIds }
        val customMerchants = root.optJSONArray("customMerchants").objects().mapNotNull { o ->
            val name = o.optString("name").trim().ifBlank { return@mapNotNull null }
            CustomMerchant(
                id = o.getLong("id"),
                name = name,
                aliases = o.optString("aliases"),
                categoryId = o.optLongOrNull("categoryId")?.takeIf { it in categoryIds },
                photo = if (o.isNull("photo")) null else runCatching { java.util.Base64.getDecoder().decode(o.getString("photo")) }.getOrNull(),
                createdAt = o.optLong("createdAt"),
            )
        }.distinctBy { it.name.lowercase() }
        val vehicles = root.optJSONArray("vehicles").objects().map { o ->
            Vehicle(
                id = o.getLong("id"), name = o.getString("name"), emoji = o.optString("emoji").ifBlank { "🚗" },
                plate = o.optString("plate"), oilReminderMonths = o.optLongOrNull("oilReminderMonths")?.toInt(),
                itvDueEpochDay = o.optLongOrNull("itvDueEpochDay"), itvNoticeDays = o.optInt("itvNoticeDays", 30),
                archived = o.optBoolean("archived"), createdAt = o.optLong("createdAt"),
                kind = if (o.optString("kind") == Vehicle.KIND_HOME) Vehicle.KIND_HOME else Vehicle.KIND_CAR,
            )
        }
        val vehicleIds = vehicles.map { it.id }.toSet()
        val policies = root.optJSONArray("policies").objects().map { o ->
            Policy(
                id = o.getLong("id"), name = o.getString("name"), type = o.optString("type").ifBlank { "OTHER" },
                company = o.optString("company"), vehicleId = o.optLongOrNull("vehicleId")?.takeIf { it in vehicleIds },
                renewalEpochDay = o.getLong("renewalEpochDay"), noticeDays = o.optInt("noticeDays", 45),
                cancelDays = o.optInt("cancelDays", 30), note = o.optString("note"),
                archived = o.optBoolean("archived"), createdAt = o.optLong("createdAt"),
                policyNumber = o.optString("policyNumber"), phone = o.optString("phone"),
            )
        }
        val policyIds = policies.map { it.id }.toSet()
        val policyPrices = root.optJSONArray("policyPrices").objects().map { o ->
            PolicyPrice(
                id = o.getLong("id"), policyId = o.getLong("policyId"), startEpochDay = o.getLong("startEpochDay"),
                amountCents = o.getLong("amountCents"), note = o.optString("note"),
            )
        }.filter { it.policyId in policyIds }
        return BackupData(
            categories,
            expenses.map { if (it.tripId != null && it.tripId !in tripIds) it.copy(tripId = null) else it }
                .map { if (it.accountId != null && it.accountId !in accountIds) it.copy(accountId = null) else it },
            rules, recurring, root.optLong("exportedAt"), settings, goals, contributions, customRules, debts, trips,
            customMerchants, accounts, transfers, vehicles, policies, policyPrices,
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
}
