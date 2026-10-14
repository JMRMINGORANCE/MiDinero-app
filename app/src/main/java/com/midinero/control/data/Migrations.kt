package com.midinero.control.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: ingresos, cuotas de recurrentes y recordatorios.
 * Solo añade columnas: no se pierde ningún dato. Probada en MigrationTest.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `isIncome` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `isIncome` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `recurringId` INTEGER")
        db.execSQL("ALTER TABLE `recurring_expenses` ADD COLUMN `isIncome` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `recurring_expenses` ADD COLUMN `remainingCharges` INTEGER")
        db.execSQL("ALTER TABLE `recurring_expenses` ADD COLUMN `remindDayBefore` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v2 -> v3: metas de ahorro y sus aportaciones. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `targetCents` INTEGER NOT NULL, `deadlineEpochDay` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`goalId` INTEGER NOT NULL, `amountCents` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")
    }
}

/** v3 -> v4: reglas personalizadas de categoría y alias de comercios. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`pattern` TEXT NOT NULL, `match` TEXT NOT NULL, `categoryId` INTEGER, `renameTo` TEXT, `priority` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_rules_categoryId` ON `custom_rules` (`categoryId`)")
    }
}

/** v4 -> v5: índices para que las búsquedas por comercio y por cargo fijo sean rápidas. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_merchant` ON `expenses` (`merchant`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_recurringId` ON `expenses` (`recurringId`)")
    }
}

/** v5 -> v6: cuentas con amigos (gastos compartidos y deudas). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `settledAt` INTEGER, `expenseId` INTEGER)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_person` ON `debts` (`person`)")
    }
}

/** v6 -> v7: gastos en otras monedas (se guarda el importe original además del de euros). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `originalCurrency` TEXT")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `originalAmountCents` INTEGER")
    }
}

/** v7 -> v8: viajes y eventos con presupuesto propio. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `budgetCents` INTEGER, `startDay` INTEGER NOT NULL, `endDay` INTEGER, " +
                "`autoAssign` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `tripId` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)")
    }
}

/** v8 -> v9: foto del ticket en cada gasto. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `receipt` TEXT")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ingestion_receipts` (`eventId` TEXT NOT NULL, PRIMARY KEY(`eventId`))")
    }
}

/** v10 -> v11: categorías ocultas y en el orden que elijas. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v11 -> v12: comercios propios con foto. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_merchants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`aliases` TEXT NOT NULL, `categoryId` INTEGER, `photo` BLOB, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_merchants_name` ON `custom_merchants` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_merchants_categoryId` ON `custom_merchants` (`categoryId`)")
    }
}

/** v12 -> v13: cuentas (efectivo, banco, Revolut…) con saldo y traspasos entre ellas. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `initialBalanceCents` INTEGER NOT NULL, `balanceSince` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromAccountId` INTEGER NOT NULL, " +
                "`toAccountId` INTEGER NOT NULL, `amountCents` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "FOREIGN KEY(`fromAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`toAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_fromAccountId` ON `transfers` (`fromAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_toAccountId` ON `transfers` (`toAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_timestamp` ON `transfers` (`timestamp`)")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `accountId` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_accountId` ON `expenses` (`accountId`)")
    }
}

/** v13 -> v14: paneles de casa y coche (datos extra de los gastos, coches, seguros y sus precios). */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `kind` TEXT")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `details` TEXT")
        // La categoría Gasolina de serie va directa al panel de combustible
        db.execSQL("UPDATE `categories` SET `kind` = 'FUEL' WHERE `isDefault` = 1 AND `isIncome` = 0 AND `emoji` = '⛽'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `plate` TEXT NOT NULL, `oilReminderMonths` INTEGER, `itvDueEpochDay` INTEGER, " +
                "`itvNoticeDays` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `policies` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `company` TEXT NOT NULL, `vehicleId` INTEGER, `renewalEpochDay` INTEGER NOT NULL, " +
                "`noticeDays` INTEGER NOT NULL, `cancelDays` INTEGER NOT NULL, `note` TEXT NOT NULL, `archived` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_policies_vehicleId` ON `policies` (`vehicleId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `policy_prices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `policyId` INTEGER NOT NULL, " +
                "`startEpochDay` INTEGER NOT NULL, `amountCents` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "FOREIGN KEY(`policyId`) REFERENCES `policies`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_policy_prices_policyId` ON `policy_prices` (`policyId`)")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
)

