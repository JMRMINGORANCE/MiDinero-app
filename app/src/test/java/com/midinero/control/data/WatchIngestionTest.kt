package com.midinero.control.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.midinero.control.domain.ParsedPayment
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WatchIngestionTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var repo: ExpenseRepository
    private val time = 1_780_000_000_000L
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ExpenseRepository(db, context)
        repo.ensureDefaults()
    }
    @After fun close() = db.close()

    @Test fun phoneAndWatchInEitherOrder() = runBlocking {
        for (watchFirst in listOf(false, true)) {
            val first = if (watchFirst) "watch:1" else "phone:1"
            val second = if (watchFirst) "phone:1" else "watch:1"
            val offset = if (watchFirst) 1_000_000L else 0L
            assertNotNull(repo.addDetectedPayment(ParsedPayment(1250, "Café Sol"), "raw", time + offset, first + offset))
            assertNull(repo.addDetectedPayment(ParsedPayment(1250, "CAFE SOL"), "other raw", time + offset - 2000, second + offset))
        }
        assertEquals(2, db.expenseDao().getAll().size)
    }
    @Test fun concurrentArrivalIsAtomic() = runBlocking {
        coroutineScope {
            (1..20).map { n -> async(Dispatchers.IO) {
                repo.addDetectedPayment(ParsedPayment(990, "Tienda Luna"), "raw", time, "device:$n")
            } }.awaitAll()
        }
        assertEquals(1, db.expenseDao().getAll().size)
    }
    @Test fun replaySurvivesRepositoryRestartAndExpenseDeletion() = runBlocking {
        val e = repo.addDetectedPayment(ParsedPayment(500, "Café Sol"), "raw", time, "watch:stable")!!.first
        repo.deleteExpense(e)
        val restarted = ExpenseRepository(db, context)
        assertNull(restarted.addDetectedPayment(ParsedPayment(500, "Café Sol"), "raw", time, "watch:stable"))
        assertTrue(db.expenseDao().getAll().isEmpty())
    }
    @Test fun offlineDeliveryUsesEventTimeAndDoesNotMatchUnrelatedFuturePayment() = runBlocking {
        repo.addDetectedPayment(ParsedPayment(500, "Café Sol"), "raw", time + 86_400_000, "phone:new")
        assertNotNull(repo.addDetectedPayment(ParsedPayment(500, "Café Sol"), "raw", time, "watch:old"))
        assertNull(repo.addDetectedPayment(ParsedPayment(500, "Café Sol"), "raw", time + 1000, "phone:old"))
        assertEquals(2, db.expenseDao().getAll().size)
    }
    @Test fun sameAmountAtDifferentMerchantsIsNotDropped() = runBlocking {
        repo.addDetectedPayment(ParsedPayment(500, "Tienda Luna"), "raw", time, "phone:a")
        assertNotNull(repo.addDetectedPayment(ParsedPayment(500, "Café Sol"), "raw", time + 1000, "watch:b"))
    }
    @Test fun currencyAndTimeSeparatePayments() = runBlocking {
        repo.addDetectedPayment(ParsedPayment(500, "Café Sol", "USD"), "raw", time, "phone:a")
        assertNotNull(repo.addDetectedPayment(ParsedPayment(500, "Café Sol", "EUR"), "raw", time, "watch:b"))
        assertNotNull(repo.addDetectedPayment(ParsedPayment(500, "Café Sol", "USD"), "raw", time + 91_000, "watch:c"))
    }
    @Test fun watchEntriesAreIdempotentButTwoIntentionalEntriesArePreserved() = runBlocking {
        val cat = db.categoryDao().getAll().first()
        assertTrue(repo.addFromWatch("watch:a", 350, cat.id, "", time))
        assertFalse(ExpenseRepository(db, context).addFromWatch("watch:a", 350, cat.id, "", time))
        assertTrue(repo.addFromWatch("watch:b", 350, null, "Café", time))
        val all = db.expenseDao().getAll()
        assertEquals(2, all.size)
        assertTrue(all.any { it.categoryId == cat.id && it.merchant == cat.name })
    }
    @Test fun revolutTopUpsAreRemovedOnlyWhenDetected() = runBlocking {
        db.expenseDao().insert(Expense(amountCents = 5000, merchant = "REVOLUT**1234", timestamp = time, source = ExpenseSource.GOOGLE_PAY))
        db.expenseDao().insert(Expense(amountCents = 2000, merchant = "Revolut", timestamp = time, source = ExpenseSource.MANUAL))
        db.expenseDao().insert(Expense(amountCents = 990, merchant = "Mercadona", timestamp = time, source = ExpenseSource.GOOGLE_PAY))
        assertEquals(1, repo.removeDetectedTopUps())
        assertEquals(listOf("Mercadona", "Revolut"), db.expenseDao().getAll().map { it.merchant }.sorted())
    }
}
