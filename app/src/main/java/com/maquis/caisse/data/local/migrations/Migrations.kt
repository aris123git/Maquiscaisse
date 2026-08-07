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

    /** Sessions de caisse : ouverture/fermeture automatique à chaque login/logout. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `caisse_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `user_id` INTEGER NOT NULL,
                    `user_name` TEXT NOT NULL,
                    `opened_at` INTEGER NOT NULL,
                    `closed_at` INTEGER,
                    `sales_count` INTEGER NOT NULL DEFAULT 0,
                    `total_amount` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_caisse_sessions_user_id` ON `caisse_sessions` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_caisse_sessions_opened_at` ON `caisse_sessions` (`opened_at`)")
        }
    }

    /** Commandes maquis, users, tables, catégories, stock, audit, settings. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    `is_active` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `users` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `pin` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `permissions` TEXT NOT NULL,
                    `is_active` INTEGER NOT NULL,
                    `is_waitress` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_name` ON `users` (`name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `dining_tables` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `number` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `capacity` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `is_active` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dining_tables_number` ON `dining_tables` (`number`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `orders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `public_id` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `table_id` INTEGER,
                    `table_label` TEXT,
                    `waitress_id` INTEGER,
                    `waitress_name` TEXT,
                    `total_amount` INTEGER NOT NULL,
                    `paid_amount` INTEGER NOT NULL,
                    `note` TEXT,
                    `created_by_user_id` INTEGER,
                    `created_by_name` TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_orders_public_id` ON `orders` (`public_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_status` ON `orders` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_created_at` ON `orders` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_waitress_id` ON `orders` (`waitress_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_table_id` ON `orders` (`table_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `order_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `order_id` INTEGER NOT NULL,
                    `product_id` INTEGER NOT NULL,
                    `product_name` TEXT NOT NULL,
                    `category_name` TEXT NOT NULL,
                    `unit_price` INTEGER NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `line_total` INTEGER NOT NULL,
                    FOREIGN KEY(`order_id`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_order_id` ON `order_items` (`order_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_product_id` ON `order_items` (`product_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_category_name` ON `order_items` (`category_name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `order_payments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `order_id` INTEGER NOT NULL,
                    `payment_mode` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `amount_tendered` INTEGER NOT NULL,
                    `change_amount` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `user_id` INTEGER,
                    `user_name` TEXT,
                    FOREIGN KEY(`order_id`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_payments_order_id` ON `order_payments` (`order_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_payments_created_at` ON `order_payments` (`created_at`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stock_movements` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `product_id` INTEGER NOT NULL,
                    `product_name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `previous_stock` INTEGER NOT NULL,
                    `new_stock` INTEGER NOT NULL,
                    `motif` TEXT NOT NULL,
                    `supplier` TEXT,
                    `comment` TEXT,
                    `user_id` INTEGER,
                    `user_name` TEXT,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_product_id` ON `stock_movements` (`product_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_created_at` ON `stock_movements` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_user_id` ON `stock_movements` (`user_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `audit_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `user_id` INTEGER,
                    `user_name` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `details` TEXT NOT NULL,
                    `old_value` TEXT,
                    `new_value` TEXT,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_created_at` ON `audit_logs` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_user_id` ON `audit_logs` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_action` ON `audit_logs` (`action`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_settings` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent(),
            )
        }
    }
}
