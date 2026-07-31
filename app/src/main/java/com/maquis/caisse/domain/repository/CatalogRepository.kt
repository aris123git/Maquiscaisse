package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.model.DiningTable
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.StockMovement
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeActive(): Flow<List<Category>>
    fun observeAll(): Flow<List<Category>>
    suspend fun add(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun deactivate(id: Long)
}

interface TableRepository {
    fun observeActive(): Flow<List<DiningTable>>
    suspend fun add(number: String, name: String, capacity: Int): Long
    suspend fun update(table: DiningTable)
}

interface UserRepository {
    fun observeActive(): Flow<List<AppUser>>
    fun observeWaitresses(): Flow<List<AppUser>>
    suspend fun listWaitresses(): List<AppUser>
    suspend fun add(user: AppUser): Long
    suspend fun update(user: AppUser)
    suspend fun login(name: String, pin: String): AppUser?
}

interface StockRepository {
    fun observeMovements(limit: Int = 200): Flow<List<StockMovement>>
    suspend fun adjust(
        productId: Long,
        type: String,
        quantity: Int,
        motif: String,
        supplier: String? = null,
        comment: String? = null,
        absoluteNewStock: Int? = null,
    )
}

interface SettingsRepository {
    suspend fun get(key: String, default: String = ""): String
    fun observe(key: String): Flow<String?>
    suspend fun set(key: String, value: String)
    suspend fun isPrintEnabled(): Boolean
    suspend fun setPrintEnabled(enabled: Boolean)
}
