package com.maquis.caisse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.entity.ProductEntity

/**
 * Base Room unique de l'application (offline-first).
 *
 * Version 1 (Sprint 0) : socle vide.
 * Version 2 (Sprint 1) : table `products`.
 *
 * Toute évolution passe par une Migration explicite (voir DatabaseModule /
 * Migrations), jamais par fallbackToDestructiveMigration().
 */
@Database(
    entities = [ProductEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}
