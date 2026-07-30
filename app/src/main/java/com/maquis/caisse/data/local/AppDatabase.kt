package com.maquis.caisse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.dao.SaleDao
import com.maquis.caisse.data.local.entity.ProductEntity
import com.maquis.caisse.data.local.entity.SaleEntity
import com.maquis.caisse.data.local.entity.SaleItemEntity

/**
 * Base Room unique de l'application (offline-first).
 *
 * Version 1 (Sprint 0) : socle vide.
 * Version 2 (Sprint 1) : table `products`.
 * Version 3 (Sprint 2) : tables `sales` + `sale_items`.
 */
@Database(
    entities = [ProductEntity::class, SaleEntity::class, SaleItemEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
}
