package com.midinero.control.domain

import com.midinero.control.data.Frequency
import com.midinero.control.data.RecurringExpense
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object Recurrence {

    /** Fecha del siguiente cargo después de [date], respetando el día del mes elegido. */
    fun next(date: LocalDate, frequency: Frequency, dayOfMonth: Int): LocalDate {
        val ym = when (frequency) {
            Frequency.WEEKLY -> return date.plusWeeks(1)
            Frequency.MONTHLY -> YearMonth.from(date).plusMonths(1)
            Frequency.QUARTERLY -> YearMonth.from(date).plusMonths(3)
            Frequency.YEARLY -> YearMonth.from(date).plusYears(1)
        }
        return ym.atDay(dayOfMonth.coerceIn(1, ym.lengthOfMonth()))
    }

    /** Primera fecha de cargo que cae hoy o más tarde (para reactivar sin apuntar meses atrasados). */
    fun firstOnOrAfter(start: LocalDate, today: LocalDate, frequency: Frequency, dayOfMonth: Int): LocalDate {
        var d = start
        var guard = 0
        while (d < today && guard < 1200) {
            d = next(d, frequency, dayOfMonth)
            guard++
        }
        return d
    }

    /** Coste mensual equivalente (un gasto anual cuenta como 1/12 al mes). */
    fun monthlyEquivalentCents(r: RecurringExpense): Long = monthlyEquivalentCents(r.amountCents, r.frequency)

    fun monthlyEquivalentCents(amountCents: Long, frequency: Frequency): Long = when (frequency) {
        Frequency.WEEKLY -> amountCents * 52 / 12
        Frequency.MONTHLY -> amountCents
        Frequency.QUARTERLY -> amountCents / 3
        Frequency.YEARLY -> amountCents / 12
    }

    /** Coste en un año. */
    fun yearlyCents(r: RecurringExpense): Long = when (r.frequency) {
        Frequency.WEEKLY -> r.amountCents * 52
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
            Frequency.WEEKLY -> tr("Cada semana, los ${LocalDate.ofEpochDay(r.nextDueEpochDay).format(weekday)}", "Every week on ${LocalDate.ofEpochDay(r.nextDueEpochDay).format(weekday)}")
            Frequency.MONTHLY -> tr("Cada mes, el día ${r.dayOfMonth}", "Every month on day ${r.dayOfMonth}")
            Frequency.QUARTERLY -> tr("Cada 3 meses, el día ${r.dayOfMonth}", "Every 3 months on day ${r.dayOfMonth}")
            Frequency.YEARLY -> tr("Cada año, el ${LocalDate.ofEpochDay(r.nextDueEpochDay).format(dayMonth)}", "Every year on ${LocalDate.ofEpochDay(r.nextDueEpochDay).format(dayMonth)}")
        }
        val left = r.remainingCharges
        return if (left != null) tr("$base · quedan $left", "$base · $left left") else base
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
