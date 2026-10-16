package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.Expense

/** Las categorías que más usas, para no enseñarlas todas de golpe al apuntar. */
object CategoryUsage {
    /** A partir de cuántas categorías el selector enseña solo las más usadas. */
    const val COMPACT_FROM = 12
    const val TOP = 8

    /** Cuántos movimientos tiene cada categoría en los últimos [days] días. */
    fun counts(expenses: List<Expense>, now: Long = System.currentTimeMillis(), days: Int = 180): Map<Long, Int> {
        val from = now - days * 24L * 3600 * 1000
        return expenses.asSequence().filter { it.timestamp >= from }.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
    }

    /**
     * Las [top] más usadas (a igualdad, en tu orden), y siempre la elegida aunque no lo sea.
     * Con pocas categorías, todas.
     */
    fun compact(categories: List<Category>, usage: Map<Long, Int>, selectedId: Long?, top: Int = TOP): List<Category> {
        if (categories.size < COMPACT_FROM) return categories
        val order = categories.withIndex().associate { it.value.id to it.index }
        val best = categories.sortedWith(compareByDescending<Category> { usage[it.id] ?: 0 }.thenBy { order[it.id] }).take(top)
        val selected = categories.firstOrNull { it.id == selectedId }
        return if (selected == null || selected in best) best else best + selected
    }
}
