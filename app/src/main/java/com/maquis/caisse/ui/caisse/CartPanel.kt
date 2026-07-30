package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.CartLine

/**
 * Panier toujours visible en bas de l'écran caisse.
 * Long-press sur une ligne → rouvre le pavé (édition / suppression).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartPanel(
    lines: List<CartLine>,
    total: Long,
    onLineLongPress: (CartLine) -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (lines.isEmpty()) {
                "Panier vide — tape un produit"
            } else {
                "Panier (${lines.sumOf { it.quantity }})"
            },
            style = MaterialTheme.typography.titleLarge,
        )

        if (lines.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .padding(top = 4.dp),
            ) {
                items(lines, key = { it.productId }) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onLineLongPress(line) },
                                onLongClick = { onLineLongPress(line) },
                            )
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${line.quantity}× ${line.productName}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = MoneyFormat.format(line.lineTotal),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                text = "Appui long pour modifier",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = MoneyFormat.format(total),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = onValidate,
                enabled = lines.isNotEmpty(),
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text("Valider", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
