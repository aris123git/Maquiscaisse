package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.maquis.caisse.data.local.entity.DiningTableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiningTableDao {
    @Query("SELECT * FROM dining_tables WHERE is_active = 1 ORDER BY number COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<DiningTableEntity>>

    @Query("SELECT * FROM dining_tables WHERE is_active = 1 ORDER BY number COLLATE NOCASE ASC")
    suspend fun listActive(): List<DiningTableEntity>

    @Query("SELECT * FROM dining_tables WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiningTableEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DiningTableEntity): Long

    @Update
    suspend fun update(entity: DiningTableEntity)

    @Query("UPDATE dining_tables SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM dining_tables")
    suspend fun count(): Int
}
