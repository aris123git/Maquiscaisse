package com.maquis.caisse.domain.model

enum class OrderStatus(val storageKey: String, val label: String) {
    EN_COURS("EN_COURS", "En cours"),
    NON_PAYEE("NON_PAYEE", "Non payée"),
    PAYEE("PAYEE", "Payée"),
    ANNULEE("ANNULEE", "Annulée");

    companion object {
        fun fromStorage(key: String): OrderStatus =
            entries.firstOrNull { it.storageKey == key } ?: NON_PAYEE
    }
}

data class OrderLine(
    val id: Long = 0L,
    val productId: Long,
    val productName: String,
    val categoryName: String,
    val unitPrice: Long,
    val quantity: Int,
) {
    val lineTotal: Long get() = unitPrice * quantity
}

data class OrderPayment(
    val id: Long = 0L,
    val paymentMode: PaymentMode,
    val amount: Long,
    val amountTendered: Long = 0L,
    val changeAmount: Long = 0L,
    val createdAtEpochMs: Long,
    val userName: String? = null,
)

data class Order(
    val id: Long = 0L,
    val publicId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val status: OrderStatus,
    val tableId: Long? = null,
    val tableLabel: String? = null,
    val waitressId: Long? = null,
    val waitressName: String? = null,
    val totalAmount: Long = 0L,
    val paidAmount: Long = 0L,
    val note: String? = null,
    val items: List<OrderLine> = emptyList(),
    val payments: List<OrderPayment> = emptyList(),
) {
    val remainingAmount: Long get() = (totalAmount - paidAmount).coerceAtLeast(0L)
    val isOpen: Boolean get() = status == OrderStatus.EN_COURS || status == OrderStatus.NON_PAYEE
}

data class CreateOrderRequest(
    val lines: List<CartLine>,
    val waitressId: Long?,
    val waitressName: String?,
    val tableId: Long?,
    val tableLabel: String?,
    val note: String? = null,
    val markAsPaid: Boolean = false,
    val paymentMode: PaymentMode? = null,
    val amountTendered: Long = 0L,
    val paymentAmount: Long = 0L,
)

data class WaitressStats(
    val waitressId: Long?,
    val waitressName: String,
    val orderCount: Int,
    val paidCount: Int,
    val unpaidCount: Int,
    val caGenerated: Long,
    val caCollected: Long,
    val toCollect: Long,
)

data class CategorySalesRow(
    val categoryName: String,
    val quantity: Int,
    val revenue: Long,
)

data class ProductSalesRow(
    val productName: String,
    val categoryName: String,
    val quantity: Int,
    val revenue: Long,
)

data class DashboardStats(
    val ordersToday: Int,
    val openOrders: Int,
    val caGenerated: Long,
    val caCollected: Long,
    val toCollect: Long,
    val topProducts: List<ProductSalesRow>,
    val topCategories: List<CategorySalesRow>,
    val waitressStats: List<WaitressStats>,
)
