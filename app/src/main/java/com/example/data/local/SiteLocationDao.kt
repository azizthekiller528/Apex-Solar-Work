package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteLocationDao {
    @Query("SELECT * FROM site_locations ORDER BY isPinned DESC, updatedAtMillis DESC, createdAtMillis ASC")
    fun getAllSitesFlow(): Flow<List<SiteLocationEntity>>

    @Query("SELECT * FROM site_locations ORDER BY isPinned DESC, updatedAtMillis DESC, createdAtMillis ASC")
    suspend fun getAllSites(): List<SiteLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSite(site: SiteLocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSites(sites: List<SiteLocationEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultSites(sites: List<SiteLocationEntity>)

    @Query("DELETE FROM site_locations WHERE id = :id")
    suspend fun deleteSite(id: String)

    @Query("DELETE FROM site_locations WHERE id NOT IN (:validIds)")
    suspend fun deleteSitesNotIn(validIds: List<String>)

    @Query("DELETE FROM site_locations")
    suspend fun deleteAllSites()

    @Query("SELECT COUNT(*) FROM site_locations")
    suspend fun getCount(): Int
}
