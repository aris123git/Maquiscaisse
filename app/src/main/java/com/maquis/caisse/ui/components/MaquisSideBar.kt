package com.maquis.caisse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maquis.caisse.navigation.Routes
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.SidebarAccent
import com.maquis.caisse.ui.theme.SidebarEnd
import com.maquis.caisse.ui.theme.SidebarStart
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
        NavItem(Routes.ASSISTANT, "Assistant"),
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
            .width(196.dp)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SidebarStart, SidebarEnd, Color(0xFF0E2A4A)),
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(
            "Maquis",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Text(
            "Caisse vivante",
            style = MaterialTheme.typography.labelLarge,
            color = SidebarAccent,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        items.forEach { item ->
            val selected = currentRoute == item.route ||
                (item.route == Routes.COMMANDES && currentRoute?.startsWith("order_detail") == true)
            val bg by animateColorAsState(
                if (selected) GestionBlue else Color.Transparent,
                label = "navBg",
            )
            val scale by animateFloatAsState(
                if (selected) 1.02f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "navScale",
            )
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
                    .height(46.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(14.dp)),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = bg,
                    contentColor = if (selected) Color.White else SidebarText,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Offline · local",
            style = MaterialTheme.typography.labelLarge,
            color = SidebarText.copy(alpha = 0.7f),
        )
    }
}
