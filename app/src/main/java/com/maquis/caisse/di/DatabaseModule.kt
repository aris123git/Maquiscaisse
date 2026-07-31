package com.maquis.caisse.di

import android.content.Context
import androidx.room.Room
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.dao.AuditLogDao
import com.maquis.caisse.data.local.dao.CategoryDao
import com.maquis.caisse.data.local.dao.DiningTableDao
import com.maquis.caisse.data.local.dao.OrderDao
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.dao.SaleDao
import com.maquis.caisse.data.local.dao.SettingsDao
import com.maquis.caisse.data.local.dao.StockMovementDao
import com.maquis.caisse.data.local.dao.UserDao
import com.maquis.caisse.data.local.migrations.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "maquis_caisse.db"

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                Migrations.MIGRATION_1_2,
                Migrations.MIGRATION_2_3,
                Migrations.MIGRATION_3_4,
            )
            .build()
    }

    @Provides fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideDiningTableDao(db: AppDatabase): DiningTableDao = db.diningTableDao()
    @Provides fun provideOrderDao(db: AppDatabase): OrderDao = db.orderDao()
    @Provides fun provideStockMovementDao(db: AppDatabase): StockMovementDao = db.stockMovementDao()
    @Provides fun provideAuditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
