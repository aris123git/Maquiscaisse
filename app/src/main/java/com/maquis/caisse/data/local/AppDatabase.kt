package com.maquis.caisse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.maquis.caisse.data.local.dao.AuditLogDao
import com.maquis.caisse.data.local.dao.AvoirDao
import com.maquis.caisse.data.local.dao.CaisseSessionDao
import com.maquis.caisse.data.local.dao.CategoryDao
import com.maquis.caisse.data.local.dao.DetteDao
import com.maquis.caisse.data.local.dao.DiningTableDao
import com.maquis.caisse.data.local.dao.OrderDao
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.dao.SaleDao
import com.maquis.caisse.data.local.dao.SettingsDao
import com.maquis.caisse.data.local.dao.StockMovementDao
import com.maquis.caisse.data.local.dao.UserDao
import com.maquis.caisse.data.local.entity.AppSettingEntity
import com.maquis.caisse.data.local.entity.AuditLogEntity
import com.maquis.caisse.data.local.entity.AvoirEntity
import com.maquis.caisse.data.local.entity.CaisseSessionEntity
import com.maquis.caisse.data.local.entity.CategoryEntity
import com.maquis.caisse.data.local.entity.DettePaiementEntity
import com.maquis.caisse.data.local.entity.DetteEntity
import com.maquis.caisse.data.local.entity.DiningTableEntity
import com.maquis.caisse.data.local.entity.OrderEntity
import com.maquis.caisse.data.local.entity.OrderItemEntity
import com.maquis.caisse.data.local.entity.OrderPaymentEntity
import com.maquis.caisse.data.local.entity.ProductEntity
import com.maquis.caisse.data.local.entity.SaleEntity
import com.maquis.caisse.data.local.entity.SaleItemEntity
import com.maquis.caisse.data.local.entity.StockMovementEntity
import com.maquis.caisse.data.local.entity.UserEntity

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        CategoryEntity::class,
        UserEntity::class,
        DiningTableEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        OrderPaymentEntity::class,
        StockMovementEntity::class,
        AuditLogEntity::class,
        AppSettingEntity::class,
        CaisseSessionEntity::class,
        DetteEntity::class,
        DettePaiementEntity::class,
        AvoirEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userDao(): UserDao
    abstract fun diningTableDao(): DiningTableDao
    abstract fun orderDao(): OrderDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun settingsDao(): SettingsDao
    abstract fun caisseSessionDao(): CaisseSessionDao
    abstract fun detteDao(): DetteDao
    abstract fun avoirDao(): AvoirDao
}
