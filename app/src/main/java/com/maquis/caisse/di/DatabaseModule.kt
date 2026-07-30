package com.maquis.caisse.di

import android.content.Context
import androidx.room.Room
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.migrations.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fournit l'instance unique de la base Room et les DAO.
 * Les Migrations explicites sont enregistrées ici, sprint après sprint.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "maquis_caisse.db"

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(Migrations.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
}
