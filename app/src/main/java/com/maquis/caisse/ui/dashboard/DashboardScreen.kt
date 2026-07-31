package com.maquis.caisse.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tableau de bord", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = viewModel::refresh) { Text("Actualiser") }
        }
        val s = stats
        if (s == null) {
            Text("Chargement…")
            return
        }
        StatLine("Commandes du jour", "${s.ordersToday}")
        StatLine("Commandes en cours", "${s.openOrders}")
        StatLine("CA généré", MoneyFormat.format(s.caGenerated))
        StatLine("CA encaissé", MoneyFormat.format(s.caCollected))
        StatLine("À encaisser", MoneyFormat.format(s.toCollect))

        Text("Produits les plus vendus", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        s.topProducts.forEach {
            Text("${it.productName} · ${it.quantity} · ${MoneyFormat.format(it.revenue)}")
        }

        Text("Catégories les plus vendues", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        s.topCategories.forEach {
            Text("${it.categoryName} · ${it.quantity} · ${MoneyFormat.format(it.revenue)}")
        }

        Text("Recettes par serveuse", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        s.waitressStats.forEach {
            Text(
                "${it.waitressName} — généré ${MoneyFormat.format(it.caGenerated)} / " +
                    "encaissé ${MoneyFormat.format(it.caCollected)} / " +
                    "à encaisser ${MoneyFormat.format(it.toCollect)}",
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
