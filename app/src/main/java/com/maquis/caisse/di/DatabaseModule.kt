package com.maquis.caisse.di

import android.content.Context
import androidx.room.Room
import com.maquis.caisse.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fournit l'instance unique de la base Room à toute l'application.
 *
 * Emplacement où seront ajoutées, sprint après sprint, les Migrations
 * explicites (ex: .addMigrations(MIGRATION_1_2, MIGRATION_2_3, ...)).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "maquis_caisse.db"

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            // .addMigrations(...) sera ajouté dès qu'une entité existera (Sprint 1+)
            .build()
    }
}
