package com.midinero.control.ui

import androidx.activity.result.contract.ActivityResultContracts
import com.midinero.control.TestApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los selectores de archivos (Restaurar, Importar del banco, foto del ticket…) se abren con ActivityResultRegistry,
 * que usa requestCode de más de 16 bits. Con un FragmentActivity antiguo eso lanzaba
 * «Can only use lower 16 bits for requestCode» y Restaurar decía que el móvil no deja abrir archivos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class FilePickerTest {

    @Test
    fun openDocumentLaunchesFromMainActivity() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val launcher = activity.activityResultRegistry.register("restore", ActivityResultContracts.OpenDocument()) { }
        launcher.launch(arrayOf("application/json"))
        launcher.unregister()
    }
}
