package com.midinero.control.domain

import com.midinero.control.data.Frequency
import com.midinero.control.data.RecurringExpense
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object Recurrence {

    /** Bit de un día de la semana en [RecurringExpense.weekdays] (lunes = 1, martes = 2… domingo = 64). */
    fun bit(day: java.time.DayOfWeek): Int = 1 shl (day.value - 1)

    /** Días elegidos, de lunes a domingo. */
    fun days(weekdays: Int): List<java.time.DayOfWeek> = java.time.DayOfWeek.entries.filter { weekdays and bit(it) != 0 }

    /** Cuántas veces se cobra a la semana. */
    fun timesPerWeek(frequency: Frequency, weekdays: Int): Int =
        if (frequency == Frequency.WEEKLY && weekdays != 0) Integer.bitCount(weekdays and 0x7F) else 1

    /** El primer día elegido que cae en [date] o después (para empezar un semanal de varios días). */
    fun alignToWeekdays(date: LocalDate, weekdays: Int): LocalDate {
        if (weekdays and 0x7F == 0) return date
        var d = date
        while (weekdays and bit(d.dayOfWeek) == 0) d = d.plusDays(1)
        return d
    }

    /** Fecha del siguiente cargo después de [date], respetando el día del mes (o los días de la semana) elegidos. */
    fun next(date: LocalDate, frequency: Frequency, dayOfMonth: Int, weekdays: Int = 0): LocalDate {
        val ym = when (frequency) {
            Frequency.WEEKLY -> return if (weekdays and 0x7F == 0) date.plusWeeks(1) else alignToWeekdays(date.plusDays(1), weekdays)
            Frequency.MONTHLY -> YearMonth.from(date).plusMonths(1)
            Frequency.QUARTERLY -> YearMonth.from(date).plusMonths(3)
            Frequency.YEARLY -> YearMonth.from(date).plusYears(1)
        }
        return ym.atDay(dayOfMonth.coerceIn(1, ym.lengthOfMonth()))
    }

    /** Primera fecha de cargo que cae hoy o más tarde (para reactivar sin apuntar meses atrasados). */
    fun firstOnOrAfter(start: LocalDate, today: LocalDate, frequency: Frequency, dayOfMonth: Int, weekdays: Int = 0): LocalDate {
        var d = start
        var guard = 0
        while (d < today && guard < 5000) {
            d = next(d, frequency, dayOfMonth, weekdays)
            guard++
        }
        return d
    }

    /** Coste mensual equivalente (un gasto anual cuenta como 1/12 al mes). */
    fun monthlyEquivalentCents(r: RecurringExpense): Long = monthlyEquivalentCents(r.amountCents * timesPerWeek(r.frequency, r.weekdays), r.frequency)

    fun monthlyEquivalentCents(amountCents: Long, frequency: Frequency): Long = when (frequency) {
        Frequency.WEEKLY -> amountCents * 52 / 12
        Frequency.MONTHLY -> amountCents
        Frequency.QUARTERLY -> amountCents / 3
        Frequency.YEARLY -> amountCents / 12
    }

    /** Coste en un año. */
    fun yearlyCents(r: RecurringExpense): Long = when (r.frequency) {
        Frequency.WEEKLY -> r.amountCents * 52 * timesPerWeek(r.frequency, r.weekdays)
        Frequency.MONTHLY -> r.amountCents * 12
        Frequency.QUARTERLY -> r.amountCents * 4
        Frequency.YEARLY -> r.amountCents
    }

    fun periodLabel(frequency: Frequency): String = when (frequency) {
        Frequency.WEEKLY -> tr("/semana", "/week")
        Frequency.MONTHLY -> tr("/mes", "/month")
        Frequency.QUARTERLY -> tr("/trimestre", "/quarter")
        Frequency.YEARLY -> tr("/año", "/year")
    }

    private val dayMonth get() = Lang.dateFormat("d 'de' MMMM", "d MMMM")

    private val weekday get() = Lang.dateFormat("EEEE")

    fun describe(r: RecurringExpense): String {
        val base = when (r.frequency) {
            Frequency.WEEKLY -> if (r.weekdays and 0x7F != 0) tr("Cada ", "Every ") + weekdaysLabel(r.weekdays)
                else tr("Cada semana, los ${LocalDate.ofEpochDay(r.nextDueEpochDay).format(weekday)}", "Every week on ${LocalDate.ofEpochDay(r.nextDueEpochDay).format(weekday)}")
            Frequency.MONTHLY -> tr("Cada mes, el día ${r.dayOfMonth}", "Every month on day ${r.dayOfMonth}")
            Frequency.QUARTERLY -> tr("Cada 3 meses, el día ${r.dayOfMonth}", "Every 3 months on day ${r.dayOfMonth}")
            Frequency.YEARLY -> tr("Cada año, el ${LocalDate.ofEpochDay(r.nextDueEpochDay).format(dayMonth)}", "Every year on ${LocalDate.ofEpochDay(r.nextDueEpochDay).format(dayMonth)}")
        }
        val left = r.remainingCharges
        return if (left != null) tr("$base · quedan $left", "$base · $left left") else base
    }

    /** «L, X, V y D» (o «Mon, Wed, Fri and Sun»). */
    fun weekdaysLabel(weekdays: Int): String {
        val names = days(weekdays).map { d ->
            if (Lang.english) d.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
            else listOf("L", "M", "X", "J", "V", "S", "D")[d.value - 1]
        }
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + tr(" y ", " and ") + names.last()
        }
    }

    fun nextLabel(r: RecurringExpense, today: LocalDate = LocalDate.now()): String {
        val next = LocalDate.ofEpochDay(r.nextDueEpochDay)
        val days = next.toEpochDay() - today.toEpochDay()
        return when {
            days <= 0L -> tr("Hoy", "Today")
            days == 1L -> tr("Mañana", "Tomorrow")
            days < 7L -> tr("En $days días (${next.format(dayMonth)})", "In $days days (${next.format(dayMonth)})")
            else -> next.format(dayMonth)
        }
    }
}

