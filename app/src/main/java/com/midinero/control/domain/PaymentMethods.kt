package com.midinero.control.domain

import com.midinero.control.data.Account
import com.midinero.control.data.Expense

/** Gasto de un método de pago en un tramo. [methodId] null = sin método. */
data class MethodTotal(val methodId: Long?, val cents: Long, val count: Int)

/**
 * Métodos de pago (tarjeta de crédito BBVA, efectivo, transferencia HSBC…). Se guardan en la tabla de cuentas;
 * solo se usan su nombre y su icono.
 */
object PaymentMethods {
    /** Método para un pago detectado: el elegido para esa app de pago o, si no, el de por defecto (si sigue en uso). */
    fun forDetected(packageName: String?, byApp: Map<String, Long>, defaultId: Long?, methods: List<Account>): Long? {
        val usable = methods.filter { !it.archived }.map { it.id }.toSet()
        return packageName?.let { byApp[it] }?.takeIf { it in usable } ?: defaultId?.takeIf { it in usable }
    }

    /** Lo gastado con cada método (sin ingresos), del que más al que menos; lo que no tiene método va al final. */
    fun totals(expenses: List<Expense>): List<MethodTotal> =
        expenses.filter { !it.isIncome }.groupBy { it.accountId }
            .map { (id, list) -> MethodTotal(id, list.sumOf { it.amountCents }, list.size) }
            .sortedWith(compareBy<MethodTotal> { it.methodId == null }.thenByDescending { it.cents })
}
