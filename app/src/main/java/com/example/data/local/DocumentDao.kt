package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE userId = :userId ORDER BY filedDateIso DESC, updatedAtMillis DESC")
    fun observeDocumentsForUser(userId: String): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE userId = :userId AND (filedDateIso = :dateIso OR documentDateIso = :dateIso)
        ORDER BY updatedAtMillis DESC
        """
    )
    fun observeDocumentsByDate(userId: String, dateIso: String): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE userId = :userId
          AND (
              ocrContent LIKE '%' || :query || '%'
              OR title LIKE '%' || :query || '%'
              OR fileName LIKE '%' || :query || '%'
          )
        ORDER BY filedDateIso DESC, updatedAtMillis DESC
        """
    )
    fun searchDocuments(userId: String, query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<DocumentEntity>)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM documents WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("DELETE FROM documents WHERE userId = :userId AND id NOT IN (:activeIds)")
    suspend fun deleteNotInForUser(userId: String, activeIds: List<String>)

    @Transaction
    suspend fun replaceUserCache(userId: String, documents: List<DocumentEntity>) {
        if (documents.isEmpty()) {
            clearForUser(userId)
        } else {
            upsertDocuments(documents)
            deleteNotInForUser(userId, documents.map { it.id })
        }
    }
}
