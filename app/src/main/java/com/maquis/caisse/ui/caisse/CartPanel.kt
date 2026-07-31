package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.ui.theme.GestionSuccess

/**
 * Panier caisse (panneau droit en paysage), comme Gestion_app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartPanel(
    lines: List<CartLine>,
    total: Long,
    onLineLongPress: (CartLine) -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (lines.isEmpty()) "Panier" else "Panier (${lines.sumOf { it.quantity }})",
                style = MaterialTheme.typography.headlineMedium,
            )
            if (onClear != null && lines.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Vider", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (lines.isEmpty()) {
            Text(
                text = "Tape un produit à gauche pour l'ajouter",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                items(lines, key = { it.productId }) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onLineLongPress(line) },
                                onLongClick = { onLineLongPress(line) },
                            )
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.productName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${line.quantity} × ${MoneyFormat.format(line.unitPrice)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = MoneyFormat.format(line.lineTotal),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                text = "Tape une ligne pour modifier",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = MoneyFormat.format(total),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )

        Button(
            onClick = onValidate,
            enabled = lines.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = GestionSuccess),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text("Encaisser", style = MaterialTheme.typography.titleLarge)
        }
    }
}
