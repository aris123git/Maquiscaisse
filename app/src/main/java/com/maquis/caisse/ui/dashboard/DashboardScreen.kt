package com.maquis.caisse.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.ChartPoint
import com.maquis.caisse.ui.charts.ChartCard
import com.maquis.caisse.ui.charts.CustomPeriodPickers
import com.maquis.caisse.ui.charts.PeriodSelector
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionCyan
import com.maquis.caisse.ui.theme.GestionSuccess
import com.maquis.caisse.ui.theme.GestionWarning

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
            subtitle = "Courbes, barres, camembert — choisis période et type",
            actionLabel = "Actualiser",
            onAction = viewModel::refresh,
        )

        GlassCard {
            Text("Période", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PeriodSelector(selected = ui.period, onSelect = viewModel::onPeriod)
            CustomPeriodPickers(
                period = ui.period,
                customDayMs = ui.customDayMs,
                customFromMs = ui.customFromMs,
                customToMs = ui.customToMs,
                onCustomDay = viewModel::onCustomDay,
                onCustomRange = viewModel::onCustomRange,
            )
        }

        val s = ui.stats
        if (s == null) {
            Text("Chargement…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        ChartCard(
            title = "Évolution du CA",
            subtitle = "CA généré sur la période",
            points = ui.timeSeries,
            chartType = ui.salesChartType,
            onChartTypeChange = viewModel::onSalesChartType,
        )

        ChartCard(
            title = "Ventes par serveuse",
            subtitle = "Touche une serveuse dans la liste pour le détail",
            points = s.waitressStats.map {
                ChartPoint(label = it.waitressName, value = it.caGenerated.toFloat())
            },
            chartType = ui.waitressChartType,
            onChartTypeChange = viewModel::onWaitressChartType,
        )

        ChartCard(
            title = "Top produits",
            points = s.topProducts.map {
                ChartPoint(label = it.productName, value = it.revenue.toFloat())
            },
            chartType = ui.productsChartType,
            onChartTypeChange = viewModel::onProductsChartType,
        )

        ChartCard(
            title = "Top catégories",
            points = s.topCategories.map {
                ChartPoint(label = it.categoryName, value = it.revenue.toFloat())
            },
            chartType = ui.categoriesChartType,
            onChartTypeChange = viewModel::onCategoriesChartType,
        )

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
            "Chiffres",
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

        GlassCard {
            Text("Serveuses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (s.waitressStats.isEmpty()) {
                Text("Aucune vente serveuse sur cette période", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Column(modifier = Modifier.weight(1f)) {
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
