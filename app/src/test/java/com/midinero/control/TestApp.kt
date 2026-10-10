package com.midinero.control

import android.util.Log
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper

/** Aplicación para los tests de interfaz: igual que la real, pero con WorkManager de pruebas. */
class TestApp : MiDineroApp() {
    // Robolectric reinicia los ajustes en cada test pero no los ficheros, y un test anterior puede seguir
    // escribiendo en segundo plano (datos de ejemplo): cada test usa su propia base de datos.
    override val databaseName: String = "test-${java.util.UUID.randomUUID()}.db"

    override fun onCreate() {
        // Los tests comprueban los textos en español, aunque el sistema de pruebas esté en inglés
        com.midinero.control.data.AppPrefs(this).language = "es"
        WorkManagerTestInitHelper.initializeTestWorkManager(
            this,
            Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build(),
        )
        super.onCreate()
    }
}
