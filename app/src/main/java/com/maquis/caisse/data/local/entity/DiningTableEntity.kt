package com.maquis.caisse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dining_tables",
    indices = [Index(value = ["number"], unique = true)],
)
data class DiningTableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val number: String,
    val name: String = "",
    val capacity: Int = 4,
    /** LIBRE | OCCUPEE | RESERVEE | A_NETTOYER */
    val status: String = "LIBRE",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
)
