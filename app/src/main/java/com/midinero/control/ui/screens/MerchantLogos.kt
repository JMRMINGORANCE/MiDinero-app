package com.midinero.control.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/** Logo descargado de un comercio. [fullBleed]: ocupa todo el cuadrado (icono de app con su propio fondo). */
class MerchantLogoImage(val image: ImageBitmap, val fullBleed: Boolean)

/**
 * Logos de los comercios conocidos, que van dentro de la app (assets/logos, los descarga el workflow
 * «Logos de comercios»). Si un comercio no tiene logo se sigue usando su distintivo de color con siglas.
 */
object MerchantLogos {
    private val cache = ConcurrentHashMap<String, Any>()
    private val missing = Any()

    /** «El Corte Inglés» → «el_corte_ingles» (igual que tools/logos/fetch_logos.py). */
    fun key(name: String): String =
        Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    fun get(context: Context, name: String): MerchantLogoImage? {
        val k = key(name)
        val cached = cache.getOrPut(k) { load(context, k) ?: missing }
        return cached as? MerchantLogoImage
    }

    private fun load(context: Context, key: String): MerchantLogoImage? = runCatching {
        context.assets.open("logos/$key.webp").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()?.let { MerchantLogoImage(it.asImageBitmap(), isFullBleed(it)) }

    /** Si las cuatro esquinas son opacas, el logo trae su propio fondo y se dibuja a sangre. */
    private fun isFullBleed(b: Bitmap): Boolean {
        val m = (b.width * 0.08f).toInt().coerceAtLeast(1)
        val corners = listOf(b.getPixel(m, m), b.getPixel(b.width - 1 - m, m), b.getPixel(m, b.height - 1 - m), b.getPixel(b.width - 1 - m, b.height - 1 - m))
        return corners.all { (it ushr 24) > 230 }
    }
}
