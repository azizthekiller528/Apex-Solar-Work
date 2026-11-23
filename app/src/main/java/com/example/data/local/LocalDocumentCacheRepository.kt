package com.example.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.example.model.DateMatchMode
import com.example.model.ScannedDocument
import com.example.util.ImageStorageHelper
import com.example.util.ThumbnailImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

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

    suspend fun seedFromAssetsIfEmpty(context: Context, userId: String = "apex_team") = withContext(Dispatchers.IO) {
        try {
            val count = documentDao.getDocumentCount()
            if (count > 0) return@withContext

            val jsonString = context.assets.open("seed_documents.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val docsDir = ImageStorageHelper.getDocsDirectory(context)
            val entities = mutableListOf<DocumentEntity>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val docId = obj.getString("id")
                val fileName = "${docId}.jpg"
                val destFile = File(docsDir, fileName)

                if (!destFile.exists() || destFile.length() == 0L) {
                    try {
                        context.assets.open("seed_photos/$fileName").use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("LocalDocumentCache", "Could not copy seed photo for $fileName: ${e.message}")
                    }
                }

                val extractedList = mutableListOf<String>()
                val extArr = obj.optJSONArray("extractedFields")
                if (extArr != null) {
                    for (j in 0 until extArr.length()) {
                        extractedList.add(extArr.getString(j))
                    }
                }

                entities.add(
                    DocumentEntity(
                        id = docId,
                        userId = userId,
                        fileName = fileName,
                        title = obj.optString("title", "Document"),
                        category = obj.optString("category", "MATERIAL_SLIP"),
                        documentDateIso = obj.optString("documentDateIso", ""),
                        filedDateIso = obj.optString("filedDateIso", ""),
                        referenceNumber = obj.optString("referenceNumber", ""),
                        organization = obj.optString("organization", ""),
                        contractorOrParty = obj.optString("contractorOrParty", ""),
                        summary = obj.optString("summary", ""),
                        extractedFieldsText = extractedList.joinToString("\n"),
                        localImagePath = destFile.absolutePath,
                        localPdfPath = "",
                        ocrContent = extractedList.joinToString("\n") + "\n" + obj.optString("summary", ""),
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            if (entities.isNotEmpty()) {
                documentDao.upsertDocuments(entities)
                Log.i("LocalDocumentCache", "Successfully seeded ${entities.size} documents from assets.")
            }
        } catch (e: Exception) {
            Log.e("LocalDocumentCache", "Failed to seed documents from assets: ${e.message}", e)
        }
    }

    /**
     * Overwrites any sideways seed photos cached locally with upright portrait assets.
     */
    suspend fun syncUpdatedSeedPhotos(context: Context) = withContext(Dispatchers.IO) {
        try {
            val docsDir = ImageStorageHelper.getDocsDirectory(context)
            val seedPhotos = context.assets.list("seed_photos") ?: return@withContext
            var updatedAny = false
            for (fileName in seedPhotos) {
                val destFile = File(docsDir, fileName)
                if (!destFile.exists() || destFile.length() == 0L) {
                    try {
                        context.assets.open("seed_photos/$fileName").use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        updatedAny = true
                    } catch (_: Exception) {}
                } else {
                    // Check if existing file is landscape (width > height)
                    try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(destFile.absolutePath, opts)
                        if (opts.outWidth > opts.outHeight) {
                            context.assets.open("seed_photos/$fileName").use { input ->
                                destFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            updatedAny = true
                            Log.i("LocalDocumentCache", "Straightened sideways photo to portrait: $fileName")
                        }
                    } catch (_: Exception) {}
                }
            }
            if (updatedAny) {
                ThumbnailImageLoader.clearCache(context)
            }

            // Also refresh metadata/line items from seed_documents.json if typos exist in cache
            try {
                val jsonString = context.assets.open("seed_documents.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val docId = obj.getString("id")
                    val existing = documentDao.getById(docId)
                    if (existing != null && (existing.extractedFieldsText.contains("THETT") ||
                            existing.extractedFieldsText.contains("COLUM ") ||
                            existing.extractedFieldsText.contains("PLAT:") ||
                            existing.extractedFieldsText.contains("PUERLING") ||
                            existing.extractedFieldsText.contains("CANECTION") ||
                            existing.extractedFieldsText.contains("CONNECTION PLATE: 95.000 EA") ||
                            existing.extractedFieldsText.contains("CONNECTION PLATE: 95") ||
                            existing.summary.contains("50 PV Modules"))) {
                        val extractedList = mutableListOf<String>()
                        val extArr = obj.optJSONArray("extractedFields")
                        if (extArr != null) {
                            for (j in 0 until extArr.length()) {
                                extractedList.add(extArr.getString(j))
                            }
                        }
                        val updated = existing.copy(
                            title = obj.optString("title", existing.title),
                            summary = obj.optString("summary", existing.summary),
                            extractedFieldsText = extractedList.joinToString("\n"),
                            ocrContent = extractedList.joinToString("\n") + "\n" + obj.optString("summary", ""),
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        documentDao.upsertDocument(updated)
                        Log.i("LocalDocumentCache", "Corrected OCR typos/quantities in cache for doc $docId")
                    }
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }
}
