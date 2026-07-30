package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.ui.components.NumericKeypad

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentDialog(
    total: Long,
    payment: PaymentFormState,
    onDismiss: () -> Unit,
    onSelectMode: (PaymentMode) -> Unit,
    onSelectField: (PaymentField) -> Unit,
    onInputChange: (String) -> Unit,
    currentInput: String,
    onConfirm: () -> Unit,
) {
    val changePreview = when (payment.mode) {
        PaymentMode.CASH -> {
            val tendered = payment.amountTendered.toLongOrNull() ?: 0L
            (tendered - total).coerceAtLeast(0L)
        }
        PaymentMode.MIXED -> {
            val tendered = payment.amountTendered.toLongOrNull() ?: 0L
            val cash = payment.cashAmount.toLongOrNull() ?: 0L
            if (tendered > 0) (tendered - cash).coerceAtLeast(0L) else 0L
        }
        else -> 0L
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(
                    text = "Paiement",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    text = "Total : ${MoneyFormat.format(total)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentMode.entries.forEach { mode ->
                        FilterChip(
                            selected = payment.mode == mode,
                            onClick = { onSelectMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }

                when (payment.mode) {
                    PaymentMode.CASH -> {
                        Text(
                            text = "Montant reçu → monnaie : ${MoneyFormat.format(changePreview)}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    PaymentMode.MIXED -> {
                        MixedFields(
                            payment = payment,
                            onSelectField = onSelectField,
                        )
                        if (changePreview > 0) {
                            Text(
                                text = "Monnaie espèces : ${MoneyFormat.format(changePreview)}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    PaymentMode.MOBILE_MONEY,
                    PaymentMode.VOUCHER,
                    PaymentMode.DEBT,
                    -> {
                        Text(
                            text = "Confirmer ${payment.mode.label} pour ${MoneyFormat.format(total)}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                payment.errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                val needsKeypad = payment.mode == PaymentMode.CASH ||
                    payment.mode == PaymentMode.MIXED

                if (needsKeypad) {
                    NumericKeypad(
                        value = currentInput,
                        onValueChange = onInputChange,
                        onConfirm = onConfirm,
                        title = fieldLabel(payment),
                        maxDigits = 9,
                        allowLeadingZero = false,
                        confirmLabel = if (payment.isSaving) "…" else "Encaisser",
                        confirmEnabled = !payment.isSaving,
                    )
                } else {
                    Button(
                        onClick = onConfirm,
                        enabled = !payment.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                    ) {
                        Text(
                            if (payment.isSaving) "Enregistrement…" else "Encaisser",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text("Annuler")
                }
            }
        }
    }
}

@Composable
private fun MixedFields(
    payment: PaymentFormState,
    onSelectField: (PaymentField) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        MixedFieldChip(
            label = "Espèces",
            value = payment.cashAmount,
            selected = payment.activeField == PaymentField.CASH,
            onClick = { onSelectField(PaymentField.CASH) },
        )
        MixedFieldChip(
            label = "Mobile Money",
            value = payment.mobileMoneyAmount,
            selected = payment.activeField == PaymentField.MOBILE_MONEY,
            onClick = { onSelectField(PaymentField.MOBILE_MONEY) },
        )
        MixedFieldChip(
            label = "Avoir",
            value = payment.voucherAmount,
            selected = payment.activeField == PaymentField.VOUCHER,
            onClick = { onSelectField(PaymentField.VOUCHER) },
        )
        MixedFieldChip(
            label = "Dette",
            value = payment.debtAmount,
            selected = payment.activeField == PaymentField.DEBT,
            onClick = { onSelectField(PaymentField.DEBT) },
        )
        MixedFieldChip(
            label = "Espèces tendues (monnaie)",
            value = payment.amountTendered,
            selected = payment.activeField == PaymentField.TENDERED,
            onClick = { onSelectField(PaymentField.TENDERED) },
        )
    }
}

@Composable
private fun MixedFieldChip(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    "$label : ${MoneyFormat.format(value.toLongOrNull() ?: 0L)}",
                )
            },
        )
    }
}

private fun fieldLabel(payment: PaymentFormState): String = when (payment.activeField) {
    PaymentField.TENDERED -> "Montant reçu"
    PaymentField.CASH -> "Part espèces"
    PaymentField.MOBILE_MONEY -> "Part Mobile Money"
    PaymentField.VOUCHER -> "Part avoir"
    PaymentField.DEBT -> "Part dette"
}
