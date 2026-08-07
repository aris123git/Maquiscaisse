package com.maquis.caisse.data.repository

import androidx.room.withTransaction
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.dao.DetteDao
import com.maquis.caisse.data.local.dao.DiningTableDao
import com.maquis.caisse.data.local.dao.OrderDao
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.entity.AuditLogEntity
import com.maquis.caisse.data.local.entity.DetteEntity
import com.maquis.caisse.data.local.entity.OrderEntity
import com.maquis.caisse.data.local.entity.OrderItemEntity
import com.maquis.caisse.data.local.entity.OrderPaymentEntity
import com.maquis.caisse.data.local.entity.StockMovementEntity
import com.maquis.caisse.domain.model.CaisseDuJour
import com.maquis.caisse.domain.model.CategorySalesRow
import com.maquis.caisse.domain.model.CreateOrderRequest
import com.maquis.caisse.domain.model.DashboardStats
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderLine
import com.maquis.caisse.domain.model.OrderPayment
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.ProductSalesRow
import com.maquis.caisse.domain.model.WaitressStats
import com.maquis.caisse.domain.order.OrderPublicId
import com.maquis.caisse.domain.payment.PaymentCalculator
import com.maquis.caisse.domain.repository.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val orderDao: OrderDao,
    private val productDao: ProductDao,
    private val tableDao: DiningTableDao,
    private val detteDao: DetteDao,
    private val session: SessionManager,
) : OrderRepository {

    override fun observeOpenOrders(): Flow<List<Order>> =
        orderDao.observeOpen().map { list -> list.map { it.toSummary() } }

    override fun observeFiltered(
        query: String,
        status: OrderStatus?,
        waitressId: Long?,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<Order>> =
        orderDao.observeFiltered(
            query = query.trim(),
            status = status?.storageKey,
            waitressId = waitressId,
            fromMs = fromMs,
            toMs = toMs,
        ).map { list -> list.map { it.toSummary() } }

    override suspend fun getOrder(id: Long): Order? = withContext(Dispatchers.IO) {
        val entity = orderDao.getById(id) ?: return@withContext null
        entity.toDomain(orderDao.getItems(id), orderDao.getPayments(id))
    }

    override suspend fun getByPublicId(publicId: String): Order? = withContext(Dispatchers.IO) {
        val entity = orderDao.getByPublicId(publicId) ?: return@withContext null
        entity.toDomain(orderDao.getItems(entity.id), orderDao.getPayments(entity.id))
    }

    override suspend fun createOrder(request: CreateOrderRequest): Order =
        withContext(Dispatchers.IO) {
            require(request.lines.isNotEmpty()) { "Ajoute au moins un produit" }
            val now = System.currentTimeMillis()
            val base = OrderPublicId.baseFromEpoch(now)
            val count = orderDao.countPublicIdPrefix(base)
            val publicId = OrderPublicId.allocate(base, count)
            val total = request.lines.sumOf { it.unitPrice * it.quantity }
            val user = session.user()

            var status = OrderStatus.EN_COURS
            var paid = 0L
            var tendered = 0L
            var change = 0L
            if (request.markAsPaid) {
                val mode = request.paymentMode ?: PaymentMode.CASH
                val amount = if (request.paymentAmount > 0) request.paymentAmount else total
                val breakdown = PaymentCalculator.simplePay(
                    remaining = total,
                    mode = mode,
                    payAmount = amount,
                    tendered = request.amountTendered.takeIf { it > 0 } ?: request.amountTendered,
                ).getOrElse { throw it }
                paid = breakdown.totalAmount
                tendered = breakdown.amountTendered
                change = breakdown.changeAmount
                status = if (paid >= total) OrderStatus.PAYEE else OrderStatus.NON_PAYEE
            }

            val orderId = db.withTransaction {
                val id = orderDao.insertOrder(
                    OrderEntity(
                        publicId = publicId,
                        createdAt = now,
                        updatedAt = now,
                        status = status.storageKey,
                        tableId = request.tableId,
                        tableLabel = request.tableLabel,
                        waitressId = request.waitressId,
                        waitressName = request.waitressName,
                        totalAmount = total,
                        paidAmount = paid,
                        note = request.note,
                        createdByUserId = user.id,
                        createdByName = user.name,
                    ),
                )
                val items = request.lines.map { line ->
                    val product = productDao.getById(line.productId)
                    OrderItemEntity(
                        orderId = id,
                        productId = line.productId,
                        productName = line.productName,
                        categoryName = product?.category ?: "",
                        unitPrice = line.unitPrice,
                        quantity = line.quantity,
                        lineTotal = line.unitPrice * line.quantity,
                    )
                }
                orderDao.insertItems(items)

                // Stock : décrémente dès la commande (consommation)
                request.lines.forEach { line ->
                    val product = productDao.getById(line.productId)
                        ?: error("Produit introuvable: ${line.productName}")
                    val updated = productDao.decreaseStockIfAvailable(line.productId, line.quantity)
                    require(updated == 1) {
                        "Stock insuffisant: ${line.productName} (dispo ${product.stock})"
                    }
                    db.stockMovementDao().insert(
                        StockMovementEntity(
                            productId = line.productId,
                            productName = line.productName,
                            type = "VENTE",
                            quantity = line.quantity,
                            previousStock = product.stock,
                            newStock = (product.stock - line.quantity).coerceAtLeast(0),
                            motif = "Commande $publicId",
                            userId = user.id,
                            userName = user.name,
                            createdAt = now,
                        ),
                    )
                }

                if (request.markAsPaid && request.paymentMode != null) {
                    orderDao.insertPayment(
                        OrderPaymentEntity(
                            orderId = id,
                            paymentMode = request.paymentMode.storageKey,
                            amount = paid,
                            amountTendered = tendered,
                            changeAmount = change,
                            createdAt = now,
                            userId = user.id,
                            userName = user.name,
                        ),
                    )
                }

                request.tableId?.let { tableDao.updateStatus(it, "OCCUPEE") }

                db.auditLogDao().insert(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "CREATE_ORDER",
                        details = "${user.name} a créé la commande $publicId (${status.label})",
                        newValue = publicId,
                        createdAt = now,
                    ),
                )
                id
            }
            getOrder(orderId) ?: error("Commande introuvable")
        }

    override suspend fun updateOrderItems(orderId: Long, lines: List<OrderLine>): Order =
        withContext(Dispatchers.IO) {
            require(lines.isNotEmpty()) { "La commande ne peut pas être vide" }
            val existing = orderDao.getById(orderId) ?: error("Commande introuvable")
            require(
                existing.status == OrderStatus.EN_COURS.storageKey ||
                    existing.status == OrderStatus.NON_PAYEE.storageKey,
            ) { "Commande non modifiable" }
            val user = session.user()
            val now = System.currentTimeMillis()
            val oldItems = orderDao.getItems(orderId)
            val total = lines.sumOf { it.lineTotal }

            db.withTransaction {
                // Remet le stock des anciennes lignes, puis décrémente les nouvelles
                oldItems.forEach { item ->
                    val p = productDao.getById(item.productId) ?: return@forEach
                    val newStock = p.stock + item.quantity
                    productDao.update(p.copy(stock = newStock))
                    db.stockMovementDao().insert(
                        StockMovementEntity(
                            productId = item.productId,
                            productName = item.productName,
                            type = "CORRECTION",
                            quantity = item.quantity,
                            previousStock = p.stock,
                            newStock = newStock,
                            motif = "Modif commande ${existing.publicId} (retrait)",
                            userId = user.id,
                            userName = user.name,
                            createdAt = now,
                        ),
                    )
                }
                lines.forEach { line ->
                    val p = productDao.getById(line.productId)
                        ?: error("Produit introuvable: ${line.productName}")
                    val updated = productDao.decreaseStockIfAvailable(line.productId, line.quantity)
                    require(updated == 1) { "Stock insuffisant: ${line.productName}" }
                    db.stockMovementDao().insert(
                        StockMovementEntity(
                            productId = line.productId,
                            productName = line.productName,
                            type = "VENTE",
                            quantity = line.quantity,
                            previousStock = p.stock,
                            newStock = (p.stock - line.quantity).coerceAtLeast(0),
                            motif = "Modif commande ${existing.publicId}",
                            userId = user.id,
                            userName = user.name,
                            createdAt = now,
                        ),
                    )
                }
                orderDao.replaceItems(
                    orderId,
                    lines.map {
                        OrderItemEntity(
                            orderId = orderId,
                            productId = it.productId,
                            productName = it.productName,
                            categoryName = it.categoryName,
                            unitPrice = it.unitPrice,
                            quantity = it.quantity,
                            lineTotal = it.lineTotal,
                        )
                    },
                )
                val paid = existing.paidAmount
                val status = when {
                    paid <= 0L -> OrderStatus.NON_PAYEE.storageKey
                    paid < total -> OrderStatus.NON_PAYEE.storageKey
                    else -> OrderStatus.PAYEE.storageKey
                }
                orderDao.updateOrder(
                    existing.copy(
                        totalAmount = total,
                        updatedAt = now,
                        status = status,
                    ),
                )
                db.auditLogDao().insert(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "UPDATE_ORDER",
                        details = "${user.name} a modifié la commande ${existing.publicId}",
                        createdAt = now,
                    ),
                )
            }
            getOrder(orderId) ?: error("Commande introuvable")
        }

    override suspend fun cancelOrder(orderId: Long): Order = withContext(Dispatchers.IO) {
        val existing = orderDao.getById(orderId) ?: error("Commande introuvable")
        require(existing.status != OrderStatus.ANNULEE.storageKey) { "Déjà annulée" }
        require(existing.status != OrderStatus.PAYEE.storageKey) { "Impossible d'annuler une commande payée" }
        val user = session.user()
        val now = System.currentTimeMillis()
        val items = orderDao.getItems(orderId)
        db.withTransaction {
            items.forEach { item ->
                val p = productDao.getById(item.productId) ?: return@forEach
                val newStock = p.stock + item.quantity
                productDao.update(p.copy(stock = newStock))
                db.stockMovementDao().insert(
                    StockMovementEntity(
                        productId = item.productId,
                        productName = item.productName,
                        type = "CORRECTION",
                        quantity = item.quantity,
                        previousStock = p.stock,
                        newStock = newStock,
                        motif = "Annulation ${existing.publicId}",
                        userId = user.id,
                        userName = user.name,
                        createdAt = now,
                    ),
                )
            }
            orderDao.updateOrder(
                existing.copy(status = OrderStatus.ANNULEE.storageKey, updatedAt = now),
            )
            existing.tableId?.let { tableDao.updateStatus(it, "A_NETTOYER") }
            db.auditLogDao().insert(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    action = "CANCEL_ORDER",
                    details = "${user.name} a annulé la commande ${existing.publicId}",
                    createdAt = now,
                ),
            )
        }
        getOrder(orderId) ?: error("Commande introuvable")
    }

    override suspend fun payOrder(
        orderId: Long,
        mode: PaymentMode,
        amount: Long,
        amountTendered: Long,
    ): Order = withContext(Dispatchers.IO) {
        val existing = orderDao.getById(orderId) ?: error("Commande introuvable")
        require(
            existing.status == OrderStatus.EN_COURS.storageKey ||
                existing.status == OrderStatus.NON_PAYEE.storageKey,
        ) { "Commande non payable" }
        val remaining = (existing.totalAmount - existing.paidAmount).coerceAtLeast(0L)
        val breakdown = PaymentCalculator.simplePay(
            remaining = remaining,
            mode = mode,
            payAmount = amount,
            tendered = amountTendered.takeIf { it > 0 },
        ).getOrElse { throw it }
        val user = session.user()
        val now = System.currentTimeMillis()
        val newPaid = existing.paidAmount + breakdown.totalAmount
        val newStatus = if (newPaid >= existing.totalAmount) {
            OrderStatus.PAYEE
        } else {
            OrderStatus.NON_PAYEE
        }

        db.withTransaction {
            orderDao.insertPayment(
                OrderPaymentEntity(
                    orderId = orderId,
                    paymentMode = mode.storageKey,
                    amount = breakdown.totalAmount,
                    amountTendered = breakdown.amountTendered,
                    changeAmount = breakdown.changeAmount,
                    createdAt = now,
                    userId = user.id,
                    userName = user.name,
                ),
            )
            orderDao.updateOrder(
                existing.copy(
                    paidAmount = newPaid,
                    status = newStatus.storageKey,
                    updatedAt = now,
                ),
            )
            if (newStatus == OrderStatus.PAYEE) {
                existing.tableId?.let { tableDao.updateStatus(it, "A_NETTOYER") }
            }
            db.auditLogDao().insert(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    action = "MARK_PAID",
                    details = "${user.name} a marqué la commande ${existing.publicId} comme ${newStatus.label} (${mode.label} ${breakdown.totalAmount})",
                    createdAt = now,
                ),
            )
        }
        // Création automatique de la dette si mode = DEBT
        if (mode == PaymentMode.DEBT) {
            val debtAmount = breakdown.totalAmount
            val orderEntity = existing
            val existingDette = detteDao.getByOrderId(orderId)
            if (existingDette != null) {
                // Consolidate: add the new amount onto the existing debt record and
                // recompute status so the displayed remaining balance stays correct
                // (e.g. a previously SETTLED dette must become PARTIAL when new debt arrives).
                val newOriginal = existingDette.originalAmount + debtAmount
                val recomputedStatus = when {
                    existingDette.paidAmount >= newOriginal -> "SETTLED"
                    existingDette.paidAmount > 0L -> "PARTIAL"
                    else -> "OPEN"
                }
                detteDao.updateDette(
                    existingDette.copy(
                        originalAmount = newOriginal,
                        status = recomputedStatus,
                    ),
                )
            } else {
                detteDao.insertDette(
                    DetteEntity(
                        customerName = orderEntity.waitressName ?: "Client",
                        orderId = orderId,
                        orderPublicId = orderEntity.publicId,
                        originalAmount = debtAmount,
                        paidAmount = 0L,
                        status = "OPEN",
                        createdAt = now,
                        userId = user.id,
                        userName = user.name,
                        note = "Commande ${orderEntity.publicId}",
                    ),
                )
            }
        }
        getOrder(orderId) ?: error("Commande introuvable")
    }

    override suspend fun waitressStats(fromMs: Long, toMs: Long, waitressId: Long?): List<WaitressStats> =
        withContext(Dispatchers.IO) {
            val orders = orderDao.listBetween(fromMs, toMs)
                .filter { it.status != OrderStatus.ANNULEE.storageKey }
                .filter { waitressId == null || it.waitressId == waitressId }
            orders.groupBy { it.waitressId to (it.waitressName ?: "Sans serveuse") }
                .map { (key, list) ->
                    val generated = list.sumOf { it.totalAmount }
                    val collected = list.sumOf { it.paidAmount }
                    WaitressStats(
                        waitressId = key.first,
                        waitressName = key.second,
                        orderCount = list.size,
                        paidCount = list.count { it.status == OrderStatus.PAYEE.storageKey },
                        unpaidCount = list.count {
                            it.status == OrderStatus.NON_PAYEE.storageKey ||
                                it.status == OrderStatus.EN_COURS.storageKey
                        },
                        caGenerated = generated,
                        caCollected = collected,
                        toCollect = (generated - collected).coerceAtLeast(0L),
                    )
                }
                .sortedByDescending { it.caGenerated }
        }

    override suspend fun categorySales(fromMs: Long, toMs: Long, waitressId: Long?): List<CategorySalesRow> =
        withContext(Dispatchers.IO) {
            val orders = orderDao.listBetween(fromMs, toMs)
                .filter { it.status != OrderStatus.ANNULEE.storageKey }
                .filter { waitressId == null || it.waitressId == waitressId }
            val rows = mutableMapOf<String, Pair<Int, Long>>()
            orders.forEach { order ->
                orderDao.getItems(order.id).forEach { item ->
                    val key = item.categoryName.ifBlank { "Divers" }
                    val prev = rows[key] ?: (0 to 0L)
                    rows[key] = (prev.first + item.quantity) to (prev.second + item.lineTotal)
                }
            }
            rows.map { (name, v) -> CategorySalesRow(name, v.first, v.second) }
                .sortedByDescending { it.revenue }
        }

    override suspend fun productSales(fromMs: Long, toMs: Long, waitressId: Long?): List<ProductSalesRow> =
        withContext(Dispatchers.IO) {
            val orders = orderDao.listBetween(fromMs, toMs)
                .filter { it.status != OrderStatus.ANNULEE.storageKey }
                .filter { waitressId == null || it.waitressId == waitressId }
            val rows = mutableMapOf<String, ProductSalesRow>()
            orders.forEach { order ->
                orderDao.getItems(order.id).forEach { item ->
                    val key = item.productName
                    val prev = rows[key]
                    rows[key] = ProductSalesRow(
                        productName = item.productName,
                        categoryName = item.categoryName.ifBlank { "Divers" },
                        quantity = (prev?.quantity ?: 0) + item.quantity,
                        revenue = (prev?.revenue ?: 0L) + item.lineTotal,
                    )
                }
            }
            rows.values.sortedByDescending { it.revenue }
        }

    override suspend fun dashboard(fromMs: Long, toMs: Long): DashboardStats =
        withContext(Dispatchers.IO) {
            val orders = orderDao.listBetween(fromMs, toMs)
                .filter { it.status != OrderStatus.ANNULEE.storageKey }
            val open = orders.count {
                it.status == OrderStatus.EN_COURS.storageKey ||
                    it.status == OrderStatus.NON_PAYEE.storageKey
            }
            val generated = orders.sumOf { it.totalAmount }
            val collected = orders.sumOf { it.paidAmount }

            // Ventilation paiements du jour par mode
            val cashToday = orderDao.totalPaymentsByMode("CASH", fromMs, toMs)
            val mobileModes = listOf(
                "ORANGE_MONEY", "MOOV_MONEY", "WAVE", "CARD", "OTHER",
                "MOBILE_MONEY", "VOUCHER", "TRANSFER",
            )
            val mobileToday = orderDao.totalPaymentsByModes(mobileModes, fromMs, toMs)
            val debtToday = detteDao.totalCreatedBetween(fromMs, toMs)
            val avoirToday = db.avoirDao().totalBetween(fromMs, toMs)

            // Écart fond de caisse si une session est ouverte
            val openSession = db.caisseSessionDao().getOpenSession()
            val fondDeCaisse: Long?
            val espècesThéoriques: Long?
            val écart: Long?
            if (openSession != null) {
                val now = toMs
                val cashSinceOpen = orderDao.totalPaymentsByMode("CASH", openSession.openedAt, now)
                fondDeCaisse = openSession.openingBalance
                espècesThéoriques = fondDeCaisse + cashSinceOpen
                écart = openSession.cashCounted?.let { counted -> counted - espècesThéoriques }
            } else {
                fondDeCaisse = null
                espècesThéoriques = null
                écart = null
            }

            DashboardStats(
                ordersToday = orders.size,
                openOrders = open,
                caGenerated = generated,
                caCollected = collected,
                toCollect = (generated - collected).coerceAtLeast(0L),
                topProducts = productSales(fromMs, toMs, null).take(5),
                topCategories = categorySales(fromMs, toMs, null).take(5),
                waitressStats = waitressStats(fromMs, toMs, null),
                caisseDuJour = CaisseDuJour(
                    cashToday = cashToday,
                    mobileToday = mobileToday,
                    debtToday = debtToday,
                    avoirToday = avoirToday,
                    fondDeCaisse = fondDeCaisse,
                    espècesThéoriques = espècesThéoriques,
                    écart = écart,
                ),
            )
        }

    override suspend fun bilanJour(fromMs: Long, toMs: Long): Map<String, Long> =
        withContext(Dispatchers.IO) {
            orderDao.paymentModeBreakdown(fromMs, toMs)
                .associate { it.paymentMode to it.total }
        }

    private fun OrderEntity.toSummary() = Order(
        id = id,
        publicId = publicId,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
        status = OrderStatus.fromStorage(status),
        tableId = tableId,
        tableLabel = tableLabel,
        waitressId = waitressId,
        waitressName = waitressName,
        totalAmount = totalAmount,
        paidAmount = paidAmount,
        note = note,
    )

    private fun OrderEntity.toDomain(
        items: List<OrderItemEntity>,
        payments: List<OrderPaymentEntity>,
    ) = toSummary().copy(
        items = items.map {
            OrderLine(
                id = it.id,
                productId = it.productId,
                productName = it.productName,
                categoryName = it.categoryName,
                unitPrice = it.unitPrice,
                quantity = it.quantity,
            )
        },
        payments = payments.map {
            OrderPayment(
                id = it.id,
                paymentMode = PaymentMode.fromStorage(it.paymentMode),
                amount = it.amount,
                amountTendered = it.amountTendered,
                changeAmount = it.changeAmount,
                createdAtEpochMs = it.createdAt,
                userName = it.userName,
            )
        },
    )
}
