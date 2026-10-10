package com.midinero.control.ui.screens

import com.midinero.control.domain.signedAmount
import com.midinero.control.ui.theme.parseHexColor
import com.midinero.control.ui.theme.MoneyStyle
import com.midinero.control.domain.Lang
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.midinero.control.domain.tr
import androidx.compose.foundation.clickable
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midinero.control.data.Category
import com.midinero.control.domain.formatCents
import com.midinero.control.ui.theme.BudgetColors

// Piezas de Inicio: tarjetas de categoría, accesos directos y últimos movimientos.

/** Tarjeta de una categoría, teñida de su color: icono, % del mes, nombre, gastado y, si tiene, su límite. */
@Composable
internal fun CategorySpendCard(spend: CategorySpend, monthTotal: Long, modifier: Modifier, onClick: () -> Unit) {
    val catColor = spend.category?.let { parseHexColor(it.colorHex) } ?: MaterialTheme.colorScheme.error
    val name = spend.category?.name ?: tr("Sin categoría", "Uncategorised")
    val dark = com.midinero.control.ui.theme.LocalDarkTheme.current
    AppCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = androidx.compose.ui.graphics.lerp(
            MaterialTheme.colorScheme.surfaceContainerLowest, catColor, if (dark) 0.16f else 0.11f,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(catColor, RoundedCornerShape(14.dp)).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) { Text(spend.category?.emoji ?: "❔", fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)) }
                Spacer(Modifier.weight(1f))
                if (monthTotal > 0 && spend.spentCents > 0) {
                    Text(
                        "${spend.spentCents * 100 / monthTotal} %",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedMoney(spend.spentCents, MaterialTheme.typography.titleLarge.merge(MoneyStyle))
            if (spend.hasLimit) {
                val color = limitColor(spend.ratio)
                val limit = spend.limitCents!!
                Spacer(Modifier.height(10.dp))
                BudgetBar(animatedRatio(spend.ratio, delayMillis = 200), color, height = 6)
                Spacer(Modifier.height(6.dp))
                val remaining = limit - spend.spentCents
                Text(
                    when {
                        remaining < 0 -> tr("Te has pasado ${formatCents(-remaining)}", "Over by ${formatCents(-remaining)}")
                        spend.ratio >= 0.8f -> tr("Quedan ${formatCents(remaining)}", "${formatCents(remaining)} left")
                        else -> tr("de ${formatCents(limit)}", "of ${formatCents(limit)}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (spend.ratio >= 0.8f) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (spend.category == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Toca para asignarles categoría", "Tap to assign a category"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Sin límite", "No limit"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}
/** Accesos directos redondos bajo la cabecera. */
@Composable
internal fun QuickActions(actions: List<QuickAction>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        actions.forEach { a ->
            val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Column(
                Modifier
                    .pressScale(source, 0.92f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = a.onClick)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(56.dp).background(a.color.copy(alpha = 0.14f), CircleShape).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) { Icon(a.icon, contentDescription = null, tint = a.color, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.height(6.dp))
                Text(a.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** Movimiento reciente en Inicio: distintivo, comercio, cuándo y cuánto. */
@Composable
internal fun RecentRow(e: com.midinero.control.data.Expense, category: Category?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantBadge(e.merchant, category, size = 40)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(relativeDay(com.midinero.control.domain.dateOf(e.timestamp)), category?.name).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            signedAmount(e),
            style = MaterialTheme.typography.titleSmall.merge(MoneyStyle),
            color = if (e.isIncome || e.isRefund) BudgetColors.ok else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** «Hoy», «Ayer» o el día corto («lun 22 sept»). */
internal fun relativeDay(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> tr("Hoy", "Today")
        today.minusDays(1) -> tr("Ayer", "Yesterday")
        else -> date.format(Lang.dateFormat("EEE d MMM", "EEE d MMM")).replace(".", "")
    }
}
