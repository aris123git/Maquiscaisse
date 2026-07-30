package com.maquis.caisse.domain.model

/**
 * Ligne du panier caisse (en mémoire / SavedStateHandle, pas Room).
 */
data class CartLine(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val imagePath: String?,
) {
    val lineTotal: Long get() = unitPrice * quantity
}
