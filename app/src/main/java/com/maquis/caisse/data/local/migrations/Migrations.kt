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

    /** Sprint 2 : tables `sales` + `sale_items`. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sales` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `total_amount` INTEGER NOT NULL,
                    `payment_mode` TEXT NOT NULL,
                    `cash_amount` INTEGER NOT NULL,
                    `mobile_money_amount` INTEGER NOT NULL,
                    `voucher_amount` INTEGER NOT NULL,
                    `debt_amount` INTEGER NOT NULL,
                    `amount_tendered` INTEGER NOT NULL,
                    `change_amount` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sale_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sale_id` INTEGER NOT NULL,
                    `product_id` INTEGER NOT NULL,
                    `product_name` TEXT NOT NULL,
                    `unit_price` INTEGER NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `line_total` INTEGER NOT NULL,
                    FOREIGN KEY(`sale_id`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sale_items_sale_id` ON `sale_items` (`sale_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sale_items_product_id` ON `sale_items` (`product_id`)",
            )
        }
    }
}
