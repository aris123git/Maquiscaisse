package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maquis.caisse.data.local.entity.CaisseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaisseSessionDao {

    @Insert
    suspend fun insert(session: CaisseSessionEntity): Long

    @Query("SELECT * FROM caisse_sessions WHERE closed_at IS NULL ORDER BY opened_at DESC LIMIT 1")
    suspend fun getOpenSession(): CaisseSessionEntity?

    /**
     * Ferme la session et calcule la ventilation depuis order_payments.
     * Toutes les ventes passent par la table orders / order_payments.
     */
    @Query("""
        UPDATE caisse_sessions
        SET closed_at    = :closedAt,
            cash_counted = :cashCounted,
            sales_count  = (
                SELECT COUNT(DISTINCT o.id) FROM orders o
                WHERE o.status = 'PAYEE'
                  AND o.updated_at >= opened_at AND o.updated_at <= :closedAt
            ),
            total_amount = (
                SELECT COALESCE(SUM(p.amount), 0) FROM order_payments p
                JOIN orders o ON p.order_id = o.id
                WHERE o.status = 'PAYEE'
                  AND o.updated_at >= opened_at AND o.updated_at <= :closedAt
            ),
            cash_sales   = (
                SELECT COALESCE(SUM(p.amount), 0) FROM order_payments p
                JOIN orders o ON p.order_id = o.id
                WHERE o.status = 'PAYEE'
                  AND o.updated_at >= opened_at AND o.updated_at <= :closedAt
                  AND p.payment_mode = 'CASH'
            ),
            mobile_sales = (
                SELECT COALESCE(SUM(p.amount), 0) FROM order_payments p
                JOIN orders o ON p.order_id = o.id
                WHERE o.status = 'PAYEE'
                  AND o.updated_at >= opened_at AND o.updated_at <= :closedAt
                  AND p.payment_mode IN ('ORANGE_MONEY','MOOV_MONEY','WAVE','CARD','OTHER','MOBILE_MONEY','VOUCHER','TRANSFER')
            ),
            debt_sales   = (
                SELECT COALESCE(SUM(p.amount), 0) FROM order_payments p
                JOIN orders o ON p.order_id = o.id
                WHERE o.status = 'PAYEE'
                  AND o.updated_at >= opened_at AND o.updated_at <= :closedAt
                  AND p.payment_mode = 'DEBT'
            )
        WHERE id = :sessionId
    """)
    suspend fun closeSession(sessionId: Long, closedAt: Long, cashCounted: Long?)

    /** Met à jour uniquement le comptage espèces sans fermer la session. */
    @Query("UPDATE caisse_sessions SET cash_counted = :cashCounted WHERE id = :sessionId")
    suspend fun updateCashCounted(sessionId: Long, cashCounted: Long)

    @Query("SELECT * FROM caisse_sessions ORDER BY opened_at DESC LIMIT 30")
    fun observeRecent(): Flow<List<CaisseSessionEntity>>
}
