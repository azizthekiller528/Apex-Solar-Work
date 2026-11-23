package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        SiteExpenseEntity::class,
        SiteMaterialRecordEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class DocFolioDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun siteExpenseDao(): SiteExpenseDao

    companion object {
        private const val DATABASE_NAME = "docfolio_local_cache.db"

        @Volatile
        private var instance: DocFolioDatabase? = null

        fun getInstance(context: Context): DocFolioDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocFolioDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
