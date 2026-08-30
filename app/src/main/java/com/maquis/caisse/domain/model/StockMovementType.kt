package com.maquis.caisse.domain.model

/**
 * Types de mouvements de stock (chaînes persistées en Room).
 * Pas de « Correction » libre : toute correction admin = [AJUSTEMENT_AUTORISE].
 */
object StockMovementType {
    const val ENTREE = "ENTREE"
    const val SORTIE = "SORTIE"
    const val VENTE = "VENTE"
    const val PERTE = "PERTE"
    const val INVENTAIRE = "INVENTAIRE"
    const val AVOIR = "AVOIR"
    /** Restauration stock suite annulation / modif commande (système). */
    const val CORRECTION = "CORRECTION"
    /** Correction admin traçable (jamais silencieuse). */
    const val AJUSTEMENT_AUTORISE = "AJUSTEMENT_AUTORISE"

    val PERTE_MOTIFS = listOf(
        "Bouteille cassée",
        "Péremption",
        "Vol",
        "Consommation interne",
        "Don",
        "Casse",
        "Autre",
    )

    fun label(type: String): String = when (type) {
        ENTREE -> "Entrée"
        SORTIE -> "Sortie"
        VENTE -> "Vente"
        PERTE -> "Perte"
        INVENTAIRE -> "Inventaire"
        AVOIR -> "Avoir"
        CORRECTION -> "Correction système"
        AJUSTEMENT_AUTORISE -> "Ajustement autorisé"
        else -> type
    }

    fun signedQuantity(type: String, quantity: Int): String {
        val q = kotlin.math.abs(quantity)
        return when (type) {
            ENTREE, AVOIR, CORRECTION, AJUSTEMENT_AUTORISE -> "+$q"
            SORTIE, VENTE, PERTE -> "−$q"
            INVENTAIRE -> "→ $q"
            else -> q.toString()
        }
    }
}

/** Statuts de passation de relève. */
object ReleveHandoff {
    const val PENDING = "PENDING"
    const val VALIDATED = "VALIDATED"
    const val ANOMALY = "ANOMALY"
}

/** Motifs d'écart d'inventaire. */
enum class InventaireEcartReason(val label: String) {
    VENTE_HORS_CAISSE("Vente hors caisse"),
    PERTE("Perte"),
    CASSE("Casse"),
    ERREUR_SAISIE("Erreur de saisie"),
    AUTRE("Autre"),
}
