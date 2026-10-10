package com.midinero.control.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import com.midinero.control.domain.Receipts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fotos de tickets. Se guardan reducidas (lado largo de 1600 px, JPEG) en el almacenamiento
 * privado de la app; el gasto solo guarda el nombre del fichero.
 */
class ReceiptStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }

    fun file(name: String): File? = File(dir, name).takeIf { it.exists() }

    /** Copia reducida de una imagen (de la galería o de la cámara). Devuelve el nombre o null si no se pudo leer. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(uri) ?: return@runCatching null
            val name = "ticket_${System.currentTimeMillis()}.jpg"
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            bitmap.recycle()
            name
        }.getOrNull()
    }

    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder respeta la orientación EXIF de las fotos de la cámara
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = Receipts.targetSize(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = Receipts.sampleSize(bounds.outWidth, bounds.outHeight) }
        val sampled = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val (w, h) = Receipts.targetSize(sampled.width, sampled.height)
        return if (w == sampled.width && h == sampled.height) sampled else sampled.scale(w, h).also { sampled.recycle() }
    }

    /** Fichero temporal donde la cámara deja la foto antes de reducirla. */
    fun cameraTarget(): Uri {
        val tmp = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(tmp, "foto.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Para compartir el ticket con otra app (correo, WhatsApp...). */
    fun shareIntent(name: String): Intent? {
        val f = file(name) ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        return Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Borra las fotos que ya no usa ningún gasto. Devuelve cuántas.
     * Solo las de hace más de un día: así sobrevive la de un gasto recién borrado que se puede deshacer
     * o la de un editor que aún está abierto.
     */
    fun cleanOrphans(referenced: Set<String>, now: Long = System.currentTimeMillis()): Int {
        val names = dir.listFiles()?.filter { now - it.lastModified() > 86_400_000L }?.map { it.name }.orEmpty()
        val orphans = Receipts.orphans(names, referenced)
        orphans.forEach { File(dir, it).delete() }
        return orphans.size
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Espacio que ocupan las fotos, en bytes. */
    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}
