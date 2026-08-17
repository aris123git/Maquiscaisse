package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.maquis.caisse.data.local.entity.OrderEntity
import com.maquis.caisse.data.local.entity.OrderItemEntity
import com.maquis.caisse.data.local.entity.OrderPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Insert
    suspend fun insertItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE order_id = :orderId")
    suspend fun deleteItems(orderId: Long)

    @Insert
    suspend fun insertPayment(payment: OrderPaymentEntity): Long

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE public_id = :publicId LIMIT 1")
    suspend fun getByPublicId(publicId: String): OrderEntity?

    @Query("SELECT COUNT(*) FROM orders WHERE public_id = :publicId OR public_id LIKE :publicId || '-%'")
    suspend fun countPublicIdPrefix(publicId: String): Int

    @Query("SELECT * FROM order_items WHERE order_id = :orderId ORDER BY id ASC")
    suspend fun getItems(orderId: Long): List<OrderItemEntity>

    @Query("SELECT * FROM order_payments WHERE order_id = :orderId ORDER BY created_at ASC")
    suspend fun getPayments(orderId: Long): List<OrderPaymentEntity>

    @Query(
        """
        SELECT * FROM orders
        WHERE status IN ('EN_COURS', 'NON_PAYEE')
        ORDER BY created_at DESC
        """,
    )
    fun observeOpen(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY created_at DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT * FROM orders
        WHERE created_at BETWEEN :fromMs AND :toMs
        ORDER BY created_at DESC
        """,
    )
    suspend fun listBetween(fromMs: Long, toMs: Long): List<OrderEntity>

    @Query(
        """
        SELECT * FROM orders
        WHERE created_at BETWEEN :fromMs AND :toMs
          AND (:cashierId IS NULL OR created_by_user_id = :cashierId)
          AND status != 'ANNULEE'
        ORDER BY created_at DESC
        """,
    )
    suspend fun listByCashierBetween(
        fromMs: Long,
        toMs: Long,
        cashierId: Long?,
    ): List<OrderEntity>

    @Query(
        """
        SELECT * FROM orders
        WHERE (:status IS NULL OR status = :status)
          AND (:waitressId IS NULL OR waitress_id = :waitressId)
          AND (:query = '' OR public_id LIKE '%' || :query || '%'
               OR IFNULL(waitress_name,'') LIKE '%' || :query || '%'
               OR IFNULL(table_label,'') LIKE '%' || :query || '%'
               OR EXISTS (
                    SELECT 1 FROM order_items i
                    WHERE i.order_id = orders.id
                      AND (i.product_name LIKE '%' || :query || '%'
                           OR i.category_name LIKE '%' || :query || '%')
               )
               OR EXISTS (
                    SELECT 1 FROM order_payments p
                    WHERE p.order_id = orders.id
                      AND p.payment_mode LIKE '%' || :query || '%'
               ))
          AND created_at BETWEEN :fromMs AND :toMs
        ORDER BY created_at DESC
        """,
    )
    fun observeFiltered(
        query: String,
        status: String?,
        waitressId: Long?,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<OrderEntity>>

    @Transaction
    suspend fun replaceItems(orderId: Long, items: List<OrderItemEntity>) {
        deleteItems(orderId)
        insertItems(items.map { it.copy(orderId = orderId) })
    }
}
