package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE (:userId = '' OR :userId = :userId) ORDER BY filedDateIso DESC, updatedAtMillis DESC")
    fun observeDocumentsForUser(userId: String = ""): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int

    @Query(
        """
        SELECT * FROM documents
        WHERE (:userId = '' OR :userId = :userId) AND (filedDateIso = :dateIso OR documentDateIso = :dateIso)
        ORDER BY updatedAtMillis DESC
        """
    )
    fun observeDocumentsByDate(userId: String, dateIso: String): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE (:userId = '' OR :userId = :userId)
          AND (
            ocrContent LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE
            OR title LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE
            OR fileName LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE
        )
        ORDER BY filedDateIso DESC, updatedAtMillis DESC
        """
    )
    fun searchDocuments(userId: String, query: String): Flow<List<DocumentEntity>>

    /**
     * Reactive Room query supporting live keyword search, inclusive ISO date-range filtering
     * (single day, open-ended start/end, or bounded start..end), optional category filter,
     * and dual-date matching mode (EITHER, FILED_ONLY, DOCUMENT_ONLY).
     */
    @Query(
        """
        SELECT * FROM documents
        WHERE (:userId = '' OR :userId = :userId)
          AND (
            :escapedKeyword = ''
            OR title LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR fileName LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR referenceNumber LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR organization LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR contractorOrParty LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR summary LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR extractedFieldsText LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR ocrContent LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR filedDateIso LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
            OR documentDateIso LIKE '%' || :escapedKeyword || '%' ESCAPE '\' COLLATE NOCASE
        )
          AND (
              :category IS NULL
              OR category = :category
          )
          AND (
              (:startDateIso IS NULL AND :endDateIso IS NULL)
              OR (
                  :dateMatchMode = 'FILED_ONLY'
                  AND (:startDateIso IS NULL OR filedDateIso >= :startDateIso)
                  AND (:endDateIso IS NULL OR filedDateIso <= :endDateIso)
              )
              OR (
                  :dateMatchMode = 'DOCUMENT_ONLY'
                  AND documentDateIso != ''
                  AND (:startDateIso IS NULL OR documentDateIso >= :startDateIso)
                  AND (:endDateIso IS NULL OR documentDateIso <= :endDateIso)
              )
              OR (
                  :dateMatchMode = 'EITHER'
                  AND (
                      (
                          (:startDateIso IS NULL OR filedDateIso >= :startDateIso)
                          AND (:endDateIso IS NULL OR filedDateIso <= :endDateIso)
                      )
                      OR (
                          documentDateIso != ''
                          AND (:startDateIso IS NULL OR documentDateIso >= :startDateIso)
                          AND (:endDateIso IS NULL OR documentDateIso <= :endDateIso)
                      )
                  )
              )
          )
        ORDER BY filedDateIso DESC, updatedAtMillis DESC
        """
    )
    fun queryDocumentsByKeywordOrDateRange(
        userId: String,
        escapedKeyword: String,
        startDateIso: String?,
        endDateIso: String?,
        category: String? = null,
        dateMatchMode: String = "EITHER"
    ): Flow<List<DocumentEntity>>

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

    @Query("DELETE FROM documents WHERE userId = :userId AND id NOT IN (:activeIds) AND id NOT LIKE 'apex_doc_%' AND id NOT LIKE 'doc_%'")
    suspend fun deleteNotInForUser(userId: String, activeIds: List<String>)

    @Transaction
    suspend fun replaceUserCache(userId: String, documents: List<DocumentEntity>) {
        if (documents.isNotEmpty()) {
            upsertDocuments(documents)
            deleteNotInForUser(userId, documents.map { it.id })
        }
    }
}
