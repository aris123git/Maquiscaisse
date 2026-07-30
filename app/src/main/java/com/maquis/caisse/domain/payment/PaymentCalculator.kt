package com.maquis.caisse.domain.payment

import com.maquis.caisse.domain.model.PaymentBreakdown
import com.maquis.caisse.domain.model.PaymentInput
import com.maquis.caisse.domain.model.PaymentMode

/**
 * Règles de paiement / monnaie — source unique de vérité (UI + repository).
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

    /** Aperçu monnaie pour l'UI (0 si saisie incomplete / invalide). */
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

            PaymentMode.MOBILE_MONEY -> singleMode(total, PaymentMode.MOBILE_MONEY)
            PaymentMode.VOUCHER -> singleMode(total, PaymentMode.VOUCHER)
            PaymentMode.DEBT -> singleMode(total, PaymentMode.DEBT)

            PaymentMode.MIXED -> {
                val cash = input.cashAmount
                val mm = input.mobileMoneyAmount
                val voucher = input.voucherAmount
                val debt = input.debtAmount
                val paid = cash + mm + voucher + debt
                require(paid == total) {
                    "Le paiement mixte ($paid) doit égaler le total ($total)"
                }
                require(paid > 0L) { "Répartis le paiement mixte" }

                // Monnaie uniquement si le commerçant a saisi un montant tendu.
                val tendered = input.amountTendered
                val change = if (tendered != null) {
                    require(cash > 0L) {
                        "Pas de monnaie sans part espèces"
                    }
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
                    mobileMoneyAmount = mm,
                    voucherAmount = voucher,
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
            cashAmount = if (mode == PaymentMode.CASH) total else 0L,
            mobileMoneyAmount = if (mode == PaymentMode.MOBILE_MONEY) total else 0L,
            voucherAmount = if (mode == PaymentMode.VOUCHER) total else 0L,
            debtAmount = if (mode == PaymentMode.DEBT) total else 0L,
            amountTendered = 0L,
            changeAmount = 0L,
        )

    private fun requireNonNegative(input: PaymentInput) {
        require((input.amountTendered ?: 0L) >= 0L) { "Montant reçu invalide" }
        require(input.cashAmount >= 0L) { "Part espèces invalide" }
        require(input.mobileMoneyAmount >= 0L) { "Part Mobile Money invalide" }
        require(input.voucherAmount >= 0L) { "Part avoir invalide" }
        require(input.debtAmount >= 0L) { "Part dette invalide" }
    }
}
