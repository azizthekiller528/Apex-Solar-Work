package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteExpenseDao {
    @Query("SELECT * FROM site_expenses ORDER BY dateIso DESC, createdAtMillis DESC")
    fun getAllExpensesFlow(): Flow<List<SiteExpenseEntity>>

    @Query("SELECT * FROM site_expenses WHERE dateIso = :dateIso ORDER BY createdAtMillis ASC")
    fun getExpensesByDateFlow(dateIso: String): Flow<List<SiteExpenseEntity>>

    @Query("SELECT * FROM site_expenses WHERE dateIso = :dateIso ORDER BY createdAtMillis ASC")
    suspend fun getExpensesByDate(dateIso: String): List<SiteExpenseEntity>

    @Query("SELECT DISTINCT dateIso FROM site_expenses WHERE dateIso != ''")
    suspend fun getAllExpenseDates(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<SiteExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultExpenses(expenses: List<SiteExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: SiteExpenseEntity)

    @Query("DELETE FROM site_expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("DELETE FROM site_expenses WHERE dateIso = :dateIso")
    suspend fun deleteExpensesForDate(dateIso: String)

    @Query("DELETE FROM site_expenses WHERE dateIso = :dateIso AND id NOT IN (:validIds)")
    suspend fun deleteExpensesNotIn(dateIso: String, validIds: List<String>)

    @Query("SELECT COUNT(*) FROM site_expenses")
    suspend fun getCount(): Int

    @Query("SELECT * FROM site_materials ORDER BY dateIso DESC, sNo ASC")
    fun getAllMaterialsFlow(): Flow<List<SiteMaterialRecordEntity>>

    @Query("SELECT * FROM site_materials WHERE dateIso = :dateIso ORDER BY sNo ASC")
    fun getMaterialsByDateFlow(dateIso: String): Flow<List<SiteMaterialRecordEntity>>

    @Query("SELECT * FROM site_materials WHERE dateIso = :dateIso ORDER BY sNo ASC")
    suspend fun getMaterialsByDate(dateIso: String): List<SiteMaterialRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(materials: List<SiteMaterialRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: SiteMaterialRecordEntity)

    @Query("DELETE FROM site_materials WHERE id = :id")
    suspend fun deleteMaterial(id: String)

    @Query("DELETE FROM site_materials WHERE dateIso = :dateIso")
    suspend fun deleteMaterialsForDate(dateIso: String)

    @Query("DELETE FROM site_materials WHERE dateIso = :dateIso AND id NOT IN (:validIds)")
    suspend fun deleteMaterialsNotIn(dateIso: String, validIds: List<String>)

    @Query("SELECT COUNT(*) FROM site_materials")
    suspend fun getMaterialCount(): Int
}
