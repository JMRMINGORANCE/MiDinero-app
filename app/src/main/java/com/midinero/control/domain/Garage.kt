package com.midinero.control.domain

import com.midinero.control.data.Policy
import com.midinero.control.data.PolicyPrice
import com.midinero.control.data.Vehicle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Lo que ha costado un coche en un tramo, por tipo, y los km que ha hecho. */
data class VehicleSummary(
    val vehicle: Vehicle,
    val fuel: FuelSummary,
    /** Mantenimientos, ITV, impuesto… del tramo. */
    val services: List<MeterEntry>,
    val servicesCents: Long,
    /** Seguros de este coche cuyo precio empezó en el tramo. */
    val insuranceCents: Long,
    /** Últimos km que marca el coche (de cualquier gasto suyo, no solo del tramo). */
    val lastOdometer: Long?,
    /** Km del tramo: los de los repostajes, o la diferencia del cuentakilómetros si no hay. */
    val km: Double,
) {
    val totalCents: Long get() = fuel.totalCents + servicesCents + insuranceCents
    /** Céntimos por km recorrido. */
    val centsPerKm: Double? get() = if (km > 0) totalCents / km else null
}

enum class ReminderKind { OIL, ITV, POLICY }

/**
 * Aviso de algo que vence: cambio de aceite, ITV o renovación de un seguro.
 * Se avisa (una vez por [key]) a partir de [notifyFrom].
 */
data class Reminder(
    val key: String,
    val kind: ReminderKind,
    val refId: Long,
    val title: String,
    val dueDate: LocalDate,
    val notifyFrom: LocalDate,
    /** Para los seguros: último día para avisar a la compañía si te vas. */
    val cancelBy: LocalDate? = null,
) {
    fun daysLeft(today: LocalDate): Long = ChronoUnit.DAYS.between(today, dueDate)
    fun isActive(today: LocalDate): Boolean = !today.isBefore(notifyFrom)

    /** Texto del aviso, según cuánto falte. */
    fun message(today: LocalDate): String {
        val days = daysLeft(today)
        val f = Lang.dateFormat("d MMM", "d MMM")
        return when (kind) {
            ReminderKind.OIL -> if (days <= 0) tr("Toca cambiar el aceite (desde el ${dueDate.format(f)})", "Oil change due (since ${dueDate.format(f)})")
                else tr("Cambio de aceite en $days días: ve preparando el coche", "Oil change in $days days: get the car ready")
            ReminderKind.ITV -> when {
                days < 0 -> tr("La ITV venció el ${dueDate.format(f)}", "The MOT expired on ${dueDate.format(f)}")
                days == 0L -> tr("La ITV vence hoy", "The MOT expires today")
                else -> tr("ITV el ${dueDate.format(f)}, en $days días: pide cita", "MOT on ${dueDate.format(f)}, in $days days: book it")
            }
            ReminderKind.POLICY -> {
                val cancel = cancelBy
                when {
                    days < 0 -> tr("Venció el ${dueDate.format(f)}: apunta el precio nuevo", "Renewed on ${dueDate.format(f)}: add the new price")
                    cancel != null && !today.isAfter(cancel) ->
                        tr("Renueva el ${dueDate.format(f)}. Pide ya el precio y negocia: para irte, avisa antes del ${cancel.format(f)}",
                            "Renews on ${dueDate.format(f)}. Ask for the price and haggle: to leave, tell them before ${cancel.format(f)}")
                    else -> tr("Renueva el ${dueDate.format(f)}, en $days días", "Renews on ${dueDate.format(f)}, in $days days")
                }
            }
        }
    }
}

