package com.midinero.control.domain

import java.text.NumberFormat

/** Panel al que van los gastos de una categoría (se guarda en Category.kind). */
enum class TrackerKind(val emoji: String, private val es: String, private val en: String) {
    FUEL("⛽", "Combustible", "Fuel"),
    ELECTRICITY("💡", "Luz", "Electricity"),
    GAS("🔥", "Gas", "Gas"),
    WATER("🚿", "Agua", "Water"),
    CAR("🔧", "Coche: mantenimiento, ITV, impuesto…", "Car: servicing, MOT, tax…");

    val label: String get() = tr(es, en)

    /** Nombre corto para una categoría nueva de este panel. */
    val categoryName: String get() = when (this) {
        CAR -> tr("Mantenimiento coche", "Car servicing")
        else -> label
    }

    companion object {
        fun of(value: String?): TrackerKind? = value?.let { v -> entries.firstOrNull { it.name == v } }
    }
}

/** Qué se le ha hecho al coche en un gasto de mantenimiento. */
enum class ServiceType(val emoji: String, private val es: String, private val en: String) {
    OIL("🛢️", "Aceite y filtros", "Oil and filters"),
    TYRES("🛞", "Ruedas", "Tyres"),
    BRAKES("🛑", "Frenos", "Brakes"),
    BATTERY("🔋", "Batería", "Battery"),
    ITV("📋", "ITV", "MOT"),
    TAX("🏛️", "Impuesto de circulación", "Road tax"),
    REPAIR("🔧", "Reparación", "Repair"),
    OTHER("🧽", "Otros", "Other");

    val label: String get() = tr(es, en)

    companion object {
        fun of(value: String): ServiceType? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Datos extra de un gasto según su panel: litros y km de un repostaje, kWh de la luz por tramos, m³ de agua,
 * km del coche y qué se le hizo… Todo opcional. Se guarda como texto corto en Expense.details («l=40.5;km=620»).
 */
data class ExpenseDetails(
    val liters: Double? = null,
    /** Km que ha durado este repostaje (hasta el siguiente). */
    val tripKm: Double? = null,
    /** Km que marca el coche. */
    val odometerKm: Long? = null,
    val vehicleId: Long? = null,
    val kwhPeak: Double? = null,
    val kwhFlat: Double? = null,
    val kwhValley: Double? = null,
    /** kWh vertidos a la red (placas solares). */
    val kwhExported: Double? = null,
    /** kWh de gas. */
    val kwh: Double? = null,
    val m3: Double? = null,
    val services: Set<ServiceType> = emptySet(),
) {
    val isEmpty: Boolean get() = this == EMPTY

    /** kWh de luz consumidos (los tres tramos). */
    val kwhConsumed: Double? get() = listOfNotNull(kwhPeak, kwhFlat, kwhValley).takeIf { it.isNotEmpty() }?.sum()

    fun encode(): String? {
        if (isEmpty) return null
        val parts = mutableListOf<String>()
        fun add(key: String, value: Double?) { if (value != null) parts += "$key=${num(value)}" }
        add("l", liters)
        add("km", tripKm)
        odometerKm?.let { parts += "odo=$it" }
        vehicleId?.let { parts += "car=$it" }
        add("kp", kwhPeak)
        add("kl", kwhFlat)
        add("kv", kwhValley)
        add("kx", kwhExported)
        add("kwh", kwh)
        add("m3", m3)
        if (services.isNotEmpty()) parts += "svc=" + services.sortedBy { it.ordinal }.joinToString(",") { it.name }
        return parts.joinToString(";")
    }

    companion object {
        val EMPTY = ExpenseDetails()

        fun parse(text: String?): ExpenseDetails {
            if (text.isNullOrBlank()) return EMPTY
            val map = text.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            fun d(key: String) = map[key]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
            return ExpenseDetails(
                liters = d("l"),
                tripKm = d("km"),
                odometerKm = map["odo"]?.toLongOrNull()?.takeIf { it >= 0 },
                vehicleId = map["car"]?.toLongOrNull(),
                kwhPeak = d("kp"),
                kwhFlat = d("kl"),
                kwhValley = d("kv"),
                kwhExported = d("kx"),
                kwh = d("kwh"),
                m3 = d("m3"),
                services = map["svc"].orEmpty().split(',').mapNotNull { ServiceType.of(it.trim()) }.toSet(),
            )
        }

        /** Número sin ceros de más y con punto, para guardarlo: 40.5, 620. */
        private fun num(value: Double): String {
            val rounded = Math.round(value * 1000) / 1000.0
            return if (rounded == Math.floor(rounded) && rounded < 1e12) rounded.toLong().toString() else rounded.toString()
        }
    }
}

/** Lo que escribe el usuario («40,5», «1.234,5», «620») a número; null si no es un número válido. */
fun parseQuantity(text: String): Double? {
    val t = text.trim().replace(" ", "")
    if (t.isEmpty()) return null
    val normalized = when {
        t.contains(',') && t.contains('.') -> if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        t.contains(',') -> t.replace(',', '.')
        // «1.234» con tres cifras detrás del punto son miles
        Regex("""\d{1,3}(\.\d{3})+""").matches(t) -> t.replace(".", "")
        else -> t
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

/** Número para mostrar en el idioma de la app con [decimals] decimales como mucho: «40,5», «1.234». */
fun formatQuantity(value: Double, decimals: Int = 1): String =
    NumberFormat.getNumberInstance(Lang.locale).apply {
        maximumFractionDigits = decimals
        minimumFractionDigits = 0
    }.format(value)

/** Número para el campo de texto del editor (sin separador de miles): «40,5». */
fun quantityToInput(value: Double?): String {
    if (value == null) return ""
    return NumberFormat.getNumberInstance(Lang.locale).apply {
        maximumFractionDigits = 3
        isGroupingUsed = false
    }.format(value)
}

/** Precio por unidad con más decimales que un importe normal: «1,559 €» por litro, «0,1432 €» por kWh. */
fun formatUnitPrice(centsPerUnit: Double, decimals: Int = 3): String {
    if (amountsHidden()) return HIDDEN_AMOUNT
    return NumberFormat.getCurrencyInstance(Lang.locale).apply {
        currency = java.util.Currency.getInstance(Currencies.base)
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(centsPerUnit / 100.0)
}
