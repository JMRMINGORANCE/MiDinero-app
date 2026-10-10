package com.midinero.control.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midinero.control.domain.MerchantStat
import com.midinero.control.domain.dateOf
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.tr
import com.midinero.control.ui.MainViewModel
import com.midinero.control.ui.theme.MoneyStyle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Dónde se va el dinero: los comercios de más a menos gasto, con cuántas veces, el ticket medio y la última visita. */
@Composable
fun MerchantsScreen(vm: MainViewModel, onBack: () -> Unit, onOpenMerchant: (String) -> Unit) {
    val periods = listOf(30 to tr("30 días", "30 days"), 90 to tr("3 meses", "3 months"), 365 to tr("1 año", "1 year"), 0 to tr("Todo", "All"))
    var selected by rememberSaveable { mutableIntStateOf(1) }
    val days = periods[selected].first
    val flow = remember(days) { vm.merchantRanking(days) }
    val ranking by flow.collectAsState(initial = null)
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    val byId = remember(categories) { categories.associateBy { it.id } }
    val list = ranking.orEmpty()
    val total = list.sumOf { it.totalCents }.coerceAtLeast(1)

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(
                tr("Comercios", "Merchants"),
                subtitle = tr("Dónde se va tu dinero, de más a menos.", "Where your money goes, from most to least."),
                onBack = onBack,
            )
        }
        item { SegmentedTabs(periods.map { it.second }, selected, { selected = it }) }
        if (ranking != null && list.isEmpty()) {
            item { Text(tr("No hay gastos en este periodo.", "No expenses in this period."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (list.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(tr("Comercios", "Merchants"), list.size.toString(), null, Modifier.weight(1f))
                    val top = list.first()
                    StatTile(
                        tr("Donde más", "Top"), top.name, "${formatCents(top.totalCents)} · ${top.totalCents * 100 / total} %",
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        list.take(100).forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            MerchantRankRow(i + 1, m, m.totalCents.toFloat() / list.first().totalCents, byId[m.categoryId]) { onOpenMerchant(m.key) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MerchantRankRow(position: Int, m: MerchantStat, share: Float, category: com.midinero.control.data.Category?, onClick: () -> Unit) {
    val ago = ChronoUnit.DAYS.between(dateOf(m.lastTimestamp), LocalDate.now())
    val last = when (ago) {
        0L -> tr("hoy", "today")
        1L -> tr("ayer", "yesterday")
        else -> tr("hace $ago días", "$ago days ago")
    }
    val times = if (m.count == 1) tr("1 vez", "once") else tr("${m.count} veces", "${m.count} times")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$position. ${m.name}, ${formatCents(m.totalCents)}, $times" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantBadge(m.name, category, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatCents(m.totalCents), style = MaterialTheme.typography.titleSmall.merge(MoneyStyle), fontWeight = FontWeight.Bold)
            }
            Text(
                tr("$times · media ${formatCents(m.averageCents)} · $last", "$times · avg ${formatCents(m.averageCents)} · $last"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
