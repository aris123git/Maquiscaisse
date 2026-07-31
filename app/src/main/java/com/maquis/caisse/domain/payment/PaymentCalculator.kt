package com.maquis.caisse.domain.payment

import com.maquis.caisse.domain.model.PaymentBreakdown
import com.maquis.caisse.domain.model.PaymentInput
import com.maquis.caisse.domain.model.PaymentMode

/**
 * Règles de paiement / monnaie — source unique de vérité (UI + repository).
 * Aligné sur Gestion_app (espèces, Orange/Moov, carte, virement, dette, mixte).
 */
object PaymentCalculator {

    fun validate(total: Long, input: PaymentInput): Result<PaymentBreakdown> {
        if (total <= 0L) {
            return Result.failure(IllegalArgumentException("Montant total invalide"))
        }
        return try {
            Result.success(compute(total, input))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    fun previewChange(total: Long, input: PaymentInput): Long =
        validate(total, input).getOrNull()?.changeAmount ?: 0L

    private fun compute(total: Long, input: PaymentInput): PaymentBreakdown {
        require(total > 0L) { "Montant total invalide" }
        requireNonNegative(input)

        return when (input.mode) {
            PaymentMode.CASH -> {
                val tendered = input.amountTendered
                    ?: throw IllegalArgumentException("Saisis le montant reçu")
                require(tendered >= total) {
                    "Montant insuffisant (reçu $tendered, total $total)"
                }
                PaymentBreakdown(
                    mode = PaymentMode.CASH,
                    totalAmount = total,
                    cashAmount = total,
                    mobileMoneyAmount = 0L,
                    voucherAmount = 0L,
                    debtAmount = 0L,
                    amountTendered = tendered,
                    changeAmount = tendered - total,
                )
            }

            PaymentMode.ORANGE_MONEY,
            PaymentMode.MOOV_MONEY,
            PaymentMode.CARD,
            PaymentMode.TRANSFER,
            PaymentMode.DEBT,
            -> singleMode(total, input.mode)

            PaymentMode.MIXED -> {
                val cash = input.cashAmount
                val orange = input.mobileMoneyAmount
                val moov = input.voucherAmount
                val debt = input.debtAmount
                val paid = cash + orange + moov + debt
                require(paid == total) {
                    "Le paiement mixte ($paid) doit égaler le total ($total)"
                }
                require(paid > 0L) { "Répartis le paiement mixte" }

                val tendered = input.amountTendered
                val change = if (tendered != null) {
                    require(cash > 0L) { "Pas de monnaie sans part espèces" }
                    require(tendered >= cash) {
                        "Espèces tendues insuffisantes (reçu $tendered, part $cash)"
                    }
                    tendered - cash
                } else {
                    0L
                }

                PaymentBreakdown(
                    mode = PaymentMode.MIXED,
                    totalAmount = total,
                    cashAmount = cash,
                    mobileMoneyAmount = orange,
                    voucherAmount = moov,
                    debtAmount = debt,
                    amountTendered = tendered ?: 0L,
                    changeAmount = change,
                )
            }
        }
    }

    private fun singleMode(total: Long, mode: PaymentMode): PaymentBreakdown =
        PaymentBreakdown(
            mode = mode,
            totalAmount = total,
            cashAmount = 0L,
            mobileMoneyAmount = when (mode) {
                PaymentMode.ORANGE_MONEY, PaymentMode.CARD, PaymentMode.TRANSFER -> total
                else -> 0L
            },
            voucherAmount = if (mode == PaymentMode.MOOV_MONEY) total else 0L,
            debtAmount = if (mode == PaymentMode.DEBT) total else 0L,
            amountTendered = 0L,
            changeAmount = 0L,
        )

    private fun requireNonNegative(input: PaymentInput) {
        require((input.amountTendered ?: 0L) >= 0L) { "Montant reçu invalide" }
        require(input.cashAmount >= 0L) { "Part espèces invalide" }
        require(input.mobileMoneyAmount >= 0L) { "Part Orange Money invalide" }
        require(input.voucherAmount >= 0L) { "Part Moov Money invalide" }
        require(input.debtAmount >= 0L) { "Part dette invalide" }
    }
}
