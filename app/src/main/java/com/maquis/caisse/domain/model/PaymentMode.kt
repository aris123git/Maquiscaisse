package com.maquis.caisse.domain.model

/**
 * Modes de paiement de la caisse (Sprint 2).
 * Avoir / dette : enregistrés ici ; logique métier complète aux sprints 6–7.
 */
enum class PaymentMode(val storageKey: String, val label: String) {
    CASH("CASH", "Espèces"),
    MOBILE_MONEY("MOBILE_MONEY", "Mobile Money"),
    VOUCHER("VOUCHER", "Avoir"),
    DEBT("DEBT", "Dette"),
    MIXED("MIXED", "Mixte"),
    ;

    companion object {
        fun fromStorage(key: String): PaymentMode =
            entries.firstOrNull { it.storageKey == key }
                ?: error("Mode de paiement inconnu: $key")
    }
}
