package com.midinero.control.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.midinero.control.domain.ReceiptReading
import com.midinero.control.domain.ReceiptText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lee el texto de la foto de un ticket con el reconocimiento de texto de Google que va dentro de la app:
 * funciona sin Internet y la foto no sale del móvil.
 */
object ReceiptOcr {
    suspend fun read(context: Context, file: File): ReceiptReading? = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = try { recognizer.process(image).await() } finally { recognizer.close() }
            // Filas como en el papel: las líneas a la misma altura se juntan («TOTAL ........ 11,20»)
            val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
            val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
            lines.sortedBy { it.boundingBox!!.centerY() }.forEach { line ->
                val box = line.boundingBox!!
                val row = rows.lastOrNull()
                val ref = row?.first()?.boundingBox
                if (ref != null && kotlin.math.abs(ref.centerY() - box.centerY()) < maxOf(ref.height(), box.height()) / 2) row.add(line)
                else rows.add(mutableListOf(line))
            }
            val texts = rows.map { r -> r.sortedBy { it.boundingBox!!.left }.joinToString("  ") { it.text } }
            ReceiptText.parse(texts).takeIf { !it.isEmpty }
        }.getOrNull()
    }
}
