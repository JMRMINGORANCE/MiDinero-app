package com.midinero.control.domain

import com.midinero.control.data.Category
import com.midinero.control.data.Expense
import java.time.LocalDate
import java.time.YearMonth

/** Un gasto de un panel (recibo de la luz, repostaje, mantenimiento…) con sus datos extra ya leídos. */
data class MeterEntry(val expense: Expense, val date: LocalDate, val details: ExpenseDetails) {
    val cents: Long get() = expense.amountCents
}

/** Repostaje con sus cifras: precio por litro, consumo y coste cada 100 km (las que se puedan calcular). */
data class FuelFill(
    val entry: MeterEntry,
    val liters: Double?,
    val km: Double?,
    /** Céntimos por litro. */
    val pricePerLiter: Double?,
    val litersPer100: Double?,
    /** Céntimos cada 100 km. */
    val costPer100: Double?,
)

/** Resumen de combustible de un periodo. Los promedios solo usan los repostajes que tienen el dato. */
data class FuelSummary(
    val totalCents: Long,
    val fills: List<FuelFill>,
    val liters: Double,
    val km: Double,
    val pricePerLiter: Double?,
    val litersPer100: Double?,
    val costPer100: Double?,
)

/** Totales de un mes de un panel de suministros (luz, gas, agua). */
data class MeterMonth(
    val month: YearMonth,
    val cents: Long,
    val bills: Int,
    val kwhPeak: Double = 0.0,
    val kwhFlat: Double = 0.0,
    val kwhValley: Double = 0.0,
    val kwhExported: Double = 0.0,
    val kwh: Double = 0.0,
    val m3: Double = 0.0,
) {
    val kwhConsumed: Double get() = kwhPeak + kwhFlat + kwhValley
}

object Meters {
    /** Categorías de un panel. */
    fun categoriesOf(kind: TrackerKind, categories: List<Category>): List<Category> = categories.filter { it.kind == kind.name }

    /** Gastos (no ingresos) de las categorías de [kind], del más antiguo al más reciente. */
    fun entries(kind: TrackerKind, categories: List<Category>, expenses: List<Expense>): List<MeterEntry> {
        val ids = categoriesOf(kind, categories).map { it.id }.toSet()
        if (ids.isEmpty()) return emptyList()
        return expenses.asSequence()
            .filter { !it.isIncome && it.categoryId in ids }
            .map { MeterEntry(it, dateOf(it.timestamp), ExpenseDetails.parse(it.details)) }
            .sortedWith(compareBy({ it.date }, { it.expense.timestamp }))
            .toList()
    }

    fun inRange(entries: List<MeterEntry>, range: DateRange?): List<MeterEntry> =
        if (range == null) entries else entries.filter { range.contains(it.date) }

    fun fill(e: MeterEntry): FuelFill {
        val liters = e.details.liters?.takeIf { it > 0 }
        val km = e.details.tripKm?.takeIf { it > 0 }
        val cents = e.cents.toDouble()
        return FuelFill(
            entry = e,
            liters = liters,
            km = km,
            pricePerLiter = liters?.let { cents / it },
            litersPer100 = if (liters != null && km != null) liters / km * 100 else null,
            costPer100 = km?.let { cents / it * 100 },
        )
    }

    /**
     * Si un repostaje no tiene apuntados los km que le duró pero él y el siguiente del mismo coche tienen
     * los km del cuentakilómetros, se sacan de la diferencia.
     */
    fun withDerivedKm(entries: List<MeterEntry>): List<MeterEntry> {
        val result = entries.toMutableList()
        val lastIndex = HashMap<Long?, Int>()
        entries.forEachIndexed { i, e ->
            val odo = e.details.odometerKm ?: return@forEachIndexed
            lastIndex[e.details.vehicleId]?.let { p ->
                val prev = result[p]
                val prevOdo = prev.details.odometerKm!!
                if (prev.details.tripKm == null && odo > prevOdo) result[p] = prev.copy(details = prev.details.copy(tripKm = (odo - prevOdo).toDouble()))
            }
            lastIndex[e.details.vehicleId] = i
        }
        return result
    }

    /**
     * El repostaje anterior a [timestamp] (del mismo coche si se ha elegido uno), sin contar [excludeId]:
     * al apuntar uno nuevo, a ese se le anotan los km que duró.
     */
    fun previousFill(fills: List<Expense>, timestamp: Long, vehicleId: Long?, excludeId: Long? = null): Expense? =
        fills.asSequence()
            .filter { it.id != excludeId && !it.isIncome && it.timestamp < timestamp }
            .filter { vehicleId == null || ExpenseDetails.parse(it.details).vehicleId.let { v -> v == null || v == vehicleId } }
            .maxByOrNull { it.timestamp }

