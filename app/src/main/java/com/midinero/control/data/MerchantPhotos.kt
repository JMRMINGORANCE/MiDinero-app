package com.midinero.control.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Tus comercios, siempre a mano para dibujar su foto en cualquier lista sin consultar la base de datos. */
object CustomMerchantsState {
    internal val mutable = MutableStateFlow<List<CustomMerchant>>(emptyList())
    val list: StateFlow<List<CustomMerchant>> = mutable
}

/** Las etiquetas (#cumpleaños…) que más usas, para ofrecerlas al apuntar. */
object TagsState {
    internal val mutable = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = mutable
}

/** Fotos de comercio: cuadradas, pequeñas (160 px) y en WebP, para guardarlas en la base de datos y en las copias. */
object MerchantPhotos {
    const val SIZE = 160

    /** Lee una imagen del móvil, la recorta al centro en cuadrado y la reduce. null si no se puede leer. */
    suspend fun fromUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(context, uri) ?: return@runCatching null
            val side = minOf(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            val small = if (side == SIZE) square else square.scale(SIZE, SIZE)
            val out = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            small.compress(format, 85, out)
            out.toByteArray()
        }.getOrNull()
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Respeta la orientación de las fotos de la cámara y no carga la imagen entera en memoria
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val side = minOf(info.size.width, info.size.height)
                if (side > SIZE * 3) {
                    val f = side / (SIZE * 3f)
                    decoder.setTargetSize((info.size.width / f).toInt().coerceAtLeast(1), (info.size.height / f).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }
}
