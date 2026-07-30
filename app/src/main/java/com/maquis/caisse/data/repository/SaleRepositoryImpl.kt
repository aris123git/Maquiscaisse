package com.maquis.caisse.data.repository

import androidx.room.withTransaction
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.dao.SaleDao
import com.maquis.caisse.data.local.entity.SaleEntity
import com.maquis.caisse.data.local.entity.SaleItemEntity
import com.maquis.caisse.domain.model.CompleteSaleRequest
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.Sale
import com.maquis.caisse.domain.model.SaleItem
import com.maquis.caisse.domain.repository.SaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
) : SaleRepository {

    override suspend fun completeSale(request: CompleteSaleRequest): Sale =
        withContext(Dispatchers.IO) {
            require(request.lines.isNotEmpty()) { "Le panier est vide" }
            val total = request.lines.sumOf { it.lineTotal }
            require(total > 0) { "Montant total invalide" }

            val change = when (request.paymentMode) {
                PaymentMode.CASH -> {
                    require(request.amountTendered >= total) {
                        "Montant insuffisant (reçu ${request.amountTendered}, total $total)"
                    }
                    request.amountTendered - total
                }
                PaymentMode.MIXED -> {
                    val paid = request.cashAmount + request.mobileMoneyAmount +
                        request.voucherAmount + request.debtAmount
                    require(paid == total) {
                        "Le paiement mixte ($paid) doit égaler le total ($total)"
                    }
                    if (request.amountTendered > 0L) {
                        require(request.amountTendered >= request.cashAmount) {
                            "Espèces tendues insuffisantes"
                        }
                        request.amountTendered - request.cashAmount
                    } else {
                        0L
                    }
                }
                else -> 0L
            }

            val cashAmount = when (request.paymentMode) {
                PaymentMode.CASH -> total
                PaymentMode.MIXED -> request.cashAmount
                else -> 0L
            }
            val mobileAmount = when (request.paymentMode) {
                PaymentMode.MOBILE_MONEY -> total
                PaymentMode.MIXED -> request.mobileMoneyAmount
                else -> 0L
            }
            val voucherAmount = when (request.paymentMode) {
                PaymentMode.VOUCHER -> total
                PaymentMode.MIXED -> request.voucherAmount
                else -> 0L
            }
            val debtAmount = when (request.paymentMode) {
                PaymentMode.DEBT -> total
                PaymentMode.MIXED -> request.debtAmount
                else -> 0L
            }

            val createdAt = System.currentTimeMillis()
            val saleEntity = SaleEntity(
                createdAt = createdAt,
                totalAmount = total,
                paymentMode = request.paymentMode.storageKey,
                cashAmount = cashAmount,
                mobileMoneyAmount = mobileAmount,
                voucherAmount = voucherAmount,
                debtAmount = debtAmount,
                amountTendered = request.amountTendered,
                changeAmount = change,
            )
            val itemEntities = request.lines.map { line ->
                SaleItemEntity(
                    saleId = 0L,
                    productId = line.productId,
                    productName = line.productName,
                    unitPrice = line.unitPrice,
                    quantity = line.quantity,
                    lineTotal = line.lineTotal,
                )
            }

            val saleId = db.withTransaction {
                val id = saleDao.insertSaleWithItems(saleEntity, itemEntities)
                request.lines.forEach { line ->
                    productDao.decreaseStock(line.productId, line.quantity)
                }
                id
            }

            getSale(saleId) ?: error("Vente introuvable après insertion")
        }

    override suspend fun getSale(id: Long): Sale? = withContext(Dispatchers.IO) {
        val sale = saleDao.getSaleById(id) ?: return@withContext null
        val items = saleDao.getItemsForSale(id)
        sale.toDomain(items)
    }

    private fun SaleEntity.toDomain(items: List<SaleItemEntity>) = Sale(
        id = id,
        createdAtEpochMs = createdAt,
        totalAmount = totalAmount,
        paymentMode = PaymentMode.fromStorage(paymentMode),
        cashAmount = cashAmount,
        mobileMoneyAmount = mobileMoneyAmount,
        voucherAmount = voucherAmount,
        debtAmount = debtAmount,
        amountTendered = amountTendered,
        changeAmount = changeAmount,
        items = items.map {
            SaleItem(
                id = it.id,
                saleId = it.saleId,
                productId = it.productId,
                productName = it.productName,
                unitPrice = it.unitPrice,
                quantity = it.quantity,
                lineTotal = it.lineTotal,
            )
        },
    )
}
