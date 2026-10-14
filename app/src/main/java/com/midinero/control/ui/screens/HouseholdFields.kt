package com.midinero.control.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.midinero.control.data.Vehicle
import com.midinero.control.domain.ExpenseDetails
import com.midinero.control.domain.ServiceType
import com.midinero.control.domain.TrackerKind
import com.midinero.control.domain.formatQuantity
import com.midinero.control.domain.formatUnitPrice
import com.midinero.control.domain.parseQuantity
import com.midinero.control.domain.shortLabel
import com.midinero.control.domain.quantityToInput
import com.midinero.control.domain.tr

/** Lo que se va escribiendo en los campos extra del editor (litros, km, kWh…), como texto. */
@Stable
class DetailsInput(
    private val initial: ExpenseDetails,
    liters: String = quantityToInput(initial.liters),
    tripKm: String = quantityToInput(initial.tripKm),
    odometer: String = initial.odometerKm?.toString().orEmpty(),
    vehicleId: Long? = initial.vehicleId,
    kwhPeak: String = quantityToInput(initial.kwhPeak),
    kwhFlat: String = quantityToInput(initial.kwhFlat),
    kwhValley: String = quantityToInput(initial.kwhValley),
    kwhExported: String = quantityToInput(initial.kwhExported),
    kwh: String = quantityToInput(initial.kwh),
    m3: String = quantityToInput(initial.m3),
    services: Set<ServiceType> = initial.services,
    previousKm: String = "",
) {
    var liters by mutableStateOf(liters)
    var tripKm by mutableStateOf(tripKm)
    var odometer by mutableStateOf(odometer)
    var vehicleId by mutableStateOf(vehicleId)
    var kwhPeak by mutableStateOf(kwhPeak)
    var kwhFlat by mutableStateOf(kwhFlat)
    var kwhValley by mutableStateOf(kwhValley)
    var kwhExported by mutableStateOf(kwhExported)
    var kwh by mutableStateOf(kwh)
    var m3 by mutableStateOf(m3)
    var services by mutableStateOf(services)
    /** Km que duró el repostaje anterior (se guardan en ese, no en este). */
    var previousKm by mutableStateOf(previousKm)

    /**
     * Los datos a guardar para un gasto de [kind]: los campos de ese panel sobre lo que ya tenía el gasto
     * (lo de otros paneles no se pierde). Sin panel, se queda lo que tenía.
     */
    fun build(kind: TrackerKind?): String? {
        if (kind == null) return initial.encode()
        val d = when (kind) {
            TrackerKind.FUEL -> initial.copy(
                liters = parseQuantity(liters), tripKm = parseQuantity(tripKm),
                odometerKm = parseQuantity(odometer)?.toLong(), vehicleId = vehicleId,
            )
            TrackerKind.ELECTRICITY -> initial.copy(
                kwhPeak = parseQuantity(kwhPeak), kwhFlat = parseQuantity(kwhFlat),
                kwhValley = parseQuantity(kwhValley), kwhExported = parseQuantity(kwhExported),
            )
            TrackerKind.GAS -> initial.copy(kwh = parseQuantity(kwh))
            TrackerKind.WATER -> initial.copy(m3 = parseQuantity(m3))
            TrackerKind.CAR -> initial.copy(odometerKm = parseQuantity(odometer)?.toLong(), vehicleId = vehicleId, services = services)
        }
        return d.encode()
    }

    companion object {
        val Saver = listSaver<DetailsInput, String>(
            save = {
                listOf(
                    it.initial.encode().orEmpty(), it.liters, it.tripKm, it.odometer, it.vehicleId?.toString().orEmpty(),
                    it.kwhPeak, it.kwhFlat, it.kwhValley, it.kwhExported, it.kwh, it.m3, it.services.joinToString(",") { s -> s.name },
                    it.previousKm,
                )
            },
            restore = { l ->
                DetailsInput(
                    ExpenseDetails.parse(l[0]), l[1], l[2], l[3], l[4].toLongOrNull(), l[5], l[6], l[7], l[8], l[9], l[10],
                    l[11].split(',').mapNotNull { ServiceType.of(it) }.toSet(), l.getOrElse(12) { "" },
                )
            },
        )
    }
}

/** Estado de los campos extra; un gasto nuevo con un solo coche lo lleva ya elegido. */
@Composable
fun rememberDetailsInput(details: String?, vehicles: List<Vehicle>): DetailsInput =
    rememberSaveable(saver = DetailsInput.Saver) {
        val parsed = ExpenseDetails.parse(details)
        val active = vehicles.filter { !it.archived }
        DetailsInput(parsed, vehicleId = parsed.vehicleId ?: active.singleOrNull()?.id)
    }

