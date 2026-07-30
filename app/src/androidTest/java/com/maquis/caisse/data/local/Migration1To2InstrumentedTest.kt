package com.maquis.caisse.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maquis.caisse.data.local.migrations.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test de migration Room 1→2 (création table products).
 * Crée une base v1 vide (Sprint 0) sans dépendre du fichier de schéma exporté,
 * puis ouvre AppDatabase v2 avec MIGRATION_1_2.
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2InstrumentedTest {

    private val testDb = "migration-test-products"

    @Test
    fun migrate1To2_createsProductsTable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        // Simule la base Sprint 0 (version 1, aucune table métier).
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // Schéma vide volontairement.
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .addMigrations(Migrations.MIGRATION_1_2)
            .build()

        try {
            db.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
            db.openHelper.writableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='products'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("products", cursor.getString(0))
            }
            assertTrue(db.productDao().observeAll() != null)
        } finally {
            db.close()
            context.deleteDatabase(testDb)
        }
    }
}
