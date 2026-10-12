package com.midinero.control.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    @Test
    fun buscaEnEspanolYEnIngles() {
        assertTrue(EmojiCatalog.search("perro").any { it.emoji == "🐶" })
        assertTrue(EmojiCatalog.search("dog").any { it.emoji == "🐶" })
        assertTrue(EmojiCatalog.search("Gimnasio").any { it.emoji == "🏋️" })
        assertTrue(EmojiCatalog.search("electricidad").any { it.emoji == "💡" })
        assertTrue(EmojiCatalog.search("cafe").any { it.emoji == "☕" })
        assertTrue(EmojiCatalog.search("zzzz").isEmpty())
    }

    @Test
    fun hayMuchosYTodosTienenPalabras() {
        val all = EmojiCatalog.groups.flatMap { it.entries }
        assertTrue(all.size > 200)
        assertTrue(all.all { it.emoji.isNotBlank() && it.keywords.isNotBlank() && !it.emoji.contains(' ') })
    }
}
