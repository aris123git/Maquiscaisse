package com.maquis.caisse.domain.model

/**
 * Modes de paiement alignés sur Gestion_app.
 * Anciennes clés MOBILE_MONEY / VOUCHER restent lisibles (ventes déjà enregistrées).
 */
enum class PaymentMode(val storageKey: String, val label: String) {
    CASH("CASH", "Espèces"),
    ORANGE_MONEY("ORANGE_MONEY", "Orange Money"),
    MOOV_MONEY("MOOV_MONEY", "Moov Money"),
    CARD("CARD", "Carte"),
    TRANSFER("TRANSFER", "Virement"),
    DEBT("DEBT", "Dette"),
    MIXED("MIXED", "Mixte"),
    ;

    companion object {
        fun fromStorage(key: String): PaymentMode = when (key) {
            "MOBILE_MONEY" -> ORANGE_MONEY
            "VOUCHER" -> MOOV_MONEY
            else -> entries.firstOrNull { it.storageKey == key }
                ?: error("Mode de paiement inconnu: $key")
        }
    }
}
