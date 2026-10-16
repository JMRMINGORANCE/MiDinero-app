package com.midinero.control.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midinero.control.MiDineroApp
import com.midinero.control.data.Backup
import com.midinero.control.data.BackupData
import com.midinero.control.data.ExpenseRepository
import com.midinero.control.domain.CsvExport
import com.midinero.control.domain.CsvImport
import com.midinero.control.domain.CsvMapping
import com.midinero.control.domain.CsvPreview
import com.midinero.control.domain.tr
import com.midinero.control.service.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tus datos: copias de seguridad (manuales, automáticas y cifradas), exportar a CSV, importar del banco,
 * y borrar todo. Lo usan Ajustes e Importar.
 */
class DataViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as MiDineroApp).repository
    val prefs = (app as MiDineroApp).prefs

    // ---------- Tickets ----------

    /** Bytes que ocupan las fotos de tickets. */
    suspend fun receiptBytes(): Long = withContext(Dispatchers.IO) { repo.receipts.totalBytes() }

    private val resolver get() = getApplication<Application>().contentResolver

    fun exportBackup(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val data = repo.exportBackup()
                withContext(Dispatchers.IO) {
                    val text = withContext(Dispatchers.Default) { Backup.toFile(data, prefs.backupPassword) }
                    resolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
                data
            }
            if (result.isSuccess) prefs.lastBackupAt = System.currentTimeMillis()
            onDone(
                result.fold(
                    { tr("Copia guardada: ${it.expenses.size} movimientos, ${it.recurring.size} recurrentes", "Backup saved: ${it.expenses.size} transactions, ${it.recurring.size} recurring") },
                    { tr("No se ha podido guardar la copia", "Couldn't save the backup") },
                )
            )
        }
    }

    /** Copias automáticas en Descargas/MiDinero (para móviles que no dejan elegir carpeta). */
    fun setAutoBackupDownloads(onDone: (String) -> Unit) {
        prefs.autoBackupUri = BackupWorker.DOWNLOADS
        if (prefs.autoBackupDays == 0) prefs.autoBackupDays = 7
        prefs.lastAutoBackupError = null
        backupNow(onDone)
    }

    /** Guarda una copia en Descargas/MiDinero sin preguntar dónde (si el móvil no abre el selector de archivos). */
    fun exportToDownloads(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { BackupWorker.writeToDownloads(getApplication(), com.midinero.control.data.Profiles.backupPrefix(com.midinero.control.data.Profiles.current, "copia")) } }
            if (result.isSuccess) prefs.lastBackupAt = System.currentTimeMillis()
            onDone(result.fold({ tr("Copia guardada en Descargas/$it", "Backup saved to Downloads/$it") }, { (it as? IllegalStateException)?.message ?: tr("No se ha podido guardar la copia", "Couldn't save the backup") }))
        }
    }

    /** Guarda la carpeta para las copias automáticas y activa la copia semanal si estaba apagada. */
    fun setAutoBackupFolder(tree: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.autoBackupUri = tree.toString()
        if (prefs.autoBackupDays == 0) prefs.autoBackupDays = 7
        prefs.lastAutoBackupError = null
    }

    fun backupNow(onDone: (String) -> Unit) {
        val folder = prefs.autoBackupUri ?: return onDone(tr("Elige antes una carpeta", "Choose a folder first"))
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { BackupWorker.writeTo(getApplication(), folder) }
            }.isSuccess
            if (ok) {
                prefs.lastBackupAt = System.currentTimeMillis()
                prefs.lastAutoBackupError = null
            } else {
                prefs.lastAutoBackupError = tr("No se pudo guardar la copia automática. Vuelve a elegir la carpeta.", "Couldn't save the automatic backup. Choose the folder again.")
            }
            onDone(
                when {
                    !ok -> tr("No se pudo guardar en esa carpeta", "Couldn't save to that folder")
                    folder == BackupWorker.DOWNLOADS -> tr("Copia guardada en Descargas/MiDinero", "Backup saved to Downloads/MiDinero")
                    else -> tr("Copia guardada en tu carpeta", "Backup saved in your folder")
                },
            )
        }
    }

    /**
     * Lee una copia sin aplicarla, para pedir confirmación. Si está cifrada se prueba [password] o, si no se da,
     * la contraseña de las copias de este móvil; si no vale, el resultado es un [com.midinero.control.data.BackupPasswordException].
     */
    fun readBackup(uri: Uri, password: String? = null, onResult: (Result<BackupData>) -> Unit) {
        viewModelScope.launch {
            onResult(
                runCatching {
                    val text = withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    }
                    withContext(Dispatchers.Default) { Backup.fromFile(text, password ?: prefs.backupPassword) }
                }
            )
        }
    }

    /** Restaura la copia; si falla (disco lleno, copia dañada…) no se pierde nada y [onDone] recibe false. */
    fun restoreBackup(data: BackupData, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { repo.restoreBackup(data) }.isSuccess)
        }
    }

    fun exportCsv(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val data = repo.exportBackup()
                val csv = withContext(Dispatchers.Default) { CsvExport.build(data.expenses, data.categories, data.trips, data.vehicles, data.accounts) }
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                data.expenses.size
            }
            onDone(result.fold({ tr("Exportados $it movimientos", "Exported $it transactions") }, { tr("No se ha podido exportar", "Couldn't export") }))
        }
    }

    /** Lee un CSV del banco y prepara la vista previa. */
    fun readBankCsv(uri: Uri, onResult: (CsvPreview?) -> Unit) {
        viewModelScope.launch {
            val preview = runCatching {
                val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes() } }
                // Algunos bancos exportan en Windows-1252 (ñ y tildes rotas en UTF-8)
                val utf8 = bytes.toString(Charsets.UTF_8)
                val text = if (utf8.contains('\uFFFD')) bytes.toString(charset("windows-1252")) else utf8
                withContext(Dispatchers.Default) { CsvImport.preview(text) }
            }.getOrNull()
            onResult(preview)
        }
    }

    /** Importa los movimientos; [onDone] recibe null si algo falla (no se cierra la app). */
    fun importBankCsv(preview: CsvPreview, mapping: CsvMapping, includeIncome: Boolean, onDone: (ExpenseRepository.ImportResult?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val rows = withContext(Dispatchers.Default) { CsvImport.rows(preview, mapping) }
                repo.importRows(rows, includeIncome)
            }.getOrNull()
            onDone(result)
        }
    }

    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteAllData()
            onDone()
        }
    }
}
