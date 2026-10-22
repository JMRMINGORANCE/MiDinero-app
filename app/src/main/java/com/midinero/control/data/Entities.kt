package com.midinero.control.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Categoría de gasto. [monthlyLimitCents] es null cuando no tiene límite. */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val monthlyLimitCents: Long? = null,
    val isDefault: Boolean = false,
    /** Categoría de ingresos (nómina...). No tiene límite ni cuenta como gasto. (v2) */
    @ColumnInfo(defaultValue = "0") val isIncome: Boolean = false,
    /** Oculta: no se ofrece al elegir categoría ni para clasificar sola, pero conserva su historial. (v11) */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    /** Posición en las listas; a igual posición, por nombre. (v11) */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /** Panel al que van sus gastos (FUEL, ELECTRICITY, GAS, WATER, CAR; ver TrackerKind); null = ninguno. (v14) */
    val kind: String? = null,
)

/** Origen de un movimiento. GOOGLE_PAY = detectado en una notificación (de cualquier app de pago). */
enum class ExpenseSource { MANUAL, GOOGLE_PAY, RECURRING, IMPORTED }

/** Un gasto. El importe se guarda en céntimos para evitar errores de redondeo. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    // merchant y recurringId: sugerencias, duplicados e historial de cargos fijos (v5)
    indices = [Index("categoryId"), Index("timestamp"), Index("merchant"), Index("recurringId"), Index("tripId"), Index("accountId")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val merchant: String,
    val note: String = "",
    val categoryId: Long? = null,
    val timestamp: Long,
    val source: ExpenseSource = ExpenseSource.MANUAL,
    /** Texto original de la notificación (solo para gastos detectados). */
    val rawText: String = "",
    /** true si es un ingreso en lugar de un gasto. (v2) */
    @ColumnInfo(defaultValue = "0") val isIncome: Boolean = false,
    /** Recurrente que lo generó, si viene de uno. (v2) */
    val recurringId: Long? = null,
    /** Moneda original si se pagó en otra moneda (USD, GBP...); [amountCents] va siempre en euros. (v7) */
    val originalCurrency: String? = null,
    /** Importe en la moneda original, en céntimos. (v7) */
    val originalAmountCents: Long? = null,
    /** Viaje o evento al que pertenece, si alguno. (v8) */
    val tripId: Long? = null,
    /** Nombre del fichero con la foto del ticket (en la carpeta privada de la app). (v9) */
    val receipt: String? = null,
    /** Cuenta de la que sale (o a la que entra) el dinero; null = ninguna. (v13) */
    val accountId: Long? = null,
    /** Datos extra según el panel de su categoría: litros, km, kWh, m³… (ver ExpenseDetails). (v14) */
    val details: String? = null,
) {
    /** Devolución de una compra: se guarda en negativo para que reste del gasto de su categoría y su mes. */
    val isRefund: Boolean get() = !isIncome && amountCents < 0
}

/** Regla aprendida: cuando aparezca este comercio, asignar esta categoría. */
@Entity(
    tableName = "merchant_rules",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("categoryId")],
)
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
)

/** Gasto total de una categoría en un periodo. */
data class CategoryTotal(
    val categoryId: Long?,
    val totalCents: Long,
)

/** Gasto total de un viaje o evento. */
data class TripTotal(
    val tripId: Long,
    val totalCents: Long,
)

/** Cuántas veces se ha apuntado un recurrente y cuánto suma. */
data class RecurringHistory(val count: Int, val totalCents: Long)

/** Comercio usado antes, para autocompletar. [categoryId] es la última categoría que tuvo. */
data class MerchantSuggestion(
    val merchant: String,
    val categoryId: Long?,
    val uses: Int,
    val lastUsed: Long,
)

enum class Frequency { WEEKLY, MONTHLY, QUARTERLY, YEARLY }

/**
 * Gasto recurrente (alquiler, suscripciones...). Cada vez que llega [nextDueEpochDay]
 * se crea un gasto automáticamente y la fecha avanza un mes o un año.
 */
@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("categoryId")],
)
data class RecurringExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val categoryId: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    /** Día del mes del cargo (1-31). Si el mes es más corto, se usa el último día. */
    val dayOfMonth: Int,
    /** Fecha del próximo cargo (LocalDate.toEpochDay()). */
    val nextDueEpochDay: Long,
    val active: Boolean = true,
    val note: String = "",
    /** Ingreso recurrente (nómina...) en lugar de gasto. (v2) */
    @ColumnInfo(defaultValue = "0") val isIncome: Boolean = false,
    /** Cargos que quedan (financiaciones, cuotas); null = sin fin. (v2) */
    val remainingCharges: Int? = null,
    /** Avisar el día antes del cargo. (v2) */
    @ColumnInfo(defaultValue = "0") val remindDayBefore: Boolean = false,
    /**
     * Semanal en varios días: bit 0 = lunes … bit 6 = domingo (por ejemplo, un sueldo cada L, X, V y D).
     * 0 = una vez por semana, el mismo día que el primer cargo. (v16)
     */
    @ColumnInfo(defaultValue = "0") val weekdays: Int = 0,
)

/** Meta de ahorro (viaje, colchón, coche...). Lo ahorrado es la suma de sus aportaciones. (v3) */
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎯",
    val targetCents: Long,
    /** Fecha límite (LocalDate.toEpochDay()), opcional. */
    val deadlineEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

/** Aportación (o retirada, si es negativa) a una meta. (v3) */
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoal::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("goalId")],
)
data class GoalContribution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountCents: Long,
    val timestamp: Long,
    val note: String = "",
)

