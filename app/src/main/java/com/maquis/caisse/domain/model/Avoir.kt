package com.maquis.caisse.domain.model

data class Avoir(
    val id: Long,
    val orderId: Long?,
    val orderPublicId: String?,
    val customerName: String,
    val reason: String,
    val amount: Long,
    val createdAt: Long,
    val userName: String,
    val note: String,
)
