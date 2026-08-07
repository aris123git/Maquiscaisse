package com.maquis.caisse.ui.caissesession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.CaisseSession
import com.maquis.caisse.domain.repository.CaisseSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CaisseSessionViewModel @Inject constructor(
    sessionRepository: CaisseSessionRepository,
) : ViewModel() {
    val sessions: StateFlow<List<CaisseSession>> = sessionRepository
        .observeRecent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

@Composable
fun CaisseSessionScreen(viewModel: CaisseSessionViewModel = hiltViewModel()) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)

    val openSession = sessions.firstOrNull { it.isOpen }
    val closedSessions = sessions.filter { !it.isOpen }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Session en cours
            item {
                Text("Session en cours", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item {
                if (openSession != null) {
                    SessionCard(session = openSession, df = df, timeFormat = timeFormat, isOpen = true)
                } else {
                    Text(
                        "Aucune session ouverte — connecte un caissier pour démarrer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Historique
            if (closedSessions.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Historique des sessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(closedSessions) { session ->
                    SessionCard(session = session, df = df, timeFormat = timeFormat, isOpen = false)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: CaisseSession,
    df: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
    isOpen: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                session.userName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (isOpen) "● En cours" else "Fermée",
                style = MaterialTheme.typography.bodySmall,
                color = if (isOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Ouverture : ${df.format(Date(session.openedAt))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (session.closedAt != null) {
            Text(
                "Fermeture : ${df.format(Date(session.closedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.durationMs?.let { ms ->
                val mins = ms / 60_000
                val heures = mins / 60
                val reste = mins % 60
                Text(
                    "Durée : ${if (heures > 0) "${heures}h${reste.toString().padStart(2, '0')}min" else "${reste}min"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${session.salesCount} vente(s)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                MoneyFormat.format(session.totalAmount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
