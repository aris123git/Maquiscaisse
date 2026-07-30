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
 * Placeholder — implémentation complète prévue au Sprint 5 (voir prompt Cursor).
 * Ne pas ajouter de logique métier ici avant ce sprint.
 */
@Composable
fun CaisseSessionScreen() {
    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Ouverture / fermeture de caisse — à venir (Sprint 5)", style = MaterialTheme.typography.titleLarge)
        }
    }
}
