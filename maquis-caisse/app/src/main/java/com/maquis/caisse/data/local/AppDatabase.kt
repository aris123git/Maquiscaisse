package com.maquis.caisse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base Room unique de l'application (offline-first).
 *
 * SPRINT 0 : socle vide. Aucune entité métier n'est encore déclarée —
 * elles seront ajoutées sprint par sprint (Product au Sprint 1, Sale/
 * SaleItem au Sprint 2, Table/Order/CashSession/Voucher/Debt ensuite).
 *
 * Règle absolue pour tous les sprints suivants : toute évolution de ce
 * schéma doit passer par une Migration explicite (voir DatabaseModule),
 * jamais par fallbackToDestructiveMigration().
 */
@Database(
    entities = [],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase()
