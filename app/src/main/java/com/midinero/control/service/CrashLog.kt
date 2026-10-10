package com.midinero.control.service

import android.content.Context
import android.os.Build
import com.midinero.control.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Si la app se cierra por un error, guarda el detalle técnico en el propio móvil (nunca se envía a ningún sitio).
 * Al volver a abrirla se ofrece copiarlo o compartirlo para poder arreglarlo.
 */
object CrashLog {
    private const val FILE = "ultimo-error.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(app.filesDir, FILE).writeText(
                    "MiDinero ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})\n" +
                        "Hilo: ${thread.name}\n\n" + trace.take(20_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** El último error guardado, si lo hay. */
    fun read(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