    fun fuel(entries: List<MeterEntry>): FuelSummary {
        val fills = withDerivedKm(entries).map(::fill)
        val withLiters = fills.filter { it.liters != null }
        val withKm = fills.filter { it.km != null }
        val withBoth = fills.filter { it.liters != null && it.km != null }
        val litersWithKm = withBoth.sumOf { it.liters!! }
        val kmWithLiters = withBoth.sumOf { it.km!! }
        val kmTotal = withKm.sumOf { it.km!! }
        return FuelSummary(
            totalCents = entries.sumOf { it.cents },
            fills = fills,
            liters = withLiters.sumOf { it.liters!! },
            km = kmTotal,
            pricePerLiter = withLiters.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.entry.cents }.toDouble() / l.sumOf { it.liters!! } },
            litersPer100 = if (kmWithLiters > 0) litersWithKm / kmWithLiters * 100 else null,
            costPer100 = if (kmTotal > 0) withKm.sumOf { it.entry.cents }.toDouble() / kmTotal * 100 else null,
        )
    }

    /**
     * Si el último repostaje gastó bastante más que la media de los anteriores (al menos 3 con consumo),
     * devuelve cuánto más en tanto por uno (0.18 = un 18 % más). Puede ser hora de revisar el coche.
     */
    fun consumptionAlert(fills: List<FuelFill>, threshold: Double = 0.15): Double? {
        val measured = fills.filter { it.litersPer100 != null }
        if (measured.size < 4) return null
        val last = measured.last().litersPer100!!
        val previous = measured.dropLast(1).takeLast(6).map { it.litersPer100!! }
        val avg = previous.average()
        if (avg <= 0) return null
        val over = last / avg - 1
        return over.takeIf { it >= threshold }
    }

    /** Suma por mes natural de los recibos de un suministro (luz, gas, agua), del más antiguo al más reciente. */
    fun monthly(entries: List<MeterEntry>): List<MeterMonth> =
        entries.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (month, list) ->
            MeterMonth(
                month = month,
                cents = list.sumOf { it.cents },
                bills = list.size,
                kwhPeak = list.sumOf { it.details.kwhPeak ?: 0.0 },
                kwhFlat = list.sumOf { it.details.kwhFlat ?: 0.0 },
                kwhValley = list.sumOf { it.details.kwhValley ?: 0.0 },
                kwhExported = list.sumOf { it.details.kwhExported ?: 0.0 },
                kwh = list.sumOf { it.details.kwh ?: 0.0 },
                m3 = list.sumOf { it.details.m3 ?: 0.0 },
            )
        }

    /** Cantidad del suministro en un recibo: kWh consumidos (luz), kWh (gas) o m³ (agua). */
    fun quantity(kind: TrackerKind, d: ExpenseDetails): Double? = when (kind) {
        TrackerKind.ELECTRICITY -> d.kwhConsumed
        TrackerKind.GAS -> d.kwh
        TrackerKind.WATER -> d.m3
        TrackerKind.FUEL -> d.liters
        TrackerKind.CAR, TrackerKind.HOME -> null
    }

    fun quantity(kind: TrackerKind, m: MeterMonth): Double = when (kind) {
        TrackerKind.ELECTRICITY -> m.kwhConsumed
        TrackerKind.GAS -> m.kwh
        TrackerKind.WATER -> m.m3
        else -> 0.0
    }

    /** Precio medio por unidad (céntimos por kWh o por m³) de los recibos que tienen la cantidad apuntada. */
    fun unitPrice(kind: TrackerKind, entries: List<MeterEntry>): Double? {
        val measured = entries.filter { (quantity(kind, it.details) ?: 0.0) > 0 }
        val qty = measured.sumOf { quantity(kind, it.details)!! }
        return if (qty > 0) measured.sumOf { it.cents }.toDouble() / qty else null
    }

    fun unit(kind: TrackerKind): String = when (kind) {
        TrackerKind.ELECTRICITY, TrackerKind.GAS -> "kWh"
        TrackerKind.WATER -> "m³"
        TrackerKind.FUEL -> "l"
        TrackerKind.CAR -> "km"
        TrackerKind.HOME -> ""
    }

    /**
     * Resumen de la luz para pegar en un comparador de tarifas (luzfija.es, la CNMC…):
     * consumo por tramos y excedentes de cada mes y el total, sin importes si están ocultos.
     */
    fun electricitySummary(months: List<MeterMonth>): String {
        val list = months.takeLast(12)
        if (list.isEmpty()) return ""
        val monthFormat = Lang.dateFormat("MMM yyyy")
        fun kwh(v: Double) = formatQuantity(v, 0) + " kWh"
        val lines = mutableListOf(tr("Consumo de luz (MiDinero)", "Electricity use (MiDinero)"))
        list.forEach { m ->
            val parts = mutableListOf(
                tr("punta", "peak") + " " + kwh(m.kwhPeak),
                tr("llano", "flat") + " " + kwh(m.kwhFlat),
                tr("valle", "off-peak") + " " + kwh(m.kwhValley),
            )
            if (m.kwhExported > 0) parts += tr("excedentes", "exported") + " " + kwh(m.kwhExported)
            parts += formatCents(m.cents)
            lines += "${m.month.atDay(1).format(monthFormat)}: " + parts.joinToString(" · ")
        }
        val total = list.fold(MeterMonth(list.first().month, 0, 0)) { acc, m ->
            acc.copy(
                cents = acc.cents + m.cents, bills = acc.bills + m.bills,
                kwhPeak = acc.kwhPeak + m.kwhPeak, kwhFlat = acc.kwhFlat + m.kwhFlat,
                kwhValley = acc.kwhValley + m.kwhValley, kwhExported = acc.kwhExported + m.kwhExported,
            )
        }
        lines += ""
        lines += tr("Total ${list.size} meses", "Total ${list.size} months") + ": " + listOf(
            tr("punta", "peak") + " " + kwh(total.kwhPeak),
            tr("llano", "flat") + " " + kwh(total.kwhFlat),
            tr("valle", "off-peak") + " " + kwh(total.kwhValley),
            tr("excedentes", "exported") + " " + kwh(total.kwhExported),
            formatCents(total.cents),
        ).joinToString(" · ")
        if (total.kwhConsumed > 0) {
            val pct = { v: Double -> formatQuantity(v / total.kwhConsumed * 100, 0) + " %" }
            lines += tr("Reparto", "Split") + ": " + tr("punta", "peak") + " ${pct(total.kwhPeak)} · " +
                tr("llano", "flat") + " ${pct(total.kwhFlat)} · " + tr("valle", "off-peak") + " ${pct(total.kwhValley)}"
        }
        return lines.joinToString("\n")
    }
}
