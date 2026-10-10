package com.midinero.control.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.midinero.control.domain.ParsedPayment
import com.midinero.control.domain.Recurrence
import com.midinero.control.domain.currentPeriod
import com.midinero.control.domain.endMillis
import com.midinero.control.domain.startMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: ExpenseRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ExpenseRepository(db, context)
        repo.ensureDefaults()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun category(name: String) = db.categoryDao().getByName(name)!!

    @Test
    fun `recurrente atrasado apunta todos los cargos pendientes`() = runBlocking {
        val today = LocalDate.now()
        val start = today.minusDays(70)
        repo.saveRecurring(
            RecurringExpense(name = "Netflix", amountCents = 1299, dayOfMonth = start.dayOfMonth, nextDueEpochDay = start.toEpochDay())
        )
        var expected = 0
        var d = start
        while (d <= today) { expected++; d = Recurrence.next(d, Frequency.MONTHLY, start.dayOfMonth) }

        val expenses = db.expenseDao().getAll()
        assertEquals(expected, expenses.size)
        assertTrue(expenses.all { it.source == ExpenseSource.RECURRING && it.recurringId != null })
        val r = db.recurringDao().getAll().single()
        assertEquals(d.toEpochDay(), r.nextDueEpochDay)
        // Volver a ejecutar no duplica nada
        assertEquals(0, repo.generateDueRecurring())
    }

    @Test
    fun `las cuotas terminan solas`() = runBlocking {
        val start = LocalDate.now().minusMonths(5)
        repo.saveRecurring(
            RecurringExpense(
                name = "Móvil a plazos", amountCents = 2000, dayOfMonth = start.dayOfMonth,
                nextDueEpochDay = start.toEpochDay(), remainingCharges = 2,
            )
        )
        assertEquals(2, db.expenseDao().getAll().size)
        val r = db.recurringDao().getAll().single()
        assertEquals(0, r.remainingCharges)
        assertFalse(r.active)
    }

    @Test
    fun `saltar el proximo cargo`() = runBlocking {
        val next = LocalDate.now().plusDays(3)
        repo.saveRecurring(RecurringExpense(name = "Gimnasio", amountCents = 3000, dayOfMonth = next.dayOfMonth, nextDueEpochDay = next.toEpochDay()))
        val r = db.recurringDao().getAll().single()
        repo.skipNextCharge(r)
        assertEquals(Recurrence.next(next, Frequency.MONTHLY, next.dayOfMonth).toEpochDay(), db.recurringDao().getAll().single().nextDueEpochDay)
        assertTrue(db.expenseDao().getAll().isEmpty())
    }

    @Test
    fun `aprender un comercio categoriza sus gastos pendientes`() = runBlocking {
        val now = System.currentTimeMillis()
        repo.addExpense(Expense(amountCents = 500, merchant = "BAR PEPE 123", timestamp = now))
        repo.addExpense(Expense(amountCents = 700, merchant = "Bar Pepe 999", timestamp = now))
        val ocio = category(DefaultCategories.OCIO)
        repo.learn("bar pepe", ocio.id)
        assertTrue(db.expenseDao().getAll().all { it.categoryId == ocio.id })
        assertEquals(ocio.id, repo.categorize("BAR PEPE 555"))
    }

    @Test
    fun `pagos detectados se categorizan y no se duplican`() = runBlocking {
        val now = System.currentTimeMillis()
        val first = repo.addDetectedPayment(ParsedPayment(1250, "MERCADONA"), "raw", now)
        assertNotNull(first)
        assertEquals(category(DefaultCategories.SUPERMERCADO).id, first!!.first.categoryId)
        // Mismo pago avisado por otra app con otro texto
        assertNull(repo.addDetectedPayment(ParsedPayment(1250, "Pago con Google Pay"), "raw", now + 20_000))
        // Otro importe sí se registra
        assertNotNull(repo.addDetectedPayment(ParsedPayment(300, "Café"), "raw", now + 30_000))
        assertEquals(2, db.expenseDao().getAll().size)
    }

    @Test
    fun `limpia las fotos de tickets que ya no usa ningun gasto`() = runBlocking {
        val dir = java.io.File(context.filesDir, "receipts").apply { mkdirs() }
        val old = System.currentTimeMillis() - 3L * 86_400_000
        val used = java.io.File(dir, "usado.jpg").apply { writeText("x"); setLastModified(old) }
        val orphan = java.io.File(dir, "huerfano.jpg").apply { writeText("x"); setLastModified(old) }
        val recent = java.io.File(dir, "reciente.jpg").apply { writeText("x") }
        repo.addExpense(Expense(amountCents = 100, merchant = "Ferretería", timestamp = System.currentTimeMillis(), receipt = "usado.jpg"))
        assertEquals(1, repo.cleanReceipts())
        assertTrue(used.exists())
        assertFalse(orphan.exists())
        // La de un editor aún abierto no se toca
        assertTrue(recent.exists())
    }

    @Test
    fun `viaje activo recoge los gastos de sus fechas`() = runBlocking {
        val now = System.currentTimeMillis()
        val today = java.time.LocalDate.now().toEpochDay()
        repo.addExpense(Expense(amountCents = 4000, merchant = "Hotel", timestamp = now - 60_000))
        repo.addExpense(Expense(amountCents = 999, merchant = "Antes del viaje", timestamp = now - 10L * 86_400_000))
        val (tripId, collected) = repo.saveTrip(Trip(name = "Oporto", startDay = today - 1, endDay = today + 3, budgetCents = 10_000), true)
        assertEquals(1, collected)
        val detected = repo.addDetectedPayment(ParsedPayment(2500, "TAXI"), "raw", now)!!.first
        assertEquals(tripId, detected.tripId)
        assertEquals(6500L, db.expenseDao().tripTotal(tripId))
        // Borrar el viaje no borra sus gastos
        repo.deleteTrip(repo.trip(tripId)!!)
        assertEquals(3, db.expenseDao().getAll().size)
        assertNull(db.expenseDao().getAll().firstOrNull { it.tripId != null })
    }

    @Test
    fun `pagos en otra moneda se guardan en euros con el ultimo cambio`() = runBlocking {
        AppPrefs(context).setRate("USD", 0.9)
        val now = System.currentTimeMillis()
        val saved = repo.addDetectedPayment(ParsedPayment(2000, "STARBUCKS NYC", "USD"), "raw", now)!!.first
        assertEquals(1800L, saved.amountCents)
        assertEquals("USD", saved.originalCurrency)
        assertEquals(2000L, saved.originalAmountCents)
        // El mismo aviso repetido no se duplica aunque venga en dólares
        assertNull(repo.addDetectedPayment(ParsedPayment(2000, "STARBUCKS NYC", "USD"), "raw", now + 10_000))
    }

    @Test
    fun `aviso de limite una sola vez por umbral`() = runBlocking {
        val ocio = category(DefaultCategories.OCIO)
        repo.setLimit(ocio, 10000)
        val alerts = context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
        val key80 = "alert_${ocio.id}_${currentPeriod()}_80"
        val key100 = "alert_${ocio.id}_${currentPeriod()}_100"
        val now = System.currentTimeMillis()
        repo.addExpense(Expense(amountCents = 5000, merchant = "Cine", categoryId = ocio.id, timestamp = now))
        assertFalse(alerts.getBoolean(key80, false))
        repo.addExpense(Expense(amountCents = 3500, merchant = "Cine", categoryId = ocio.id, timestamp = now))
        assertTrue(alerts.getBoolean(key80, false))
        assertFalse(alerts.getBoolean(key100, false))
        repo.addExpense(Expense(amountCents = 2000, merchant = "Cine", categoryId = ocio.id, timestamp = now))
        assertTrue(alerts.getBoolean(key100, false))
    }

    @Test
    fun `los ingresos no cuentan como gasto`() = runBlocking {
        val now = System.currentTimeMillis()
        val m = currentPeriod()
        repo.addExpense(Expense(amountCents = 150000, merchant = "Nómina", isIncome = true, timestamp = now))
        repo.addExpense(Expense(amountCents = 2000, merchant = "Cine", timestamp = now))
        assertEquals(2000L, repo.spentBetween(m.startMillis(), m.endMillis()))
    }

    @Test
    fun `nombre de categoria repetido se rechaza`() = runBlocking {
        val ocio = category(DefaultCategories.OCIO)
        assertFalse(repo.saveCategory(ocio.copy(name = DefaultCategories.SALUD)))
        assertFalse(repo.saveCategory(Category(name = "ocio", emoji = "x", colorHex = "#000000")))
        assertTrue(repo.saveCategory(Category(name = "Mascotas", emoji = "🐶", colorHex = "#000000")))
    }

    @Test
    fun `borrar una categoria pasa sus movimientos, fijos y reglas a otra`() = runBlocking {
        val ocio = category(DefaultCategories.OCIO)
        val compras = category(DefaultCategories.COMPRAS)
        val now = System.currentTimeMillis()
        repo.addExpense(Expense(amountCents = 900, merchant = "Cine Luna", categoryId = ocio.id, timestamp = now))
        repo.saveRecurring(RecurringExpense(name = "Netflix", amountCents = 1299, categoryId = ocio.id, dayOfMonth = 1, nextDueEpochDay = LocalDate.now().plusDays(5).toEpochDay()))
        repo.learn("Cine Luna", ocio.id)
        db.ruleDao().insert(CustomRule(pattern = "cine", categoryId = ocio.id))

        repo.deleteCategory(ocio, moveTo = compras.id)

        assertNull(db.categoryDao().getById(ocio.id))
        assertEquals(compras.id, db.expenseDao().getAll().single().categoryId)
        assertEquals(compras.id, db.recurringDao().getAll().single().categoryId)
        assertEquals(compras.id, db.ruleDao().getAll().single().categoryId)
        assertEquals(compras.id, repo.categorize("CINE LUNA 22"))
    }

    @Test
    fun `deshacer el borrado de una categoria lo deja todo como estaba`() = runBlocking {
        val ocio = category(DefaultCategories.OCIO)
        val compras = category(DefaultCategories.COMPRAS)
        val now = System.currentTimeMillis()
        repo.addExpense(Expense(amountCents = 900, merchant = "Cine Luna", categoryId = ocio.id, timestamp = now))
        repo.saveRecurring(RecurringExpense(name = "Netflix", amountCents = 1299, categoryId = ocio.id, dayOfMonth = 1, nextDueEpochDay = LocalDate.now().plusDays(5).toEpochDay()))
        repo.learn("Cine Luna", ocio.id)
        db.ruleDao().insert(CustomRule(pattern = "cine", categoryId = ocio.id))
        val before = repo.exportBackup()

        // Sin destino (lo aprendido se borra en cascada) y con destino
        for (moveTo in listOf(null, compras.id)) {
            val deleted = repo.deleteCategory(category(DefaultCategories.OCIO), moveTo)
            assertNull(db.categoryDao().getById(ocio.id))
            repo.restoreCategory(deleted)
            val after = repo.exportBackup()
            assertEquals(before.categories.sortedBy { it.id }, after.categories.sortedBy { it.id })
            assertEquals(before.expenses, after.expenses)
            assertEquals(before.recurring, after.recurring)
            assertEquals(before.customRules, after.customRules)
            assertEquals(before.rules.sortedBy { it.merchantKey }, after.rules.sortedBy { it.merchantKey })
        }
    }

    @Test
    fun `borrar una categoria sin destino deja sus gastos sin categoria`() = runBlocking {
        val ocio = category(DefaultCategories.OCIO)
        repo.addExpense(Expense(amountCents = 900, merchant = "Cine", categoryId = ocio.id, timestamp = System.currentTimeMillis()))
        repo.deleteCategory(ocio)
        assertNull(db.expenseDao().getAll().single().categoryId)
    }

    @Test
    fun `categorias ordenadas a mano y ocultas fuera de la clasificacion automatica`() = runBlocking {
        val salud = category(DefaultCategories.SALUD)
        val ocio = category(DefaultCategories.OCIO)
        repo.reorderCategories(listOf(salud.id, ocio.id))
        val ordered = repo.categories.first().map { it.id }
        assertEquals(listOf(salud.id, ocio.id), ordered.filter { it == salud.id || it == ocio.id })
        // Las que no se han ordenado (sortOrder 0) van antes, por nombre, como siempre
        assertEquals(ordered.size - 2, ordered.indexOf(salud.id))
        // Una nueva, después de ordenar, va al final
        val nueva = repo.createCategory(Category(name = "Mascotas", emoji = "🐶", colorHex = "#000000"))
        assertEquals(nueva, repo.categories.first().last().id)

        val supermercado = category(DefaultCategories.SUPERMERCADO)
        assertEquals(supermercado.id, repo.categorize("MERCADONA 123"))
        repo.saveCategory(supermercado.copy(hidden = true))
        assertNull(repo.categorize("MERCADONA 123"))
        // Lo que tú le enseñas sí se respeta aunque esté oculta
        repo.learn("Mercadona", supermercado.id)
        assertEquals(supermercado.id, repo.categorize("Mercadona"))
    }

    @Test
    fun `una devolucion detectada resta de la categoria de la compra`() = runBlocking {
        val now = System.currentTimeMillis()
        // Compra en un comercio sin categoría automática, puesta a mano en Compras
        val compras = category(DefaultCategories.COMPRAS)
        repo.addExpense(Expense(amountCents = 5000, merchant = "Tienda Lunar", categoryId = compras.id, timestamp = now - 3 * 86_400_000L))
        val (refund, cat) = repo.addDetectedPayment(ParsedPayment(2000, "TIENDA LUNAR", refund = true), "raw", now)!!
        assertTrue(refund.isRefund)
        assertEquals(-2000L, refund.amountCents)
        assertEquals("Tienda Lunar", refund.merchant)
        assertEquals(compras.id, cat?.id)
        assertEquals(3000L, repo.spentBetween(now - 30L * 86_400_000, now + 1))
        // El mismo aviso repetido no se apunta dos veces
        assertNull(repo.addDetectedPayment(ParsedPayment(2000, "TIENDA LUNAR", refund = true), "raw", now + 20_000))
    }

    @Test
    fun `devolucion apuntada a mano desde la compra`() = runBlocking {
        val ocio = category(DefaultCategories.OCIO)
        val now = System.currentTimeMillis()
        repo.addExpense(Expense(amountCents = 1800, merchant = "Cine", categoryId = ocio.id, timestamp = now - 86_400_000L))
        val compra = db.expenseDao().getAll().single()
        // Nunca más de lo que costó
        val refund = repo.addRefund(compra, 5000, now)
        assertEquals(-1800L, refund.amountCents)
        assertEquals(ocio.id, refund.categoryId)
        assertEquals(0L, repo.spentBetween(now - 30L * 86_400_000, now + 1))
    }

    @Test
    fun `una devolucion del banco no se importa como ingreso`() = runBlocking {
        val day = LocalDate.now().minusDays(2)
        val rows = listOf(
            com.midinero.control.domain.ImportedRow(day.minusDays(5), "COMPRA ZARA ONLINE", -5990),
            com.midinero.control.domain.ImportedRow(day, "DEVOLUCION COMPRA ZARA ONLINE", 5990),
        )
        val result = repo.importRows(rows, includeIncome = false)
        assertEquals(2, result.added)
        val all = db.expenseDao().getAll()
        assertTrue(all.none { it.isIncome })
        val refund = all.single { it.isRefund }
        assertEquals(-5990L, refund.amountCents)
        assertEquals(all.single { !it.isRefund }.categoryId, refund.categoryId)
    }

    @Test
    fun `una deuda se corrige, se borra y se recupera`() = runBlocking {
        repo.saveDebt(Debt(person = "Ana", amountCents = 1500, note = "Cena"))
        val d = db.debtDao().getAll().single()
        repo.saveDebt(d.copy(person = "Ana", amountCents = -900, note = "Taxi"))
        val fixed = db.debtDao().getAll().single()
        assertEquals(d.id, fixed.id)
        assertEquals(-900L, fixed.amountCents)
        assertEquals("Taxi", fixed.note)
        repo.deleteDebt(fixed)
        assertTrue(db.debtDao().getAll().isEmpty())
        repo.restoreDebt(fixed)
        assertEquals(fixed, db.debtDao().getAll().single())
    }

    @Test
    fun `devolucion sin compra original se apunta con la categoria que se adivine`() = runBlocking {
        val now = System.currentTimeMillis()
        val (refund, _) = repo.addDetectedPayment(ParsedPayment(1000, "Tienda Nueva", refund = true), "raw", now)!!
        assertEquals(-1000L, refund.amountCents)
        assertEquals("Tienda Nueva", refund.merchant)
    }

    @Test
    fun `crear una categoria personalizada devuelve su id`() = runBlocking {
        val id = repo.createCategory(Category(name = "  Mascotas ", emoji = "🐶", colorHex = "#000000"))
        assertNotNull(id)
        assertEquals("Mascotas", db.categoryDao().getById(id!!)?.name)
        // Mismo nombre (sin distinguir mayúsculas) o vacío: no se crea
        assertNull(repo.createCategory(Category(name = "mascotas", emoji = "x", colorHex = "#000000")))
        assertNull(repo.createCategory(Category(name = " ", emoji = "x", colorHex = "#000000")))
        // Se puede usar enseguida en un gasto
        repo.addExpense(Expense(amountCents = 1500, merchant = "Tiendanimal", categoryId = id, timestamp = System.currentTimeMillis()))
        assertEquals(id, db.expenseDao().getAll().single().categoryId)
    }

    @Test
    fun `cambiar la categoria de un comercio afecta a todo su historial`() = runBlocking {
        val now = System.currentTimeMillis()
        val salud = category(DefaultCategories.SALUD)
        repo.addExpense(Expense(amountCents = 1000, merchant = "Clínica Sol 1", categoryId = category(DefaultCategories.OTROS).id, timestamp = now))
        repo.addExpense(Expense(amountCents = 2000, merchant = "CLINICA SOL 2", timestamp = now))
        repo.addExpense(Expense(amountCents = 3000, merchant = "Otra cosa", timestamp = now))
        repo.recategorizeMerchant("clinica sol", salud.id)
        val all = db.expenseDao().getAll()
        assertEquals(2, all.count { it.categoryId == salud.id })
        assertEquals(salud.id, repo.categorize("Clinica Sol 3"))
    }

    @Test
    fun `las reglas mandan, renombran y se aplican al historial`() = runBlocking {
        val compras = category(DefaultCategories.COMPRAS)
        val ocio = category(DefaultCategories.OCIO)
        val now = System.currentTimeMillis()
        repo.addExpense(Expense(amountCents = 999, merchant = "AMZN Mktp ES*111", categoryId = ocio.id, timestamp = now - 100_000_000))
        val rule = CustomRule(pattern = "amzn", categoryId = compras.id, renameTo = "Amazon")
        repo.saveCustomRule(rule)
        assertEquals(compras.id, repo.categorize("AMZN Mktp ES*2K4"))

        val detected = repo.addDetectedPayment(ParsedPayment(2500, "AMZN Mktp ES*2K4"), "raw", now)!!
        assertEquals("Amazon", detected.first.merchant)
        assertEquals(compras.id, detected.first.categoryId)

        val saved = db.ruleDao().getAll().single()
        assertEquals(1, repo.applyRuleToHistory(saved))
        assertTrue(db.expenseDao().getAll().all { it.merchant == "Amazon" && it.categoryId == compras.id })
    }

    @Test
    fun `importar del banco sin duplicar y clasificando`() = runBlocking {
        val day = LocalDate.now().minusDays(2)
        val rows = listOf(
            com.midinero.control.domain.ImportedRow(day, "COMPRA MERCADONA", -4520),
            com.midinero.control.domain.ImportedRow(day, "NOMINA EMPRESA", 185000),
            com.midinero.control.domain.ImportedRow(day, "COMPRA MERCADONA", -4520),
        )
        // Dos filas iguales en el mismo fichero son dos compras (dos cafés el mismo día)
        val first = repo.importRows(rows, includeIncome = false)
        assertEquals(2, first.added)
        assertEquals(0, first.duplicates)
        assertEquals(1, first.skippedIncome)
        val all = db.expenseDao().getAll()
        assertEquals(2, all.size)
        val e = all.first()
        assertEquals(ExpenseSource.IMPORTED, e.source)
        assertEquals(category(DefaultCategories.SUPERMERCADO).id, e.categoryId)
        // Volver a importar el mismo fichero no añade nada nuevo salvo el ingreso si ahora se pide
        val second = repo.importRows(rows, includeIncome = true)
        assertEquals(1, second.added)
        assertEquals(2, second.duplicates)
        assertTrue(db.expenseDao().getAll().any { it.isIncome && it.amountCents == 185000L })
    }

    @Test
    fun `importar no duplica un pago ya detectado en el movil`() = runBlocking {
        val day = LocalDate.now().minusDays(3)
        val detectedAt = day.atTime(18, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        repo.addExpense(Expense(amountCents = 4520, merchant = "Mercadona", timestamp = detectedAt, source = ExpenseSource.GOOGLE_PAY))
        val rows = listOf(
            com.midinero.control.domain.ImportedRow(day, "COMPRA TARJ. 5234XXXX MERCADONA VALENCIA", -4520),
            com.midinero.control.domain.ImportedRow(day, "COMPRA TARJ. 5234XXXX ZARA", -2999),
        )
        val result = repo.importRows(rows, includeIncome = false)
        assertEquals(1, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(2, db.expenseDao().getAll().size)
    }

    @Test
    fun `datos de ejemplo se borran sin tocar los reales`() = runBlocking {
        repo.addExpense(Expense(amountCents = 1234, merchant = "Mío", timestamp = System.currentTimeMillis()))
        repo.loadDemo()
        assertTrue(db.expenseDao().getAll().size > 20)
        assertEquals(3, db.recurringDao().getAll().size)
        assertEquals(1, db.tripDao().getAll().size)
        assertTrue(db.expenseDao().getAll().any { it.originalCurrency == "GBP" && it.tripId != null })
        repo.clearDemo()
        assertTrue(db.tripDao().getAll().isEmpty())
        assertEquals(listOf("Mío"), db.expenseDao().getAll().map { it.merchant })
        assertTrue(db.recurringDao().getAll().isEmpty())
        assertTrue(db.goalDao().getGoals().isEmpty())
    }

    @Test
    fun `compartir un gasto deja tu parte y apunta lo que te deben`() = runBlocking {
        val id = repo.addExpense(Expense(amountCents = 9000, merchant = "Cena", timestamp = System.currentTimeMillis()))
        val e = db.expenseDao().getById(id)!!
        repo.shareExpense(e, listOf("Ana", "Luis"))
        assertEquals(3000L, db.expenseDao().getById(id)!!.amountCents)
        val debts = db.debtDao().getAll()
        assertEquals(6000L, debts.sumOf { it.amountCents })
        assertTrue(debts.all { it.expenseId == id && it.settledAt == null })
        repo.settlePerson("ana")
        assertEquals(listOf("Luis"), db.debtDao().getAll().filter { it.settledAt == null }.map { it.person })
    }
}
