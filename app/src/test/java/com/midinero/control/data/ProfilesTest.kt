package com.midinero.control.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProfilesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        Profiles.current = Profiles.MAIN
    }

    @Test
    fun elPrincipalUsaLosFicherosDeSiempre() {
        assertEquals(AppDatabase.FILE_NAME, Profiles.databaseName(Profiles.MAIN))
        assertEquals("midinero_prefs", Profiles.prefsName(Profiles.MAIN))
        assertEquals("receipts", Profiles.receiptsDir(Profiles.MAIN))
        // Las copias de un perfil no empiezan como las del principal (al limpiar las viejas no se tocan entre ellas)
        val main = Profiles.backupPrefix(Profiles.MAIN, "auto")
        val other = Profiles.backupPrefix(2, "auto")
        assertFalse(other.startsWith(main))
        assertFalse(main.startsWith(other))
    }

    @Test
    fun crearYBorrarUnPerfil() {
        val main = AppPrefs(context)
        main.themeMode = "dark"
        main.monthlyBudgetCents = 50000
        main.onboardingDone = true
        val business = Profiles.create(context, "Negocio", "💼")
        assertEquals(listOf("Personal", "Negocio"), Profiles.list(context).map { it.name })
        val prefs = AppPrefs(context, Profiles.prefsName(business.id))
        // Se lleva el aspecto, pero no los datos ni el presupuesto, y no pasa por la bienvenida
        assertEquals("dark", prefs.themeMode)
        assertEquals(0L, prefs.monthlyBudgetCents)
        assertTrue(prefs.onboardingDone)

        val db = AppDatabase.build(context, Profiles.databaseName(business.id))
        db.openHelper.writableDatabase
        db.close()
        assertTrue(context.getDatabasePath(Profiles.databaseName(business.id)).exists())

        // No se puede borrar el principal ni el abierto
        assertFalse(Profiles.delete(context, Profiles.MAIN))
        Profiles.current = business.id
        assertFalse(Profiles.delete(context, business.id))
        Profiles.current = Profiles.MAIN
        assertTrue(Profiles.delete(context, business.id))
        assertFalse(context.getDatabasePath(Profiles.databaseName(business.id)).exists())
        assertEquals(1, Profiles.list(context).size)
        assertEquals(50000L, AppPrefs(context).monthlyBudgetCents)
    }
}