enum class RuleMatch { CONTAINS, STARTS_WITH, EXACT }

/** Regla personalizada de categorización y renombrado de comercios. (v4) */
@Entity(
    tableName = "custom_rules",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("categoryId")],
)
data class CustomRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val match: RuleMatch = RuleMatch.CONTAINS,
    /** Categoría a asignar; null = solo renombrar. */
    val categoryId: Long? = null,
    /** Nombre con el que guardar el comercio ("AMZN Mktp" -> "Amazon"); null = no renombrar. */
    val renameTo: String? = null,
    val priority: Int = 0,
)

/**
 * Dinero pendiente con otra persona. [amountCents] > 0: te debe; < 0: le debes. (v6)
 * Se crea al compartir un gasto (solo cuenta tu parte) o a mano.
 */
@Entity(tableName = "debts", indices = [Index("person")])
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val amountCents: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Cuándo se saldó; null = pendiente. */
    val settledAt: Long? = null,
    /** Gasto del que viene, si se creó al compartir uno. */
    val expenseId: Long? = null,
)

/**
 * Viaje o evento con su propio presupuesto (vacaciones, una boda, la reforma...). (v8)
 * Las fechas son días (epochDay). Con [autoAssign], lo que se apunte entre esas fechas se asigna solo.
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✈️",
    val budgetCents: Long? = null,
    val startDay: Long,
    val endDay: Long? = null,
    val autoAssign: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Comercio añadido por ti, con su foto (opcional). (v12)
 * Los pagos cuyo texto contenga [name] o alguno de [aliases] se muestran con este nombre, su foto y, si la tiene, su categoría.
 * La foto se guarda dentro de la base de datos (pequeña, WebP) para que viaje con las copias de seguridad.
 */
@Entity(
    tableName = "custom_merchants",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index(value = ["name"], unique = true), Index("categoryId")],
)
data class CustomMerchant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Otros textos con los que aparece en los avisos del banco, separados por comas («BAR PACO SL, PACO TAPAS»). */
    val aliases: String = "",
    val categoryId: Long? = null,
    val photo: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    // ByteArray compara por referencia: se compara su contenido para que Compose y los tests vean cambios reales
    override fun equals(other: Any?): Boolean = other is CustomMerchant && id == other.id && name == other.name &&
        aliases == other.aliases && categoryId == other.categoryId && createdAt == other.createdAt &&
        (photo?.contentEquals(other.photo) ?: (other.photo == null))

    override fun hashCode(): Int = arrayOf(id, name, aliases, categoryId, createdAt, photo?.contentHashCode()).contentHashCode()
}

/**
 * Dónde está tu dinero: efectivo, la tarjeta del banco, Revolut, Bybit… (v13)
 * El saldo parte de [initialBalanceCents] en [balanceSince] y suma lo que se apunte en ella desde entonces.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "💳",
    val initialBalanceCents: Long = 0,
    val balanceSince: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

/** Paso de dinero de una cuenta a otra: no es gasto ni ingreso. (v13) */
@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["fromAccountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fromAccountId"), Index("toAccountId"), Index("timestamp")],
)
data class Transfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val timestamp: Long,
    val note: String = "",
)

/**
 * Coche (o moto) o vivienda: a él van sus repostajes, mantenimientos, ITV y seguro, o sus recibos de luz, agua y gas. (v14)
 * [kind]: "CAR" o "HOME". (v15)
 */
@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🚗",
    val plate: String = "",
    /** Avisar estos meses después del último cambio de aceite; null = no avisar. */
    val oilReminderMonths: Int? = 11,
    /** Fecha de la próxima ITV (epochDay); null = sin apuntar. */
    val itvDueEpochDay: Long? = null,
    /** Con cuántos días de antelación avisar de la ITV. */
    val itvNoticeDays: Int = 30,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "CAR") val kind: String = KIND_CAR,
) {
    val isHome: Boolean get() = kind == KIND_HOME

    companion object {
        const val KIND_CAR = "CAR"
        const val KIND_HOME = "HOME"
    }
}

/** Seguro (coche, hogar, vida…) con su fecha de renovación y los avisos para negociar o darse de baja. (v14) */
@Entity(
    tableName = "policies",
    foreignKeys = [
        ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("vehicleId")],
)
data class Policy(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** CAR, HOME, LIFE, HEALTH u OTHER. */
    val type: String = "CAR",
    val company: String = "",
    val vehicleId: Long? = null,
    /** Día en que vence la póliza (epochDay). */
    val renewalEpochDay: Long,
    /** Avisar estos días antes del vencimiento para pedir el precio de renovación. */
    val noticeDays: Int = 45,
    /** Días antes del vencimiento en que acaba el plazo para darse de baja. */
    val cancelDays: Int = 30,
    val note: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Número de póliza y teléfono de la compañía, a mano para dar un parte. (v15) */
    @ColumnInfo(defaultValue = "") val policyNumber: String = "",
    @ColumnInfo(defaultValue = "") val phone: String = "",
)

/** Precio de un año (o periodo) de un seguro: el historial muestra cuánto ha subido en cada renovación. (v14) */
@Entity(
    tableName = "policy_prices",
    foreignKeys = [
        ForeignKey(entity = Policy::class, parentColumns = ["id"], childColumns = ["policyId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("policyId")],
)
data class PolicyPrice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val policyId: Long,
    /** Desde cuándo vale este precio (epochDay). */
    val startEpochDay: Long,
    val amountCents: Long,
    val note: String = "",
)