object Garage {
    fun summary(
        vehicle: Vehicle,
        fuelEntries: List<MeterEntry>,
        carEntries: List<MeterEntry>,
        policies: List<Policy>,
        prices: List<PolicyPrice>,
        range: DateRange?,
    ): VehicleSummary {
        val mine = { e: MeterEntry -> e.details.vehicleId == vehicle.id }
        val allFuel = fuelEntries.filter(mine)
        val allCar = carEntries.filter(mine)
        val fuel = Meters.fuel(Meters.inRange(allFuel, range))
        val services = Meters.inRange(allCar, range)
        val policyIds = policies.filter { it.vehicleId == vehicle.id }.map { it.id }.toSet()
        val insurance = prices.filter { it.policyId in policyIds && (range == null || range.contains(LocalDate.ofEpochDay(it.startEpochDay))) }
            .sumOf { it.amountCents }
        val readings = (allFuel + allCar).filter { it.details.odometerKm != null }.sortedBy { it.date }
        val inRangeReadings = readings.filter { range == null || range.contains(it.date) }.mapNotNull { it.details.odometerKm }
        val odoKm = if (inRangeReadings.size >= 2) (inRangeReadings.max() - inRangeReadings.min()).toDouble() else 0.0
        return VehicleSummary(
            vehicle = vehicle,
            fuel = fuel,
            services = services.reversed(),
            servicesCents = services.sumOf { it.cents },
            insuranceCents = insurance,
            lastOdometer = readings.lastOrNull()?.details?.odometerKm,
            km = if (fuel.km > 0) fuel.km else odoKm,
        )
    }

    /** Último gasto de [type] de un coche (el cambio de aceite más reciente, por ejemplo). */
    fun lastService(vehicleId: Long, type: ServiceType, carEntries: List<MeterEntry>): MeterEntry? =
        carEntries.filter { it.details.vehicleId == vehicleId && type in it.details.services }.maxByOrNull { it.date }

    /** Todos los avisos (activos o no), del que vence antes al que vence después. */
    fun reminders(vehicles: List<Vehicle>, carEntries: List<MeterEntry>, policies: List<Policy>): List<Reminder> {
        val list = mutableListOf<Reminder>()
        vehicles.filter { !it.archived }.forEach { v ->
            val months = v.oilReminderMonths
            if (months != null && months > 0) {
                lastService(v.id, ServiceType.OIL, carEntries)?.let { last ->
                    val due = last.date.plusMonths(months.toLong())
                    list += Reminder("oil-${v.id}-${last.date.toEpochDay()}", ReminderKind.OIL, v.id, "${v.emoji} ${v.name}", due, due)
                }
            }
            v.itvDueEpochDay?.let { day ->
                val due = LocalDate.ofEpochDay(day)
                list += Reminder("itv-${v.id}-$day", ReminderKind.ITV, v.id, "${v.emoji} ${v.name}", due, due.minusDays(v.itvNoticeDays.toLong().coerceAtLeast(0)))
            }
        }
        policies.filter { !it.archived }.forEach { p ->
            val due = LocalDate.ofEpochDay(p.renewalEpochDay)
            list += Reminder(
                "pol-${p.id}-${p.renewalEpochDay}", ReminderKind.POLICY, p.id, p.name, due,
                notifyFrom = due.minusDays(p.noticeDays.toLong().coerceAtLeast(0)),
                cancelBy = if (p.cancelDays > 0) due.minusDays(p.cancelDays.toLong()) else null,
            )
        }
        return list.sortedBy { it.dueDate }
    }

    /** Precio actual de un seguro (el último que empezó) y el anterior, para ver cuánto ha subido. */
    fun priceHistory(policyId: Long, prices: List<PolicyPrice>): List<PolicyPrice> =
        prices.filter { it.policyId == policyId }.sortedBy { it.startEpochDay }

    /** Subida (o bajada) del último precio frente al anterior, en tanto por uno. */
    fun lastChange(history: List<PolicyPrice>): Double? {
        if (history.size < 2) return null
        val prev = history[history.size - 2].amountCents
        return if (prev > 0) (history.last().amountCents - prev).toDouble() / prev else null
    }
}
