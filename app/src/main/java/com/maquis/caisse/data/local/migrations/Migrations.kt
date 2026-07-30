package com.maquis.caisse.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations Room explicites — jamais de fallback destructif.
 *
 * Sprint 0 livrait la base vide en version 1.
 * Sprint 1 crée la table `products` (version 2).
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `products` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `sale_price` INTEGER NOT NULL,
                    `purchase_price` INTEGER NOT NULL,
                    `stock` INTEGER NOT NULL,
                    `alert_threshold` INTEGER NOT NULL,
                    `image_path` TEXT,
                    `is_active` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
}
