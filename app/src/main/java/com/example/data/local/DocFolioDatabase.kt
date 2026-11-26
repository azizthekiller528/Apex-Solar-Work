package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentEntity::class,
        SiteExpenseEntity::class,
        SiteMaterialRecordEntity::class,
        SiteLocationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class DocFolioDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun siteExpenseDao(): SiteExpenseDao
    abstract fun siteLocationDao(): SiteLocationDao

    companion object {
        private const val DATABASE_NAME = "docfolio_local_cache.db"

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_locations` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `siteType` TEXT NOT NULL,
                        `addressOrVillage` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `googleMapsUrl` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: DocFolioDatabase? = null

        fun getInstance(context: Context): DocFolioDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocFolioDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
