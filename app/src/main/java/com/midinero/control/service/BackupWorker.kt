package com.midinero.control.service

import com.midinero.control.domain.tr
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.midinero.control.MiDineroApp
import com.midinero.control.data.Backup
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Guarda sola una copia de seguridad en la carpeta elegida y conserva las 5 más recientes. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MiDineroApp
        val prefs = app.prefs
        val days = prefs.autoBackupDays
        val folder = prefs.autoBackupUri ?: return Result.success()
        if (days <= 0) return Result.success()
        val due = System.currentTimeMillis() - prefs.lastBackupAt >= days * 24L * 3600 * 1000 - 3600_000
        if (!due && !inputData.getBoolean(KEY_FORCE, false)) return Result.success()
        return try {
            writeTo(app, folder)
            prefs.lastBackupAt = System.currentTimeMillis()
            prefs.lastAutoBackupError = null
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SecurityException) {
            prefs.lastAutoBackupError = tr("No se pudo guardar la copia automática. Vuelve a elegir la carpeta.", "Couldn't save the automatic backup. Choose the folder again.")
            Result.success()
        } catch (e: java.io.FileNotFoundException) {
            prefs.lastAutoBackupError = tr("No se pudo guardar la copia automática. Vuelve a elegir la carpeta.", "Couldn't save the automatic backup. Choose the folder again.")
            Result.success()
        } catch (e: Exception) {
            // Fallo pasajero (almacenamiento, proveedor de la carpeta…): se reintenta más tarde
            prefs.lastAutoBackupError = tr("No se pudo guardar la última copia automática; se reintentará.", "Couldn't save the last automatic backup; it will retry.")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val NAME = "auto_backup"
        const val KEY_FORCE = "force"
        private const val PREFIX = "MiDinero-auto-"
        private const val KEEP = 5

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Valor de la carpeta de copias que significa «Descargas/MiDinero» (no hace falta elegir nada). */
        const val DOWNLOADS = "downloads"

        /** Escribe la copia en la carpeta elegida o en Descargas/MiDinero. Lanza excepción si no puede. */
        suspend fun writeTo(app: MiDineroApp, folder: String) {
            if (folder == DOWNLOADS) writeToDownloads(app, PREFIX, keep = KEEP) else writeBackup(app, folder.toUri())
        }

        /**
         * Guarda la copia en Descargas/MiDinero con MediaStore (Android 10 o posterior, sin pedir permisos) y deja solo
         * las [keep] más recientes con ese [prefix]. Devuelve el nombre del archivo.
         */
        suspend fun writeToDownloads(app: MiDineroApp, prefix: String, keep: Int = Int.MAX_VALUE): String {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                throw IllegalStateException(tr("Este móvil necesita elegir una carpeta", "This phone needs a folder to be chosen"))
            }
            val resolver = app.contentResolver
            val json = Backup.toFile(app.repository.exportBackup(), app.prefs.backupPassword)
            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val path = android.os.Environment.DIRECTORY_DOWNLOADS + "/MiDinero/"
            val name = "$prefix${LocalDate.now()}.json"
            val col = android.provider.MediaStore.MediaColumns.DISPLAY_NAME
            // Las de MiDinero que ya hay (solo se ven las que guardó esta app): se sustituye la de hoy y se borran las viejas
            val existing = mutableListOf<Pair<Uri, String>>()
            resolver.query(
                collection, arrayOf(android.provider.MediaStore.MediaColumns._ID, col),
                "$col LIKE ?", arrayOf("$prefix%"), null,
            )?.use { c ->
                while (c.moveToNext()) existing += android.content.ContentUris.withAppendedId(collection, c.getLong(0)) to c.getString(1)
            }
            val values = android.content.ContentValues().apply {
                put(col, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, path)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error(tr("No se pudo crear el fichero", "Couldn't create the file"))
            try {
                resolver.openOutputStream(uri, "w")!!.use { it.write(json.toByteArray()) }
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            // La copia nueva ya está escrita: ahora sí se quita la anterior del mismo día
            existing.filter { it.second == name }.forEach { runCatching { resolver.delete(it.first, null, null) } }
            resolver.update(uri, android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                put(col, name)
            }, null, null)
            existing.filter { it.second != name }.sortedByDescending { it.second }.drop((keep - 1).coerceAtLeast(0))
                .forEach { runCatching { resolver.delete(it.first, null, null) } }
            return "MiDinero/$name"
        }

        /** Escribe la copia en la carpeta [tree] y borra las antiguas. Lanza excepción si no puede. */
        suspend fun writeBackup(app: MiDineroApp, tree: Uri) {
            val resolver = app.contentResolver
            val json = Backup.toFile(app.repository.exportBackup(), app.prefs.backupPassword)
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "$PREFIX${LocalDate.now()}.json"
            // Si ya hay una de hoy, se sustituye, pero solo después de escribir la nueva (si falla, se conserva la anterior)
            val sameDay = children(app, tree).filter { it.second == name }.map { it.first }
            val file = DocumentsContract.createDocument(resolver, dir, "application/json", name)
                ?: error(tr("No se pudo crear el fichero", "Couldn't create the file"))
            try {
                resolver.openOutputStream(file, "wt")!!.use { it.write(json.toByteArray()) }
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, file) }
                throw e
            }
            sameDay.forEach { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            if (sameDay.isNotEmpty()) runCatching { DocumentsContract.renameDocument(resolver, file, name) }
            children(app, tree)
                .filter { it.second.startsWith(PREFIX) }
                .sortedByDescending { it.second }
                .drop(KEEP)
                .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.first) } }
        }

        private fun children(context: Context, tree: Uri): List<Pair<Uri, String>> {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val out = mutableListOf<Pair<Uri, String>>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to c.getString(1)
                }
            }
            return out
        }
    }
}
