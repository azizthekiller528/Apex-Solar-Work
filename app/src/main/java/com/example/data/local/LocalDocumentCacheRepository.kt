package com.example.data.local

import com.example.model.DateMatchMode
import com.example.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository abstracting Room SQLite metadata cache operations.
 * Mirrors cloud-synced documents locally (filename, dates, local file paths, OCR content)
 * and provides reactive Flow queries by keyword and/or date range without feeding writes back to Firestore.
 */
class LocalDocumentCacheRepository(
    private val documentDao: DocumentDao
) {
    fun observeCachedDocuments(userId: String): Flow<List<DocumentEntity>> =
        documentDao.observeDocumentsForUser(userId)

    fun observeCachedDocumentsByDate(userId: String, dateIso: String): Flow<List<DocumentEntity>> =
        documentDao.observeDocumentsByDate(userId, dateIso)

    fun searchCachedDocuments(userId: String, query: String): Flow<List<DocumentEntity>> =
        documentDao.searchDocuments(userId, escapeLikePattern(query.trim()))

    fun queryDocumentsByKeywordOrDateRange(
        userId: String,
        keyword: String = "",
        startDateIso: String? = null,
        endDateIso: String? = null,
        category: String? = null,
        dateMatchMode: DateMatchMode = DateMatchMode.EITHER
    ): Flow<List<DocumentEntity>> {
        val normalizedStart = startDateIso?.takeIf { it.isNotBlank() }
        val normalizedEnd = endDateIso?.takeIf { it.isNotBlank() }
        val (orderedStart, orderedEnd) = if (
            normalizedStart != null && normalizedEnd != null && normalizedStart > normalizedEnd
        ) {
            normalizedEnd to normalizedStart
        } else {
            normalizedStart to normalizedEnd
        }

        return documentDao.queryDocumentsByKeywordOrDateRange(
            userId = userId,
            escapedKeyword = escapeLikePattern(keyword.trim()),
            startDateIso = orderedStart,
            endDateIso = orderedEnd,
            category = category,
            dateMatchMode = dateMatchMode.name
        )
    }

    suspend fun getCachedDocument(docId: String): DocumentEntity? = withContext(Dispatchers.IO) {
        documentDao.getById(docId)
    }

    suspend fun syncFromCloud(
        userId: String,
        documents: List<ScannedDocument>,
        docsDirPath: String
    ) = withContext(Dispatchers.IO) {
        val entities = documents
            .filter { it.id.isNotBlank() }
            .map { it.toDocumentEntity(docsDirPath) }
        documentDao.replaceUserCache(userId, entities)
    }

    suspend fun upsertDocument(
        document: ScannedDocument,
        docsDirPath: String
    ) = withContext(Dispatchers.IO) {
        if (document.id.isNotBlank()) {
            documentDao.upsertDocument(document.toDocumentEntity(docsDirPath))
        }
    }

    suspend fun deleteById(docId: String) = withContext(Dispatchers.IO) {
        if (docId.isNotBlank()) {
            documentDao.deleteById(docId)
        }
    }

    private fun escapeLikePattern(raw: String): String {
        if (raw.isEmpty()) return ""
        return buildString(raw.length) {
            for (ch in raw) {
                when (ch) {
                    '\\', '%', '_' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
        }
    }
}
