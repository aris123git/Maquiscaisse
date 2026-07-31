package com.maquis.caisse.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maquis.caisse.domain.model.Permissions
import com.maquis.caisse.ui.assistant.AssistantScreen
import com.maquis.caisse.ui.caisse.CaisseScreen
import com.maquis.caisse.ui.categories.CategoriesScreen
import com.maquis.caisse.ui.commandes.CommandesScreen
import com.maquis.caisse.ui.commandes.HistoriqueScreen
import com.maquis.caisse.ui.commandes.OrderDetailScreen
import com.maquis.caisse.ui.components.MaquisSideBar
import com.maquis.caisse.ui.components.SideBarViewModel
import com.maquis.caisse.ui.dashboard.DashboardScreen
import com.maquis.caisse.ui.parametres.ParametresScreen
import com.maquis.caisse.ui.produits.ProduitsScreen
import com.maquis.caisse.ui.rapports.RapportsScreen
import com.maquis.caisse.ui.stock.StockScreen
import com.maquis.caisse.ui.tables.TablesScreen
import com.maquis.caisse.ui.users.UsersScreen

@Composable
fun MaquisNavGraph(navController: NavHostController = rememberNavController()) {
    val sideBarVm: SideBarViewModel = hiltViewModel()
    val currentUser by sideBarVm.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == "ADMIN" ||
        currentUser?.can(Permissions.MANAGE_USERS) == true

    Scaffold(containerColor = Color.Transparent) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MaquisSideBar(navController, viewModel = sideBarVm)
            NavHost(
                navController = navController,
                startDestination = Routes.CAISSE,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                composable(Routes.CAISSE) {
                    CaisseScreen(
                        onOrderCreated = { id ->
                            navController.navigate(Routes.orderDetail(id))
                        },
                    )
                }
                composable(Routes.COMMANDES) {
                    CommandesScreen(
                        onOpenOrder = { id -> navController.navigate(Routes.orderDetail(id)) },
                    )
                }
                composable(Routes.HISTORIQUE) {
                    HistoriqueScreen(
                        onOpenOrder = { id -> navController.navigate(Routes.orderDetail(id)) },
                    )
                }
                composable(
                    route = Routes.ORDER_DETAIL,
                    arguments = listOf(navArgument("orderId") { type = NavType.LongType }),
                ) { entry ->
                    val orderId = entry.arguments?.getLong("orderId") ?: return@composable
                    OrderDetailScreen(
                        orderId = orderId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.DASHBOARD) { DashboardScreen() }
                composable(Routes.ASSISTANT) { AssistantScreen() }
                composable(Routes.PRODUITS) { ProduitsScreen() }
                composable(Routes.CATEGORIES) { CategoriesScreen() }
                composable(Routes.TABLES) { TablesScreen() }
                composable(Routes.STOCK) { StockScreen() }
                composable(Routes.RAPPORTS) { RapportsScreen() }
                composable(Routes.UTILISATEURS) {
                    if (!isAdmin) {
                        LaunchedEffect(Unit) {
                            navController.navigate(Routes.CAISSE) {
                                popUpTo(Routes.CAISSE) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    } else {
                        UsersScreen()
                    }
                }
                composable(Routes.PARAMETRES) { ParametresScreen() }
            }
        }
    }
}
