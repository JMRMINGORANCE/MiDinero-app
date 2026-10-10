package com.midinero.control.domain

import java.text.Normalizer
import java.util.Locale

object DetectedDuplicates {
    fun sameMerchant(a: String, b: String): Boolean {
        fun key(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
        val left = key(a)
        return left.isNotEmpty() && left == key(b)
    }
}
