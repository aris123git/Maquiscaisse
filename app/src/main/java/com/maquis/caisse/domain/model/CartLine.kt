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
