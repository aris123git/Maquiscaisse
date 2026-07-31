package com.maquis.caisse.ui.commandes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.PaymentMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: Long,
    onBack: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var showPay by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }

    LaunchedEffect(orderId) { viewModel.load(orderId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.order?.publicId ?: "Commande") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    state.order?.let { order ->
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(order.publicId))
                            },
                        ) { Text("Copier ID") }
                    }
                },
            )
        },
    ) { padding ->
        val order = state.order
        if (order == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                if (state.isBusy) CircularProgressIndicator()
                else Text(state.error ?: "Commande introuvable")
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${df.format(Date(order.createdAtEpochMs))} · ${order.status.label}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Table : ${order.tableLabel ?: "—"}  ·  Serveuse : ${order.waitressName ?: "—"}")
            Text(
                "Total ${MoneyFormat.format(order.totalAmount)} · " +
                    "Payé ${MoneyFormat.format(order.paidAmount)} · " +
                    "Reste ${MoneyFormat.format(order.remainingAmount)}",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val lines = if (state.editing) state.editLines else order.items
            lines.forEach { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${item.productName} ×${item.quantity}")
                        Text(
                            MoneyFormat.format(item.unitPrice),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(MoneyFormat.format(item.lineTotal))
                    if (state.editing) {
                        TextButton(onClick = { viewModel.removeLine(item.productId) }) {
                            Text("Retirer")
                        }
                    }
                }
            }

            if (order.payments.isNotEmpty()) {
                Text("Paiements", fontWeight = FontWeight.SemiBold)
                order.payments.forEach { p ->
                    Text(
                        "${p.paymentMode.label} ${MoneyFormat.format(p.amount)}" +
                            if (p.changeAmount > 0) {
                                " · monnaie ${MoneyFormat.format(p.changeAmount)}"
                            } else {
                                ""
                            },
                    )
                }
            }

            if (order.isOpen) {
                if (!state.editing) {
                    OutlinedButton(
                        onClick = { viewModel.startEdit() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Modifier la commande") }
                    Button(
                        onClick = { showPay = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("MARQUER COMME PAYÉ", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { viewModel.cancelOrder() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Annuler la commande") }
                } else {
                    Button(
                        onClick = { viewModel.saveEdits() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = state.editLines.isNotEmpty(),
                    ) { Text("Enregistrer les modifications") }
                    OutlinedButton(
                        onClick = { viewModel.cancelEdit() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Annuler l'édition") }
                }
            }

            OutlinedButton(
                onClick = { viewModel.printTicket() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Imprimer le ticket") }
        }
    }

    if (showPay) {
        val remaining = state.order?.remainingAmount ?: 0L
        PayOrderDialog(
            remaining = remaining,
            onDismiss = { showPay = false },
            onConfirm = { mode, received, full ->
                showPay = false
                viewModel.markPaid(mode, received, full)
            },
        )
    }
}

@Composable
private fun PayOrderDialog(
    remaining: Long,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMode, Long, Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var amountText by remember { mutableStateOf(remaining.toString()) }
    var partial by remember { mutableStateOf(false) }
    val received = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val change = if (!partial && mode == PaymentMode.CASH && received > remaining) {
        received - remaining
    } else {
        0L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marquer comme payé") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reste à payer : ${MoneyFormat.format(remaining)}")
                Text("Mode de paiement", fontWeight = FontWeight.SemiBold)
                PaymentMode.PAYMENT_CHOICES.filter { it != PaymentMode.MIXED }.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m },
                                label = { Text(m.label) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text(if (mode == PaymentMode.CASH) "Montant reçu" else "Montant") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (mode == PaymentMode.CASH) {
                    Text("Monnaie : ${MoneyFormat.format(change)}")
                }
                FilterChip(
                    selected = partial,
                    onClick = { partial = !partial },
                    label = { Text("Paiement partiel") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mode, received, !partial) },
                enabled = received > 0L && (partial || received >= remaining || mode != PaymentMode.CASH),
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
