package com.maquis.caisse.domain.model

data class CaisseSession(
    val id: Long,
    val userId: Long,
    val userName: String,
    val openedAt: Long,
    val closedAt: Long?,
    val salesCount: Int,
    val totalAmount: Long,
) {
    val isOpen: Boolean get() = closedAt == null
    val durationMs: Long? get() = closedAt?.let { it - openedAt }
}
