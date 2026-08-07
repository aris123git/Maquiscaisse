package com.maquis.caisse.ui.caissesession

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.data.print.EscPosPrinter
import com.maquis.caisse.domain.model.CaisseSession
import com.maquis.caisse.domain.repository.CaisseSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class CaisseSessionViewModel @Inject constructor(
    private val sessionRepository: CaisseSessionRepository,
    private val printer: EscPosPrinter,
) : ViewModel() {
    val sessions: StateFlow<List<CaisseSession>> = sessionRepository
        .observeRecent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateCashCounted(amount: Long) = viewModelScope.launch {
        sessionRepository.updateCashCounted(amount)
    }

    fun printSessionClosure(session: CaisseSession) = viewModelScope.launch {
        printer.printSessionClosure(session)
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CaisseSessionScreen(viewModel: CaisseSessionViewModel = hiltViewModel()) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }

    val openSession = sessions.firstOrNull { it.isOpen }
    val closedSessions = sessions.filter { !it.isOpen }

    var showComptageDialog by remember { mutableStateOf(false) }

    if (showComptageDialog) {
        ComptageDialog(
            openSession = openSession,
            onConfirm = { amount ->
                viewModel.updateCashCounted(amount)
                showComptageDialog = false
            },
            onDismiss = { showComptageDialog = false },
        )
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Session en cours",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                if (openSession != null) {
                    OpenSessionCard(
                        session = openSession,
                        df = df,
                        onComptage = { showComptageDialog = true },
                        onPrint = { viewModel.printSessionClosure(openSession) },
                    )
                } else {
                    Text(
                        "Aucune session ouverte — connecte un caissier pour démarrer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

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
                    ClosedSessionCard(
                        session = session,
                        df = df,
                        onPrint = { viewModel.printSessionClosure(session) },
                    )
                    HorizontalDivider()
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Open session card ─────────────────────────────────────────────────────────

@Composable
private fun OpenSessionCard(
    session: CaisseSession,
    df: SimpleDateFormat,
    onComptage: () -> Unit,
    onPrint: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    session.userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "● En cours",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF388E3C),
                )
            }
            Text(
                "Ouverture : ${df.format(Date(session.openedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

            // Fond de caisse
            FinancialRow(
                label = "Fond de caisse",
                value = MoneyFormat.format(session.openingBalance),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            // Ventes actuelles (données en direct via CaisseSessionDao.observeRecent — zéro tant que non clôturée)
            FinancialRow(
                label = "Ventes (espèces)",
                value = MoneyFormat.format(session.cashSales),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            FinancialRow(
                label = "Ventes (mobile / carte)",
                value = MoneyFormat.format(session.mobileSales),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            FinancialRow(
                label = "Ventes (dettes)",
                value = MoneyFormat.format(session.debtSales),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

            FinancialRow(
                label = "Espèces attendues",
                value = MoneyFormat.format(session.cashTheoretical),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                bold = true,
            )

            // Comptage
            if (session.cashCounted != null) {
                val variance = session.cashVariance ?: 0L
                val varColor = when {
                    variance > 0 -> Color(0xFF388E3C)
                    variance < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
                FinancialRow(
                    label = "Caisse comptée",
                    value = MoneyFormat.format(session.cashCounted),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                FinancialRow(
                    label = if (variance >= 0) "Excédent" else "Manquant",
                    value = "${if (variance >= 0) "+" else ""}${MoneyFormat.format(variance)}",
                    color = varColor,
                    bold = true,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onComptage,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (session.cashCounted == null) "Compter la caisse" else "Recompter la caisse")
            }
            if (session.cashCounted != null) {
                Button(
                    onClick = onPrint,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Text("Imprimer la cloture")
                }
            }
        }
    }
}

// ── Closed session card ───────────────────────────────────────────────────────

@Composable
private fun ClosedSessionCard(
    session: CaisseSession,
    df: SimpleDateFormat,
    onPrint: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
                "Fermée",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${df.format(Date(session.openedAt))} → ${session.closedAt?.let { df.format(Date(it)) } ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        session.durationMs?.let { ms ->
            val mins = ms / 60_000
            val h = mins / 60
            val m = mins % 60
            Text(
                "Durée : ${if (h > 0) "${h}h${m.toString().padStart(2, '0')}min" else "${m}min"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Résumé financier compact
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "${session.salesCount} vente(s)",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (session.openingBalance > 0) {
                    Text(
                        "Fond : ${MoneyFormat.format(session.openingBalance)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (session.cashSales > 0) {
                    Text(
                        "Espèces : ${MoneyFormat.format(session.cashSales)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (session.mobileSales > 0) {
                    Text(
                        "Mobile : ${MoneyFormat.format(session.mobileSales)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (session.debtSales > 0) {
                    Text(
                        "Dettes : ${MoneyFormat.format(session.debtSales)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    MoneyFormat.format(session.totalAmount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                session.cashCounted?.let { counted ->
                    val variance = session.cashVariance ?: 0L
                    val varColor = if (variance >= 0) Color(0xFF388E3C) else MaterialTheme.colorScheme.error
                    Text(
                        "Comptage : ${MoneyFormat.format(counted)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Écart : ${if (variance >= 0) "+" else ""}${MoneyFormat.format(variance)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = varColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (session.cashCounted != null) {
            Button(
                onClick = onPrint,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text("Réimprimer la clôture")
            }
        }
    }
}

// ── Comptage dialog ───────────────────────────────────────────────────────────

@Composable
private fun ComptageDialog(
    openSession: CaisseSession?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val counted = input.toLongOrNull() ?: 0L
    val theoretical = openSession?.cashTheoretical ?: 0L
    val variance = counted - theoretical
    val varColor = if (variance >= 0) Color(0xFF388E3C) else MaterialTheme.colorScheme.error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comptage espèces") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Espèces attendues", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        MoneyFormat.format(theoretical),
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    label = { Text("Montant compté *") },
                    singleLine = true,
                    suffix = { Text("FCFA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (input.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (variance >= 0) "Excédent" else "Manquant")
                        Text(
                            "${if (variance >= 0) "+" else ""}${MoneyFormat.format(variance)}",
                            color = varColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(counted) },
                enabled = counted > 0 || input == "0",
            ) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

// ── Shared composable ─────────────────────────────────────────────────────────

@Composable
private fun FinancialRow(
    label: String,
    value: String,
    color: Color,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.8f),
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
