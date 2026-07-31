package com.maquis.caisse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maquis.caisse.navigation.Routes
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.SidebarDark
import com.maquis.caisse.ui.theme.SidebarText

private data class NavItem(val route: String, val label: String)

@Composable
fun MaquisSideBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        NavItem(Routes.CAISSE, "Caisse"),
        NavItem(Routes.COMMANDES, "Commandes"),
        NavItem(Routes.HISTORIQUE, "Historique"),
        NavItem(Routes.DASHBOARD, "Tableau de bord"),
        NavItem(Routes.PRODUITS, "Produits"),
        NavItem(Routes.CATEGORIES, "Catégories"),
        NavItem(Routes.TABLES, "Tables"),
        NavItem(Routes.STOCK, "Stock"),
        NavItem(Routes.RAPPORTS, "Rapports"),
        NavItem(Routes.UTILISATEURS, "Utilisateurs"),
        NavItem(Routes.PARAMETRES, "Paramètres"),
    )

    Column(
        modifier = Modifier
            .width(188.dp)
            .fillMaxHeight()
            .background(SidebarDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text("Gestion", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(
            "Maquis · Caisse",
            style = MaterialTheme.typography.labelLarge,
            color = SidebarText,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        items.forEach { item ->
            val selected = currentRoute == item.route ||
                (item.route == Routes.COMMANDES && currentRoute?.startsWith("order_detail") == true)
            TextButton(
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Routes.CAISSE) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected) GestionBlue else Color.Transparent,
                    contentColor = if (selected) Color.White else SidebarText,
                ),
            ) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("Offline", style = MaterialTheme.typography.labelLarge, color = SidebarText)
    }
}