/** Plantillas rápidas para añadir recurrentes típicos. El precio es orientativo y editable. */
data class RecurringTemplate(
    private val es: String,
    val emoji: String,
    val categoryName: String,
    val amountCents: Long?,
    private val en: String = es,
) {
    val name: String get() = tr(es, en)
}

val recurringTemplates: List<RecurringTemplate> = listOf(
    RecurringTemplate("Alquiler cochera", "🚗", "Hogar", null, en = "Parking rent"),
    RecurringTemplate("Alquiler vivienda", "🏠", "Hogar", null, en = "Rent"),
    RecurringTemplate("Netflix", "🎬", "Suscripciones", null),
    RecurringTemplate("Spotify", "🎧", "Suscripciones", null),
    RecurringTemplate("Apple Music", "🎵", "Suscripciones", null),
    RecurringTemplate("HBO Max", "📺", "Suscripciones", null),
    RecurringTemplate("Disney+", "🏰", "Suscripciones", null),
    RecurringTemplate("Amazon Prime", "📦", "Suscripciones", null),
    RecurringTemplate("iCloud", "☁️", "Suscripciones", null),
    RecurringTemplate("Gimnasio", "🏋️", "Ocio", null, en = "Gym"),
    RecurringTemplate("Móvil", "📱", "Hogar", null, en = "Mobile phone"),
    RecurringTemplate("Internet / fibra", "🌐", "Hogar", null, en = "Internet / broadband"),
    RecurringTemplate("Luz", "💡", "Hogar", null, en = "Electricity"),
    RecurringTemplate("Seguro del coche", "🛡️", "Transporte", null, en = "Car insurance"),
    RecurringTemplate("Cuota del coche", "🚙", "Transporte", null, en = "Car payment"),
    RecurringTemplate("Seguro de hogar", "🏡", "Hogar", null, en = "Home insurance"),
    RecurringTemplate("Comunidad", "🏢", "Hogar", null, en = "Service charge"),
    RecurringTemplate("Agua", "🚿", "Hogar", null, en = "Water"),
    RecurringTemplate("YouTube Premium", "▶️", "Suscripciones", null),
    RecurringTemplate("ChatGPT / Claude", "🤖", "Suscripciones", null),
)
