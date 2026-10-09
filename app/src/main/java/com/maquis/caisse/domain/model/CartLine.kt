package com.maquis.caisse.domain.model

/**
 * Ligne du panier caisse (mémoire / SavedStateHandle, pas Room).
 */
data class CartLine(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val imagePath: String?,
) {
    val lineTotal: Long
        get() {
            require(quantity >= 0) { "Quantité négative" }
            require(unitPrice >= 0L) { "Prix négatif" }
            // Évite le wrap silencieux Long sur saisies extrêmes.
            val product = unitPrice.toBigInteger() * quantity.toBigInteger()
            require(product <= Long.MAX_VALUE.toBigInteger()) { "Montant trop élevé" }
            return product.toLong()
        }
}

/**
 * Vente « entrée libre » : ligne saisie à la main (nom + prix), sans produit au catalogue.
 * Identifiée par un productId <= 0 (unique et négatif pour chaque ligne du panier).
 * Jamais de stock concerné.
 */
object FreeEntry {
    const val CATEGORY = "Vente libre"
    const val DEFAULT_NAME = "Divers"

    private val last = java.util.concurrent.atomic.AtomicLong(0L)

    fun isFree(productId: Long): Boolean = productId <= 0L

    /** Nouvel identifiant négatif, unique dans la session. */
    fun newId(): Long {
        val candidate = -System.currentTimeMillis()
        return last.updateAndGet { prev -> if (prev == 0L || candidate < prev) candidate else prev - 1 }
    }
}
