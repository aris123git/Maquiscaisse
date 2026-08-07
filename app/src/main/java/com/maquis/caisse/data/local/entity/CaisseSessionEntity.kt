package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "caisse_sessions",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["opened_at"]),
    ],
)
data class CaisseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "user_name") val userName: String,
    @ColumnInfo(name = "opened_at") val openedAt: Long,
    @ColumnInfo(name = "closed_at") val closedAt: Long? = null,
    /** Nombre de ventes enregistrées pendant la session. */
    @ColumnInfo(name = "sales_count") val salesCount: Int = 0,
    /** Montant total FCFA des ventes pendant la session. */
    @ColumnInfo(name = "total_amount") val totalAmount: Long = 0L,
)
