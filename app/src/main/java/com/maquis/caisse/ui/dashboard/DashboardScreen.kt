package com.maquis.caisse.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.WaitressStats
import com.maquis.caisse.ui.charts.CustomPeriodPickers
import com.maquis.caisse.ui.charts.PeriodSelector
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionCyan
import com.maquis.caisse.ui.theme.GestionSuccess
import com.maquis.caisse.ui.theme.GestionWarning

private val ChartPalette = listOf(
    GestionBlue,
    GestionCyan,
    GestionSuccess,
    GestionWarning,
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
)

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var selectedWaitressId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(
            title = "Tableau de bord",
            subtitle = "${ui.period.label} — CA, coûts et bénéfices",
            actionLabel = "Actualiser",
            onAction = viewModel::refresh,
        )

        PeriodSelector(selected = ui.period, onSelect = viewModel::onPeriod)
        CustomPeriodPickers(
            period = ui.period,
            customDayMs = ui.customDayMs,
            customFromMs = ui.customFromMs,
            customToMs = ui.customToMs,
            onCustomDay = viewModel::onCustomDay,
            onCustomRange = viewModel::onCustomRange,
        )

        val s = ui.stats
        if (s == null) {
            Text("Chargement…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        GlassCard {
            Text("Ventes par serveuse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Touche une serveuse pour voir le détail.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            WaitressBarChart(
                rows = s.waitressStats,
                selectedId = selectedWaitressId,
                onSelect = { id ->
                    selectedWaitressId = if (selectedWaitressId == id) null else id
                },
            )
        }

        GlassCard {
            Text("Top produits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            HorizontalBarChart(
                labels = s.topProducts.map { it.productName },
                values = s.topProducts.map { it.revenue.toFloat() },
                barColor = GestionBlue,
            )
        }

        GlassCard {
            Text("Top catégories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            HorizontalBarChart(
                labels = s.topCategories.map { it.categoryName },
                values = s.topCategories.map { it.revenue.toFloat() },
                barColor = GestionCyan,
            )
        }

        val selected = s.waitressStats.firstOrNull { it.waitressId == selectedWaitressId }
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            selected?.let { w ->
                GlassCard {
                    Text(
                        w.waitressName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GestionBlue,
                    )
                    DetailLine("Commandes", "${w.orderCount}")
                    DetailLine("Payées", "${w.paidCount}")
                    DetailLine("Non payées", "${w.unpaidCount}")
                    DetailLine("CA généré", MoneyFormat.format(w.caGenerated))
                    DetailLine("CA encaissé", MoneyFormat.format(w.caCollected))
                    DetailLine("À encaisser", MoneyFormat.format(w.toCollect))
                }
            }
        }

        Text(
            "Chiffres de la période",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = GestionBlue,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiChip("Commandes", "${s.ordersToday}", Modifier.weight(1f), GestionBlue)
            KpiChip("En cours", "${s.openOrders}", Modifier.weight(1f), GestionWarning)
            KpiChip("CA généré", MoneyFormat.format(s.caGenerated), Modifier.weight(1.2f), GestionSuccess)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiChip("Encaissé", MoneyFormat.format(s.caCollected), Modifier.weight(1f), GestionCyan)
            KpiChip("À encaisser", MoneyFormat.format(s.toCollect), Modifier.weight(1f), GestionWarning)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiChip("Coût d'achat", MoneyFormat.format(s.costOfGoods), Modifier.weight(1f), GestionWarning)
            KpiChip(
                "Bénéfice",
                MoneyFormat.format(s.benefice),
                Modifier.weight(1f),
                if (s.benefice >= 0) GestionSuccess else Color(0xFFDC2626),
            )
            KpiChip("Marge", "${s.marginPercent} %", Modifier.weight(1f), GestionBlue)
        }

        GlassCard {
            Text("Serveuses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (s.waitressStats.isEmpty()) {
                Text("Aucune vente serveuse sur la période", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                s.waitressStats.forEach { w ->
                    val isSelected = w.waitressId == selectedWaitressId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) GestionBlue.copy(alpha = 0.12f)
                                else Color.Transparent,
                            )
                            .clickable {
                                selectedWaitressId = if (isSelected) null else w.waitressId
                            }
                            .padding(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(w.waitressName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${w.orderCount} cmd · ${w.paidCount} payées",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                MoneyFormat.format(w.caGenerated),
                                fontWeight = FontWeight.Bold,
                                color = GestionBlue,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiChip(label: String, value: String, modifier: Modifier, accent: Color) {
    Box(
        modifier = modifier
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WaitressBarChart(
    rows: List<WaitressStats>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    if (rows.isEmpty()) {
        Text("Pas encore de données", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = rows.maxOf { it.caGenerated }.coerceAtLeast(1L).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            val barSlot = size.width / rows.size
            val barWidth = barSlot * 0.55f
            rows.forEachIndexed { index, row ->
                val h = (row.caGenerated / max) * (size.height * 0.85f)
                val left = barSlot * index + (barSlot - barWidth) / 2f
                val top = size.height - h
                val selected = row.waitressId == selectedId
                val color = ChartPalette[index % ChartPalette.size]
                    .copy(alpha = if (selected) 1f else 0.7f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, h.coerceAtLeast(8f)),
                    cornerRadius = CornerRadius(10f, 10f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEachIndexed { index, row ->
                val selected = row.waitressId == selectedId
                val color = ChartPalette[index % ChartPalette.size]
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color.copy(alpha = if (selected) 0.22f else 0.10f))
                        .clickable { onSelect(row.waitressId) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        row.waitressName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = color,
                    )
                    Text(
                        MoneyFormat.format(row.caGenerated),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalBarChart(
    labels: List<String>,
    values: List<Float>,
    barColor: Color,
) {
    if (labels.isEmpty()) {
        Text("Pas encore de données", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.zip(values).forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        MoneyFormat.format(value.toLong()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = barColor,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(8f, 8f),
                        size = size,
                    )
                    drawRoundRect(
                        color = barColor,
                        cornerRadius = CornerRadius(8f, 8f),
                        size = Size(size.width * (value / max), size.height),
                    )
                }
            }
        }
    }
}
