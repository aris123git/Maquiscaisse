package com.maquis.caisse.common

import com.maquis.caisse.domain.model.Sale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ticket texte simple (partage / affichage). Impression Bluetooth = Sprint 11. */
object TicketFormatter {

    private val dateFormat =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    fun format(sale: Sale): String = buildString {
        appendLine("——— MAQUIS CAISSE ———")
        appendLine("Ticket #${sale.id}")
        appendLine(dateFormat.format(Date(sale.createdAtEpochMs)))
        appendLine("----------------------")
        sale.items.forEach { item ->
            appendLine("${item.productName}")
            appendLine(
                "  ${item.quantity} x ${MoneyFormat.format(item.unitPrice)} = ${MoneyFormat.format(item.lineTotal)}",
            )
        }
        appendLine("----------------------")
        appendLine("TOTAL : ${MoneyFormat.format(sale.totalAmount)}")
        appendLine("Paiement : ${sale.paymentMode.label}")
        when (sale.paymentMode) {
            com.maquis.caisse.domain.model.PaymentMode.CASH -> {
                if (sale.amountTendered > 0) {
                    appendLine("Reçu : ${MoneyFormat.format(sale.amountTendered)}")
                    appendLine("Monnaie : ${MoneyFormat.format(sale.changeAmount)}")
                }
            }
            com.maquis.caisse.domain.model.PaymentMode.MIXED -> {
                if (sale.cashAmount > 0) appendLine("Espèces : ${MoneyFormat.format(sale.cashAmount)}")
                if (sale.mobileMoneyAmount > 0) {
                    appendLine("Mobile Money : ${MoneyFormat.format(sale.mobileMoneyAmount)}")
                }
                if (sale.voucherAmount > 0) appendLine("Avoir : ${MoneyFormat.format(sale.voucherAmount)}")
                if (sale.debtAmount > 0) appendLine("Dette : ${MoneyFormat.format(sale.debtAmount)}")
                if (sale.changeAmount > 0) {
                    appendLine("Monnaie : ${MoneyFormat.format(sale.changeAmount)}")
                }
            }
            else -> Unit
        }
        appendLine("——— Merci ———")
    }
}
