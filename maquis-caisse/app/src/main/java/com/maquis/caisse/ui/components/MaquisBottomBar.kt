package com.maquis.caisse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.maquis.caisse.navigation.Routes

/**
 * Barre de navigation basse — cibles tactiles larges par défaut (Material3),
 * cohérent avec la contrainte UX "≥ 48dp / usage à une main".
 * SPRINT 0 : seules Caisse / Produits sont branchées ; les autres onglets
 * (Tables, Commandes, Avoirs, Dettes...) seront ajoutés au fil des sprints
 * pour éviter une barre surchargée dès le départ.
 */
@Composable
fun MaquisBottomBar(navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = { navController.navigate(Routes.CAISSE) },
            icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
            label = { Text("Caisse") },
        )
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate(Routes.PRODUITS) },
            icon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
            label = { Text("Produits") },
        )
    }
}
