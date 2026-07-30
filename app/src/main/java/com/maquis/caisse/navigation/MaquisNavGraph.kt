package com.maquis.caisse.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maquis.caisse.ui.avoirs.AvoirsScreen
import com.maquis.caisse.ui.caisse.CaisseScreen
import com.maquis.caisse.ui.caissesession.CaisseSessionScreen
import com.maquis.caisse.ui.commandes.CommandesScreen
import com.maquis.caisse.ui.components.MaquisBottomBar
import com.maquis.caisse.ui.dettes.DettesScreen
import com.maquis.caisse.ui.parametres.ParametresScreen
import com.maquis.caisse.ui.produits.ProduitsScreen
import com.maquis.caisse.ui.rapports.RapportsScreen
import com.maquis.caisse.ui.stock.StockScreen
import com.maquis.caisse.ui.tables.TablesScreen

/**
 * Graphe de navigation racine avec barre basse Caisse / Produits.
 * L'écran de démarrage reste la Caisse (usage quotidien).
 */
@Composable
fun MaquisNavGraph(navController: NavHostController = rememberNavController()) {
    Scaffold(
        bottomBar = { MaquisBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CAISSE,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CAISSE) { CaisseScreen() }
            composable(Routes.PRODUITS) { ProduitsScreen() }
            composable(Routes.TABLES) { TablesScreen() }
            composable(Routes.COMMANDES) { CommandesScreen() }
            composable(Routes.AVOIRS) { AvoirsScreen() }
            composable(Routes.DETTES) { DettesScreen() }
            composable(Routes.STOCK) { StockScreen() }
            composable(Routes.CAISSE_SESSION) { CaisseSessionScreen() }
            composable(Routes.RAPPORTS) { RapportsScreen() }
            composable(Routes.PARAMETRES) { ParametresScreen() }
        }
    }
}
