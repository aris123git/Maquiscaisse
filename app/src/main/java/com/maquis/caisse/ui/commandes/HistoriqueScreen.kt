package com.maquis.caisse.ui.commandes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.ui.common.DropdownField
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoriqueScreen(
    onOpenOrder: (Long) -> Unit,
    viewModel: HistoriqueViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val displayed = viewModel.filterDisplayed(orders)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

    Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1.4f).fillMaxSize()) {
            Text("HISTORIQUE DES COMMANDES", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQuery,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                singleLine = true,
                placeholder = { Text("ID, date, heure, serveuse, table, produit…") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DropdownField(
                    label = "Période",
                    selected = ui.period,
                    options = HistoryPeriod.entries,
                    optionLabel = {
                        when (it) {
                            HistoryPeriod.TODAY -> "Aujourd'hui"
                            HistoryPeriod.WEEK -> "7 jours"
                            HistoryPeriod.MONTH -> "30 jours"
                            HistoryPeriod.ALL -> "Tout"
                        }
                    },
                    onSelect = { it?.let(viewModel::onPeriod) },
                    modifier = Modifier.weight(1f),
                )
                DropdownField(
                    label = "Statut",
                    selected = ui.status,
                    options = OrderStatus.entries,
                    optionLabel = { it.label },
                    onSelect = viewModel::onStatus,
                    allowNull = true,
                    nullLabel = "Tous",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                DropdownField(
                    label = "Serveuse",
                    selected = ui.waitresses.firstOrNull { it.id == ui.waitressId },
                    options = ui.waitresses,
                    optionLabel = { it.name },
                    onSelect = { viewModel.onWaitress(it?.id) },
                    allowNull = true,
                    nullLabel = "Toutes",
                    modifier = Modifier.weight(1f),
                )
                DropdownField(
                    label = "Paiement",
                    selected = ui.paymentMode,
                    options = PaymentMode.PAYMENT_CHOICES,
                    optionLabel = { it.label },
                    onSelect = viewModel::onPaymentMode,
                    allowNull = true,
                    nullLabel = "Tous",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("ID", Modifier.weight(1.3f), style = MaterialTheme.typography.labelLarge)
                Text("Heure", Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge)
                Text("Table", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                Text("Serveuse", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Statut", Modifier.weight(0.9f), style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(displayed, key = { it.id }) { order ->
                    OrderRow(order, timeFmt) { onOpenOrder(order.id) }
                    HorizontalDivider()
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Stats serveuse", style = MaterialTheme.typography.titleLarge)
            DropdownField(
                label = "Serveuse",
                selected = ui.waitresses.firstOrNull { it.id == ui.waitressId },
                options = ui.waitresses,
                optionLabel = AppUser::name,
                onSelect = { viewModel.onWaitress(it?.id) },
                allowNull = true,
                nullLabel = "Toutes",
            )
            val stats = ui.waitressStats
            if (stats != null) {
                Text("Commandes : ${stats.orderCount}")
                Text("Payées : ${stats.paidCount} · Non payées : ${stats.unpaidCount}")
                Text("CA généré : ${MoneyFormat.format(stats.caGenerated)}", fontWeight = FontWeight.SemiBold)
                Text("CA encaissé : ${MoneyFormat.format(stats.caCollected)}", fontWeight = FontWeight.SemiBold)
                Text("À encaisser : ${MoneyFormat.format(stats.toCollect)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Aucune donnée pour cette période", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Par catégorie", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            ui.categoryRows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.categoryName, Modifier.weight(1.2f))
                    Text("${row.quantity}", Modifier.weight(0.5f))
                    Text(MoneyFormat.format(row.revenue), Modifier.weight(1f))
                }
            }
            if (ui.categoryRows.isNotEmpty()) {
                val totalQty = ui.categoryRows.sumOf { it.quantity }
                val totalRev = ui.categoryRows.sumOf { it.revenue }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                    Text("$totalQty", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                    Text(MoneyFormat.format(totalRev), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
            }

            Text("Par produit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(ui.productRows, key = { it.productName }) { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.productName, Modifier.weight(1.2f), maxLines = 1)
                        Text("${row.quantity}", Modifier.weight(0.4f))
                        Text(MoneyFormat.format(row.revenue), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
