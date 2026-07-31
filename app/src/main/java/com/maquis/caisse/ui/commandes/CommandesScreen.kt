package com.maquis.caisse.ui.commandes

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandesScreen(
    onOpenOrder: (Long) -> Unit,
    viewModel: CommandesViewModel = hiltViewModel(),
) {
    val orders by viewModel.openOrders.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val filtered = viewModel.filtered(orders)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Commandes en cours", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::onQuery,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Rechercher ID, serveuse, table…") },
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("ID", Modifier.weight(1.3f), style = MaterialTheme.typography.labelLarge)
            Text("Heure", Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge)
            Text("Table", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
            Text("Serveuse", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("Statut", Modifier.weight(0.9f), style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider()
        if (filtered.isEmpty()) {
            Text(
                "Aucune commande en cours",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { order ->
                    OrderRow(order, timeFmt) { onOpenOrder(order.id) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun OrderRow(order: Order, timeFmt: SimpleDateFormat, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(order.publicId, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(timeFmt.format(Date(order.createdAtEpochMs)), Modifier.weight(0.7f), style = MaterialTheme.typography.bodyLarge)
        Text(order.tableLabel ?: "—", Modifier.weight(0.8f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(order.waitressName ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(
            MoneyFormat.format(order.totalAmount),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(order.status.label, Modifier.weight(0.9f), style = MaterialTheme.typography.bodyLarge)
    }
}
