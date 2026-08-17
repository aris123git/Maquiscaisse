package com.maquis.caisse.ui.suivi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.Expense
import com.maquis.caisse.domain.model.ExpenseCategories
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.ui.charts.CustomPeriodPickers
import com.maquis.caisse.ui.charts.PeriodSelector
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.theme.GestionSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MOVEMENT_TYPES = listOf("ENTREE", "VENTE", "INVENTAIRE", "SORTIE", "CORRECTION")

@Composable
fun SuiviAdminScreen(viewModel: SuiviAdminViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()

    if (!ui.canAccess) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Accès réservé à l'administrateur (permission voir_rapports).",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val message = ui.error ?: ui.success
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageHeader(
                title = "Suivi admin",
                subtitle = "Mouvements de stock et dépenses",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ui.canManageExpenses) {
                    TextButton(onClick = viewModel::openAddExpense) { Text("Nouvelle dépense") }
                }
                TextButton(onClick = viewModel::refresh) { Text("Actualiser") }
            }
        }

        if (message != null) {
            TextPill(
                message,
                if (ui.error != null) PillTone.DANGER else PillTone.SUCCESS,
            )
        }

        PeriodSelector(selected = ui.period, onSelect = viewModel::onPeriod)
        CustomPeriodPickers(
            period = ui.period,
            customDayMs = ui.customDayMs,
            customFromMs = ui.customFromMs,
            customToMs = ui.customToMs,
            onCustomDay = viewModel::onCustomDay,
            onCustomRange = viewModel::onCustomRange,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuiviTab.entries.forEach { tab ->
                FilterChip(
                    selected = ui.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    label = { Text(tab.label) },
                )
            }
        }

        when (ui.tab) {
            SuiviTab.STOCK -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DropdownField(
                        label = "Caissier",
                        selected = users.firstOrNull { it.id == ui.filterUserId },
                        options = users,
                        optionLabel = { it.name },
                        onSelect = { viewModel.setFilterUser(it?.id) },
                        allowNull = true,
                        nullLabel = "Tous",
                        modifier = Modifier.weight(1f),
                    )
                    DropdownField(
                        label = "Type",
                        selected = ui.filterMovementType,
                        options = MOVEMENT_TYPES,
                        optionLabel = { it },
                        onSelect = viewModel::setFilterMovementType,
                        allowNull = true,
                        nullLabel = "Tous",
                        modifier = Modifier.weight(1f),
                    )
                }
                TextPill(
                    "${ui.movements.size} mouvement(s)",
                    PillTone.INFO,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(ui.movements, key = { it.id }) { movement ->
                        MovementRow(movement)
                        HorizontalDivider()
                    }
                    if (ui.movements.isEmpty() && !ui.loading) {
                        item {
                            Text(
                                "Aucun mouvement sur cette période.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
            SuiviTab.EXPENSES -> {
                Text(
                    "Total dépenses : ${MoneyFormat.format(ui.expensesTotal)}",
                    fontWeight = FontWeight.Bold,
                    color = GestionSuccess,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextPill("${ui.expenses.size} dépense(s)", PillTone.INFO)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(ui.expenses, key = { it.id }) { expense ->
                        ExpenseRow(expense)
                        HorizontalDivider()
                    }
                    if (ui.expenses.isEmpty() && !ui.loading) {
                        item {
                            Text(
                                "Aucune dépense sur cette période.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (ui.showAddExpense) {
        AddExpenseDialog(
            description = ui.expenseDescription,
            amountText = ui.expenseAmountText,
            category = ui.expenseCategory,
            onDescription = viewModel::setExpenseDescription,
            onAmount = viewModel::setExpenseAmountText,
            onCategory = viewModel::setExpenseCategory,
            onDismiss = viewModel::closeAddExpense,
            onConfirm = viewModel::saveExpense,
        )
    }
}

@Composable
private fun MovementRow(movement: StockMovement) {
    val timeFmt = rememberTimeFmt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                movement.userName?.ifBlank { "—" } ?: "—",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                timeFmt.format(Date(movement.createdAtEpochMs)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${movement.productName} · ${movement.type} × ${movement.quantity}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Stock ${movement.previousStock} → ${movement.newStock}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpenseRow(expense: Expense) {
    val timeFmt = rememberTimeFmt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(expense.description, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(MoneyFormat.format(expense.amount), fontWeight = FontWeight.Bold)
        }
        Text(
            listOfNotNull(
                expense.category,
                expense.userName.ifBlank { null }?.let { "par $it" },
                timeFmt.format(Date(expense.createdAtEpochMs)),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddExpenseDialog(
    description: String,
    amountText: String,
    category: String,
    onDescription: (String) -> Unit,
    onAmount: (String) -> Unit,
    onCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle dépense") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescription,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = onAmount,
                    label = { Text("Montant (FCFA)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                DropdownField(
                    label = "Catégorie",
                    selected = category,
                    options = ExpenseCategories,
                    optionLabel = { it },
                    onSelect = { if (it != null) onCategory(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
private fun rememberTimeFmt(): SimpleDateFormat =
    androidx.compose.runtime.remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    }
