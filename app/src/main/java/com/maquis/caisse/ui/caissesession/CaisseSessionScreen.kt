package com.maquis.caisse.ui.caissesession

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder UI — la table Room `caisse_sessions` (schéma v5/v6) est déjà en place
 * pour permettre la restauration des sauvegardes Replit. Écran métier à brancher ensuite.
 */
@Composable
fun CaisseSessionScreen() {
    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Sessions de caisse (schéma v6 prêt) — écran métier à venir",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
