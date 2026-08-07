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

    /** Session encore ouverte (closed_at IS NULL), la plus récente. */
    @Query("SELECT * FROM caisse_sessions WHERE closed_at IS NULL ORDER BY opened_at DESC LIMIT 1")
    suspend fun getOpenSession(): CaisseSessionEntity?

    /** Ferme la session et enregistre les totaux de la période.
     *  Compte les commandes PAYEE dont la date de paiement (updated_at) tombe dans la session.
     *  Toutes les ventes — directes ou via Commandes — passent par la table `orders`.
     */
    @Query("""
        UPDATE caisse_sessions
        SET closed_at    = :closedAt,
            sales_count  = (
                SELECT COUNT(*) FROM orders
                WHERE status = 'PAYEE'
                  AND updated_at >= opened_at
                  AND updated_at <= :closedAt
            ),
            total_amount = (
                SELECT COALESCE(SUM(paid_amount), 0) FROM orders
                WHERE status = 'PAYEE'
                  AND updated_at >= opened_at
                  AND updated_at <= :closedAt
            )
        WHERE id = :sessionId
    """)
    suspend fun closeSession(sessionId: Long, closedAt: Long)

    /** 30 dernières sessions pour l'historique. */
    @Query("SELECT * FROM caisse_sessions ORDER BY opened_at DESC LIMIT 30")
    fun observeRecent(): Flow<List<CaisseSessionEntity>>

    /** Session du jour (ouverte après minuit aujourd'hui). */
    @Query("SELECT * FROM caisse_sessions WHERE opened_at >= :startOfDay ORDER BY opened_at DESC")
    fun observeToday(startOfDay: Long): Flow<List<CaisseSessionEntity>>
}
