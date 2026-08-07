package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maquis.caisse.data.local.entity.AvoirEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AvoirDao {

    @Insert
    suspend fun insert(avoir: AvoirEntity): Long

    @Query("SELECT * FROM avoirs ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AvoirEntity>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM avoirs WHERE created_at BETWEEN :from AND :to")
    suspend fun totalBetween(from: Long, to: Long): Long
}
