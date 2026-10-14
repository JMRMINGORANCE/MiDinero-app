package com.midinero.control.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: ExpenseRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ExpenseRepository(db, context)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `copia ida y vuelta conserva todo`() = runBlocking {
        repo.ensureDefaults()
        val ocio = db.categoryDao().getByName(DefaultCategories.OCIO)!!
        val (tripId, _) = repo.saveTrip(Trip(name = "Lisboa", emoji = "🇵🇹", budgetCents = 80000, startDay = 19000, endDay = 19005), false)
        repo.addExpense(Expense(amountCents = 1250, merchant = "Cine \"Luna\"", note = "con Ana", categoryId = ocio.id, timestamp = 1_700_000_000_000, tripId = tripId))
        repo.addExpense(Expense(amountCents = 150000, merchant = "Nómina", isIncome = true, timestamp = 1_700_000_100_000))
        repo.learn("Cine Luna", ocio.id)
        repo.saveRecurring(
            RecurringExpense(name = "Préstamo", amountCents = 9900, frequency = Frequency.QUARTERLY, dayOfMonth = 1,
                nextDueEpochDay = 40000, remainingCharges = 6, remindDayBefore = true)
        )
        repo.saveGoal(SavingsGoal(name = "Viaje", targetCents = 200000, deadlineEpochDay = 21000))
        val goalId = db.goalDao().getGoals().single().id
        repo.contribute(goalId, 5000, "primera")
        repo.contribute(goalId, -1000)
        // Categoría oculta y orden propio también viajan en la copia
        repo.saveCategory(ocio.copy(hidden = true))
        repo.reorderCategories(listOf(db.categoryDao().getByName(DefaultCategories.SALUD)!!.id, ocio.id))
        val prefs = AppPrefs(context)
        prefs.monthlyBudgetCents = 120000
        prefs.periodStartDay = 25
        val original = repo.exportBackup()
        val json = Backup.toJson(original)

        repo.deleteAllData()
        assertTrue(db.expenseDao().getAll().isEmpty())
        assertEquals(0L, prefs.monthlyBudgetCents)

        repo.restoreBackup(Backup.fromJson(json))
        val restored = repo.exportBackup()
        assertEquals(original.expenses, restored.expenses)
        assertEquals(original.categories.sortedBy { it.id }, restored.categories.sortedBy { it.id })
        assertEquals(original.rules, restored.rules)
        assertEquals(original.recurring, restored.recurring)
        assertEquals(original.settings, restored.settings)
        assertTrue(restored.categories.single { it.id == ocio.id }.hidden)
        assertEquals(2, restored.categories.single { it.id == ocio.id }.sortOrder)
        assertEquals(original.trips, restored.trips)
        assertEquals(tripId, restored.expenses.first { it.merchant.startsWith("Cine") }.tripId)
        assertEquals(1, restored.goals.size)
        assertEquals(original.goals, restored.goals)
        assertEquals(original.contributions.sortedBy { it.id }, restored.contributions.sortedBy { it.id })
        assertEquals(4000L, restored.contributions.sumOf { it.amountCents })
        assertEquals(120000L, prefs.monthlyBudgetCents)
        assertEquals(25, prefs.periodStartDay)
        com.midinero.control.domain.Period.startDay = 1
    }

    @Test
    fun `copia cifrada solo se abre con su contrasena`() = runBlocking {
        repo.ensureDefaults()
        repo.addExpense(Expense(amountCents = 4200, merchant = "Farmacia Sol", timestamp = 1_700_000_000_000))
        val data = repo.exportBackup()
        val file = Backup.toFile(data, "secreto123")
        assertTrue(BackupCrypto.isEncrypted(file))
        // Nada del contenido se ve en el fichero
        assertFalse(file.contains("Farmacia"))
        assertEquals(data.expenses, Backup.fromFile(file, "secreto123").expenses)
        assertThrows(BackupPasswordException::class.java) { Backup.fromFile(file, "otra") }
        assertThrows(BackupPasswordException::class.java) { Backup.fromFile(file, null) }
        // Sin contraseña la copia sigue siendo el JSON de siempre, y se lee aunque haya una puesta
        val plain = Backup.toFile(data, null)
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertEquals(data.expenses, Backup.fromFile(plain, "secreto123").expenses)
    }

    @Test
    fun `cada copia cifrada usa su propia sal`() {
        val a = BackupCrypto.encrypt("hola", "clave1", iterations = 1_000)
        val b = BackupCrypto.encrypt("hola", "clave1", iterations = 1_000)
        assertNotEquals(a, b)
        assertEquals("hola", BackupCrypto.decrypt(a, "clave1"))
        assertEquals("hola", BackupCrypto.decrypt(b, "clave1"))
    }

    @Test
    fun `copia conserva coches, seguros y datos extra`() = runBlocking {
        repo.ensureDefaults()
        val fuel = db.categoryDao().getAll().single { it.kind == "FUEL" }
        val light = repo.createKindCategory(com.midinero.control.domain.TrackerKind.ELECTRICITY)
        val carId = repo.saveVehicle(Vehicle(name = "León", plate = "1234 abc", itvDueEpochDay = 20500))
        val policyId = repo.savePolicy(Policy(name = "Seguro León", vehicleId = carId, renewalEpochDay = 20600), currentPriceCents = 41000)
        repo.renewPolicy(db.householdDao().getPolicies().single(), 45000)
        repo.addExpense(Expense(amountCents = 6000, merchant = "Repsol", categoryId = fuel.id, timestamp = 1_700_000_000_000, details = "l=40;km=600;car=$carId"))
        repo.addExpense(Expense(amountCents = 8000, merchant = "Iberdrola", categoryId = light, timestamp = 1_700_000_100_000, details = "kp=100;kl=120;kv=180"))

        val restoredData = Backup.fromJson(Backup.toJson(repo.exportBackup()))
        repo.deleteAllData()
        assertTrue(db.householdDao().getVehicles().isEmpty())
        repo.restoreBackup(restoredData)

        assertEquals("1234 ABC", db.householdDao().getVehicles().single().plate)
        val policy = db.householdDao().getPolicies().single()
        assertEquals(policyId, policy.id)
        assertEquals(20600L + 365, policy.renewalEpochDay) // renovado: vence un año después
        assertEquals(listOf(41000L, 45000L), db.householdDao().getPolicyPrices().sortedBy { it.startEpochDay }.map { it.amountCents })
        assertEquals("ELECTRICITY", db.categoryDao().getById(light)!!.kind)
        assertEquals(setOf("l=40;km=600;car=$carId", "kp=100;kl=120;kv=180"), db.expenseDao().getAll().mapNotNull { it.details }.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza ficheros que no son copias`() {
        Backup.fromJson("""{"hola": 1}""")
    }
}
