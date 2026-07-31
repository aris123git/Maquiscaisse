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

/** Navigation latérale type Gestion_app (paysage). */
@Composable
fun MaquisSideBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        NavItem(Routes.CAISSE, "Caisse"),
        NavItem(Routes.PRODUITS, "Produits"),
        NavItem(Routes.STOCK, "Stock"),
        NavItem(Routes.DETTES, "Clients / Dettes"),
        NavItem(Routes.AVOIRS, "Avoirs"),
        NavItem(Routes.RAPPORTS, "Rapports"),
        NavItem(Routes.PARAMETRES, "Paramètres"),
    )

    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(SidebarDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Gestion",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Text(
            text = "Caisse · Commerce",
            style = MaterialTheme.typography.labelLarge,
            color = SidebarText,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        items.forEach { item ->
            val selected = currentRoute == item.route
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
                    .height(48.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected) GestionBlue else Color.Transparent,
                    contentColor = if (selected) Color.White else SidebarText,
                ),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Offline",
            style = MaterialTheme.typography.labelLarge,
            color = SidebarText,
        )
    }
}
