package com.midinero.control.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Crea una base de datos exactamente como la v1 (con el SQL del esquema exportado por Room),
 * la abre con la app actual y comprueba que la migración conserva los datos.
 * Room valida al abrir que el esquema migrado coincide con el esperado; si no, el test falla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val schemaDir = File("schemas/com.midinero.control.data.AppDatabase")

    /** Crea una base de datos vacía con el esquema exacto de la versión [version]. */
    private fun createDatabase(name: String, version: Int): SQLiteDatabase {
        val schema = JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = e.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    private fun createV1Database(name: String) {
        val db = createDatabase(name, 1)
        db.execSQL("INSERT INTO categories (id, name, emoji, colorHex, monthlyLimitCents, isDefault) VALUES (1, 'Ocio', '🎉', '#7048E8', 10000, 1)")
        db.execSQL("INSERT INTO expenses (id, amountCents, merchant, note, categoryId, timestamp, source, rawText) VALUES (7, 1250, 'Cine', '', 1, 1700000000000, 'MANUAL', '')")
        db.execSQL("INSERT INTO recurring_expenses (id, name, amountCents, categoryId, frequency, dayOfMonth, nextDueEpochDay, active, note) VALUES (3, 'Netflix', 1299, 1, 'MONTHLY', 5, 20000, 1, '')")
        db.execSQL("INSERT INTO merchant_rules (merchantKey, categoryId) VALUES ('cine', 1)")
        db.close()
    }

    @Test
    fun migrate1To2() = runBlocking {
        createV1Database(DB)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Abrir fuerza la migración y la validación del esquema.
            db.openHelper.writableDatabase

            val expense = db.expenseDao().getById(7)!!
            assertEquals(1250L, expense.amountCents)
            assertEquals(1L, expense.categoryId)
            assertFalse(expense.isIncome)
            assertNull(expense.recurringId)

            val recurring = db.recurringDao().getAll().single()
            assertEquals("Netflix", recurring.name)
            assertNull(recurring.remainingCharges)
            assertFalse(recurring.remindDayBefore)
            assertFalse(recurring.isIncome)

            val category = db.categoryDao().getById(1)!!
            assertEquals(10000L, category.monthlyLimitCents)
            assertFalse(category.isIncome)

            assertEquals(1, db.merchantRuleDao().getAll().size)
        } finally {
            db.close()
        }
    }

    /** Cada esquema antiguo guardado en el repositorio debe poder migrar hasta la versión actual. */
    @Test
    fun todasLasVersionesMigran() = runBlocking {
        val versions = schemaDir.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()
        val current = AppDatabase.VERSION
        for (v in versions.filter { it < current }) {
            val name = "migrate-from-$v.db"
            createDatabase(name, v).close()
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
            try {
                db.openHelper.writableDatabase
                db.categoryDao().getAll()
            } finally {
                db.close()
            }
        }
    }

    /** Al pasar a la v14, la Gasolina de serie va al panel de combustible y los gastos conservan todo. */
    @Test
    fun migrate13To14() = runBlocking {
        val name = "migrate-13-14.db"
        createDatabase(name, 13).apply {
            execSQL("INSERT INTO categories (id, name, emoji, colorHex, monthlyLimitCents, isDefault, isIncome, hidden, sortOrder) VALUES (4, 'Gasolina', '⛽', '#1971C2', NULL, 1, 0, 0, 0)")
            execSQL("INSERT INTO categories (id, name, emoji, colorHex, monthlyLimitCents, isDefault, isIncome, hidden, sortOrder) VALUES (5, 'Ocio', '🎉', '#7048E8', NULL, 1, 0, 0, 0)")
            execSQL("INSERT INTO expenses (id, amountCents, merchant, note, categoryId, timestamp, source, rawText, isIncome) VALUES (9, 6000, 'Repsol', '', 4, 1700000000000, 'MANUAL', '', 0)")
            close()
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            db.openHelper.writableDatabase
            assertEquals("FUEL", db.categoryDao().getById(4)!!.kind)
            assertNull(db.categoryDao().getById(5)!!.kind)
            val e = db.expenseDao().getById(9)!!
            assertEquals(6000L, e.amountCents)
            assertNull(e.details)
            assertEquals(0, db.householdDao().getVehicles().size)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
