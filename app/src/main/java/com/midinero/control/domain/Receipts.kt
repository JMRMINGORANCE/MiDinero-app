package com.midinero.control.domain

/** Cálculos para guardar las fotos de tickets pequeñas pero legibles. */
object Receipts {
    /** Lado largo máximo de la foto guardada: de sobra para leer un ticket. */
    const val MAX_SIDE = 1600

    /** Potencia de 2 para decodificar la imagen ya reducida sin cargarla entera en memoria. */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        var sample = 1
        val longSide = maxOf(width, height)
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    /** Tamaño final manteniendo proporciones, con el lado largo como mucho [maxSide]. */
    fun targetSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        val longSide = maxOf(width, height)
        if (longSide <= maxSide || longSide <= 0) return width to height
        val scale = maxSide.toDouble() / longSide
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /** Ficheros de tickets que ya no pertenecen a ningún gasto (para limpiarlos). */
    fun orphans(files: List<String>, referenced: Set<String>): List<String> = files.filter { it !in referenced }
}
