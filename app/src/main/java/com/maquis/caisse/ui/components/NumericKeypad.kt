package com.maquis.caisse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maquis.caisse.core.Constants

/**
 * Pavé numérique générique et réutilisable (quantité, paiement, remise,
 * remboursement, dettes, avoirs…).
 *
 * [value] est une chaîne de chiffres (pas de séparateur décimal — montants FCFA entiers).
 */
@Composable
fun NumericKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    maxDigits: Int = Constants.MAX_QUANTITY_DIGITS,
    confirmLabel: String = "OK",
    confirmEnabled: Boolean = true,
    allowLeadingZero: Boolean = false,
    onDeleteLine: (() -> Unit)? = null,
    deleteLabel: String = "Supprimer",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text(
            text = value.ifEmpty { "0" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫"),
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "C" -> onValueChange("")
                                "⌫" -> onValueChange(value.dropLast(1))
                                else -> {
                                    val next = appendDigit(
                                        current = value,
                                        digit = key,
                                        maxDigits = maxDigits,
                                        allowLeadingZero = allowLeadingZero,
                                    )
                                    onValueChange(next)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        if (key == "⌫") {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Effacer",
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onConfirm,
            // [confirmEnabled] est la source de vérité (le caller peut autoriser
            // une confirmation même si le champ actif est vide, ex. paiement mixte).
            enabled = confirmEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(confirmLabel, style = MaterialTheme.typography.titleLarge)
        }

        if (onDeleteLine != null) {
            TextButton(
                onClick = onDeleteLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = deleteLabel,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun appendDigit(
    current: String,
    digit: String,
    maxDigits: Int,
    allowLeadingZero: Boolean,
): String {
    if (current.length >= maxDigits) return current
    if (!allowLeadingZero && current.isEmpty() && digit == "0") return "0"
    if (current == "0") return digit
    return current + digit
}
