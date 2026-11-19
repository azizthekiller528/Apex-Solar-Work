package com.example.data.local

import com.example.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository abstracting Room SQLite metadata cache operations.
 * Mirrors cloud-synced documents locally (filename, dates, local file paths, OCR content)
 * without ever feeding writes back into Firestore.
 */
class LocalDocumentCacheRepository(
    private val documentDao: DocumentDao
) {
    fun observeCachedDocuments(userId: String): Flow<List<DocumentEntity>> =
        documentDao.observeDocumentsForUser(userId)

    fun observeCachedDocumentsByDate(userId: String, dateIso: String): Flow<List<DocumentEntity>> =
        documentDao.observeDocumentsByDate(userId, dateIso)

    fun searchCachedDocuments(userId: String, query: String): Flow<List<DocumentEntity>> =
        documentDao.searchDocuments(userId, query)

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
}