/** Campos extra del editor según el panel de la categoría elegida. Todos opcionales. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsFields(
    kind: TrackerKind,
    input: DetailsInput,
    vehicles: List<Vehicle>,
    /** Importe del gasto, para calcular el precio del litro mientras se escribe. */
    amountCents: Long? = null,
    /** Repostaje anterior, al que se le anotan los km que duró (solo en uno nuevo). */
    previous: com.midinero.control.data.Expense? = null,
    editing: Boolean = false,
) {
    FieldLabel(
        when (kind) {
            TrackerKind.FUEL -> tr("Repostaje (opcional)", "Refuel (optional)")
            TrackerKind.ELECTRICITY -> tr("Consumo del recibo (opcional)", "Usage on the bill (optional)")
            TrackerKind.GAS, TrackerKind.WATER -> tr("Consumo del recibo (opcional)", "Usage on the bill (optional)")
            TrackerKind.CAR -> tr("Coche (opcional)", "Car (optional)")
        }
    )
    val active = vehicles.filter { !it.archived || it.id == input.vehicleId }
    if ((kind == TrackerKind.FUEL || kind == TrackerKind.CAR) && active.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            active.forEach { v ->
                FilterChip(
                    selected = input.vehicleId == v.id,
                    onClick = { input.vehicleId = if (input.vehicleId == v.id) null else v.id },
                    label = { Text("${v.emoji} ${v.name}") },
                    shape = CircleShape,
                    colors = sheetChipColors(),
                    border = null,
                )
            }
        }
    }
    when (kind) {
        TrackerKind.FUEL -> {
            val liters = parseQuantity(input.liters)?.takeIf { it > 0 }
            NumberField(
                input.liters, { input.liters = it }, tr("Litros servidos", "Litres filled"), Modifier.fillMaxWidth(),
                hint = if (liters != null && amountCents != null && amountCents > 0)
                    tr("Sale a ${formatUnitPrice(amountCents / liters)} el litro", "That's ${formatUnitPrice(amountCents / liters)} per litre")
                else tr("Con el importe, el precio del litro sale solo", "With the amount, the price per litre is worked out"),
            )
            if (editing) {
                NumberField(
                    input.tripKm, { input.tripKm = it }, tr("Km que duró este repostaje", "Km this refuel lasted"), Modifier.fillMaxWidth(),
                    hint = tr("Se rellena solo al apuntar el siguiente repostaje", "Filled in when you log the next refuel"),
                )
            } else if (previous != null) {
                val prev = ExpenseDetails.parse(previous.details)
                val desc = listOfNotNull(
                    com.midinero.control.domain.dateOf(previous.timestamp).shortLabel(),
                    prev.liters?.let { formatQuantity(it, 2) + " l" },
                ).joinToString(", ")
                NumberField(
                    input.previousKm, { input.previousKm = it }, tr("Km que duró el repostaje anterior", "Km the previous refuel lasted"), Modifier.fillMaxWidth(),
                    hint = tr("Se apuntan en el del $desc", "Saved on the one from $desc") +
                        (prev.tripKm?.let { tr(" (ya tiene ${formatQuantity(it, 0)} km)", " (it has ${formatQuantity(it, 0)} km)") } ?: ""),
                )
            }
            NumberField(
                input.odometer, { input.odometer = it }, tr("Km que marca el coche (opcional)", "Odometer, km (optional)"), Modifier.fillMaxWidth(),
                decimals = false,
                hint = tr("Si lo apuntas siempre, los km de cada repostaje salen solos", "If you always add it, each refuel's km are worked out"),
            )
        }
        TrackerKind.ELECTRICITY -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(input.kwhPeak, { input.kwhPeak = it }, tr("kWh punta", "Peak kWh"), Modifier.weight(1f))
                NumberField(input.kwhFlat, { input.kwhFlat = it }, tr("kWh llano", "Flat kWh"), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(input.kwhValley, { input.kwhValley = it }, tr("kWh valle", "Off-peak kWh"), Modifier.weight(1f))
                NumberField(input.kwhExported, { input.kwhExported = it }, tr("kWh excedentes", "Exported kWh"), Modifier.weight(1f))
            }
        }
        TrackerKind.GAS -> NumberField(input.kwh, { input.kwh = it }, tr("kWh consumidos", "kWh used"), Modifier.fillMaxWidth())
        TrackerKind.WATER -> NumberField(input.m3, { input.m3 = it }, tr("m³ consumidos", "m³ used"), Modifier.fillMaxWidth())
        TrackerKind.CAR -> {
            NumberField(input.odometer, { input.odometer = it }, tr("Km que marca el coche", "Odometer (km)"), Modifier.fillMaxWidth(), decimals = false)
            Text(tr("Qué se le ha hecho", "What was done"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ServiceType.entries.forEach { t ->
                    FilterChip(
                        selected = t in input.services,
                        onClick = { input.services = if (t in input.services) input.services - t else input.services + t },
                        label = { Text("${t.emoji} ${t.label}") },
                        shape = CircleShape,
                        colors = sheetChipColors(),
                        border = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    decimals: Boolean = true,
    hint: String? = null,
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimals && (it == ',' || it == '.')) }.take(10)) },
            label = { Text(label) },
            singleLine = true,
            isError = value.isNotBlank() && parseQuantity(value) == null,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
            shape = MaterialTheme.shapes.medium,
            colors = softFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            supportingText = hint?.let { { Text(it) } },
        )
    }
}
