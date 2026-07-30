package com.maquis.caisse.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maquis.caisse.ui.avoirs.AvoirsScreen
import com.maquis.caisse.ui.caisse.CaisseScreen
import com.maquis.caisse.ui.caissesession.CaisseSessionScreen
import com.maquis.caisse.ui.commandes.CommandesScreen
import com.maquis.caisse.ui.dettes.DettesScreen
import com.maquis.caisse.ui.parametres.ParametresScreen
import com.maquis.caisse.ui.produits.ProduitsScreen
import com.maquis.caisse.ui.rapports.RapportsScreen
import com.maquis.caisse.ui.stock.StockScreen
import com.maquis.caisse.ui.tables.TablesScreen

/**
 * Graphe de navigation racine. SPRINT 0 : chaque destination pointe vers
 * un écran "placeholder" qui sera implémenté à son sprint dédié.
 * L'écran de démarrage est la Caisse (écran le plus utilisé au quotidien).
 */
@Composable
fun MaquisNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.CAISSE) {
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
