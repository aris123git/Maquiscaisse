package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.maquis.caisse.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query(
        """
        SELECT * FROM products
        WHERE is_active = 1
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Décrémente le stock seulement s'il est suffisant.
     * @return nombre de lignes modifiées (0 = stock insuffisant ou produit absent).
     */
    @Query(
        """
        UPDATE products
        SET stock = stock - :quantity
        WHERE id = :productId AND stock >= :quantity AND is_active = 1
        """,
    )
    suspend fun decreaseStockIfAvailable(productId: Long, quantity: Int): Int
}
