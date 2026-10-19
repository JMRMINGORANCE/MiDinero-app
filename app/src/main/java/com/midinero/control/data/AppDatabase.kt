package com.midinero.control.data

import com.midinero.control.domain.CategoryNames
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun sourceToString(source: ExpenseSource): String = source.name

    @TypeConverter
    fun stringToSource(value: String): ExpenseSource =
        runCatching { ExpenseSource.valueOf(value) }.getOrDefault(ExpenseSource.MANUAL)

    @TypeConverter
    fun ruleMatchToString(match: RuleMatch): String = match.name

    @TypeConverter
    fun stringToRuleMatch(value: String): RuleMatch =
        runCatching { RuleMatch.valueOf(value) }.getOrDefault(RuleMatch.CONTAINS)

    @TypeConverter
    fun frequencyToString(frequency: Frequency): String = frequency.name

    @TypeConverter
    fun stringToFrequency(value: String): Frequency =
        runCatching { Frequency.valueOf(value) }.getOrDefault(Frequency.MONTHLY)
}

@Database(
    entities = [
        Category::class, Expense::class, MerchantRule::class, RecurringExpense::class,
        SavingsGoal::class, GoalContribution::class, CustomRule::class, Debt::class, Trip::class, IngestionReceipt::class,
        CustomMerchant::class, Account::class, Transfer::class, Vehicle::class, Policy::class, PolicyPrice::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun recurringDao(): RecurringDao
    abstract fun goalDao(): GoalDao
    abstract fun ruleDao(): RuleDao
    abstract fun debtDao(): DebtDao
    abstract fun ingestionDao(): IngestionDao
    abstract fun tripDao(): TripDao
    abstract fun customMerchantDao(): CustomMerchantDao
    abstract fun accountDao(): AccountDao
    abstract fun householdDao(): HouseholdDao

    companion object {
        /** Versión del esquema. Al subirla: añadir la migración en Migrations.kt y guardar el esquema en schemas/. */
        const val VERSION = 15

        const val FILE_NAME = "midinero.db"

        fun build(context: Context, name: String = FILE_NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}

/** Categorías que se crean la primera vez que se abre la app. */
object DefaultCategories {
    const val COMIDA_DOMICILIO = "Comida a domicilio"
    const val RESTAURANTES = "Restaurantes"
    const val SUPERMERCADO = "Supermercado"
    const val GASOLINA = "Gasolina"
    const val OCIO = "Ocio"
    const val TRANSPORTE = "Transporte"
    const val COMPRAS = "Compras"
    const val SUSCRIPCIONES = "Suscripciones"
    const val SALUD = "Salud"
    const val HOGAR = "Hogar"
    const val OTROS = "Otros"
    const val NOMINA = "Nómina"
    const val OTROS_INGRESOS = "Otros ingresos"

    val all: List<Category> get() = listOf(
        Category(name = CategoryNames.localized(COMIDA_DOMICILIO), emoji = "🛵", colorHex = "#E8590C", isDefault = true),
        Category(name = CategoryNames.localized(RESTAURANTES), emoji = "🍽️", colorHex = "#D6336C", isDefault = true),
        Category(name = CategoryNames.localized(SUPERMERCADO), emoji = "🛒", colorHex = "#2F9E44", isDefault = true),
        Category(name = CategoryNames.localized(GASOLINA), emoji = "⛽", colorHex = "#1971C2", isDefault = true, kind = "FUEL"),
        Category(name = CategoryNames.localized(OCIO), emoji = "🎉", colorHex = "#7048E8", isDefault = true),
        Category(name = CategoryNames.localized(TRANSPORTE), emoji = "🚇", colorHex = "#0C8599", isDefault = true),
        Category(name = CategoryNames.localized(COMPRAS), emoji = "🛍️", colorHex = "#AE3EC9", isDefault = true),
        Category(name = CategoryNames.localized(SUSCRIPCIONES), emoji = "📺", colorHex = "#5F3DC4", isDefault = true),
        Category(name = CategoryNames.localized(SALUD), emoji = "💊", colorHex = "#099268", isDefault = true),
        Category(name = CategoryNames.localized(HOGAR), emoji = "🏠", colorHex = "#E67700", isDefault = true),
        Category(name = CategoryNames.localized(OTROS), emoji = "📦", colorHex = "#495057", isDefault = true),
    )

    /** Categorías de ingresos (se añaden también a instalaciones que venían de la v1). */
    val income: List<Category> get() = listOf(
        Category(name = CategoryNames.localized(NOMINA), emoji = "💼", colorHex = "#2F9E44", isDefault = true, isIncome = true),
        Category(name = CategoryNames.localized(OTROS_INGRESOS), emoji = "💰", colorHex = "#0C8599", isDefault = true, isIncome = true),
    )
}

