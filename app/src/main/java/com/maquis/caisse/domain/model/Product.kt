package com.maquis.caisse.domain.model

/**
 * Modèle domaine d'un produit (indépendant de Room).
 *
 * [imagePath] : chemin relatif stockage privé, ou null si pas d'image.
 */
data class Product(
    val id: Long = 0L,
    val name: String,
    val category: String,
    val salePrice: Long,
    val purchasePrice: Long,
    val stock: Int,
    val alertThreshold: Int,
    val imagePath: String?,
    val isActive: Boolean = true,
)
