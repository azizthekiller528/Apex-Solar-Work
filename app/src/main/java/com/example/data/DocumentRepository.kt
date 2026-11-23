package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.DateMatchMode
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.util.DateSearchParser
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

class DocumentRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val companyCollection: CollectionReference
        get() = db.collection(COMPANY_COLLECTION_NAME)

    fun getEffectiveUserId(): String {
        return auth.currentUser?.uid ?: "apex_team"
    }

    fun generateNewDocumentId(userId: String = "apex_team"): String {
        return companyCollection.document().id
    }

    fun observeDocuments(userId: String = "apex_team"): Flow<List<ScannedDocument>> = flow {
        val path = COMPANY_DOCS_PATH
        emitAll(
            companyCollection
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ScannedDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(DOCUMENT_SORT_ORDER)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Executes live Firestore queries scoped to `/users/{userId}/documents` filtered by
     * date range (`filedDateIso` / `documentDateIso`), category, and/or keyword.
     *
     * Uses single-field Firestore range/equality queries exclusively so no custom composite
     * indexes are required (preventing FAILED_PRECONDITION index errors at runtime).
     * For [DateMatchMode.EITHER], combines two live single-field Firestore range queries
     * and deduplicates matching documents by ID.
     */
    fun queryDocumentsByKeywordOrDateRange(
        userId: String = "apex_team",
        keyword: String = "",
        startDateIso: String? = null,
        endDateIso: String? = null,
        category: DocumentCategory? = null,
        dateMatchMode: DateMatchMode = DateMatchMode.EITHER
    ): Flow<List<ScannedDocument>> = flow {
        val path = COMPANY_DOCS_PATH
        val collection = companyCollection
        val (orderedStart, orderedEnd) = normalizeDateRangeBounds(startDateIso, endDateIso)
        val hasDateFilter = orderedStart != null || orderedEnd != null
        val normalizedKeyword = keyword.trim().lowercase(Locale.US)

        val baseStream: Flow<List<ScannedDocument>> = when {
            hasDateFilter -> {
                when (dateMatchMode) {
                    DateMatchMode.FILED_ONLY -> {
                        observeSingleFieldQuery(
                            query = buildDateRangeQuery(
                                collection = collection,
                                fieldName = "filedDateIso",
                                startIso = orderedStart,
                                endIso = orderedEnd,
                                excludeBlankLowerBound = false
                            ),
                            userId = userId,
                            path = path
                        )
                    }

                    DateMatchMode.DOCUMENT_ONLY -> {
                        observeSingleFieldQuery(
                            query = buildDateRangeQuery(
                                collection = collection,
                                fieldName = "documentDateIso",
                                startIso = orderedStart,
                                endIso = orderedEnd,
                                excludeBlankLowerBound = true
                            ),
                            userId = userId,
                            path = path
                        )
                    }

                    DateMatchMode.EITHER -> {
                        val filedFlow = observeSingleFieldQuery(
                            query = buildDateRangeQuery(
                                collection = collection,
                                fieldName = "filedDateIso",
                                startIso = orderedStart,
                                endIso = orderedEnd,
                                excludeBlankLowerBound = false
                            ),
                            userId = userId,
                            path = path
                        )
                        val docDateFlow = observeSingleFieldQuery(
                            query = buildDateRangeQuery(
                                collection = collection,
                                fieldName = "documentDateIso",
                                startIso = orderedStart,
                                endIso = orderedEnd,
                                excludeBlankLowerBound = true
                            ),
                            userId = userId,
                            path = path
                        )
                        combine(filedFlow, docDateFlow) { byFiled, byDocDate ->
                            mergeDistinctDocuments(byFiled, byDocDate)
                        }
                    }
                }
            }

            category != null -> {
                observeSingleFieldQuery(
                    query = collection
                        .whereEqualTo("category", category.name)
                        .limit(QUERY_LIMIT),
                    userId = userId,
                    path = path
                )
            }

            else -> {
                observeSingleFieldQuery(
                    query = collection
                        .orderBy("filedDateIso", Query.Direction.DESCENDING)
                        .limit(QUERY_LIMIT),
                    userId = userId,
                    path = path
                )
            }
        }

        emitAll(
            baseStream.map { docs ->
                docs.filter { doc ->
                    val categoryMatches = category == null || doc.categoryEnum == category
                    val keywordMatches = matchesKeyword(doc, normalizedKeyword)
                    categoryMatches && keywordMatches
                }.sortedWith(DOCUMENT_SORT_ORDER)
            }
        )
    }

    private fun buildDateRangeQuery(
        collection: CollectionReference,
        fieldName: String,
        startIso: String?,
        endIso: String?,
        excludeBlankLowerBound: Boolean
    ): Query {
        var query: Query = collection
        val lowerBound = startIso ?: if (excludeBlankLowerBound) MIN_NON_BLANK_ISO_DATE else null
        if (lowerBound != null) {
            query = query.whereGreaterThanOrEqualTo(fieldName, lowerBound)
        }
        if (endIso != null) {
            query = query.whereLessThanOrEqualTo(fieldName, endIso)
        }
        return query
            .orderBy(fieldName, Query.Direction.DESCENDING)
            .limit(QUERY_LIMIT)
    }

    private fun observeSingleFieldQuery(
        query: Query,
        userId: String,
        path: String
    ): Flow<List<ScannedDocument>> {
        return query
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ScannedDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getDocument(docId: String): Result<ScannedDocument?> {
        val docRef = companyCollection.document(docId)
        val docPath = docRef.path
        return try {
            require(docId.isNotBlank() && docId.matches(ID_REGEX)) { "Invalid document ID." }
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val doc = snapshot.toObject(ScannedDocument::class.java)
                Result.success(doc)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun createDocument(docId: String, doc: ScannedDocument): Result<String> {
        val docRef = companyCollection.document(docId)
        val docPath = docRef.path
        return try {
            val uid = auth.currentUser?.uid ?: doc.userId.ifBlank { "apex_team" }
            require(docId.isNotBlank() && docId.matches(ID_REGEX)) { "Invalid document ID." }
            val sanitizedTitle = doc.title.trim().ifBlank { "Scanned Document" }.take(200)
            val sanitizedFiledDate = if (DateSearchParser.isValidIsoDate(doc.filedDateIso)) {
                doc.filedDateIso
            } else {
                DateSearchParser.todayIso()
            }
            val sanitizedDocDate = if (doc.documentDateIso.isBlank()) {
                ""
            } else {
                DateSearchParser.normalizeToIsoOrEmpty(doc.documentDateIso)
            }
            val sanitizedCategory = DocumentCategory.fromRaw(doc.category).name
            val sanitizedFields = doc.extractedFields
                .map { it.trim().take(290) }
                .filter { it.isNotBlank() }
                .take(30)

            // Exact 18-key payload matching firestore.rules (NO 'id' field because @DocumentId is used)
            val payload = mapOf<String, Any>(
                "userId" to uid,
                "title" to sanitizedTitle,
                "category" to sanitizedCategory,
                "documentDateIso" to sanitizedDocDate,
                "filedDateIso" to sanitizedFiledDate,
                "referenceNumber" to doc.referenceNumber.trim().take(120),
                "organization" to doc.organization.trim().take(200),
                "contractorOrParty" to doc.contractorOrParty.trim().take(200),
                "summary" to doc.summary.trim().take(1000),
                "extractedFields" to sanitizedFields,
                "ocrFullText" to doc.ocrFullText.trim().take(50000),
                "localImageFileName" to doc.localImageFileName.trim().take(200),
                "localPdfFileName" to doc.localPdfFileName.trim().take(200),
                "previewBase64" to doc.previewBase64.take(280000),
                "pageCount" to doc.pageCount.coerceIn(1L, 50L),
                "starred" to doc.starred,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDocumentMetadata(
        docId: String,
        title: String,
        category: DocumentCategory,
        documentDateIso: String,
        filedDateIso: String,
        referenceNumber: String,
        organization: String,
        contractorOrParty: String,
        summary: String,
        extractedFields: List<String>,
        ocrFullText: String,
        localPdfFileName: String
    ): Result<Unit> {
        val docRef = companyCollection.document(docId)
        val docPath = docRef.path
        return try {
            require(docId.isNotBlank() && docId.matches(ID_REGEX)) { "Invalid document ID." }
            val safeTitle = title.trim().ifBlank { "Scanned Document" }.take(200)
            val safeFiled = if (DateSearchParser.isValidIsoDate(filedDateIso)) {
                filedDateIso
            } else {
                DateSearchParser.todayIso()
            }
            val safeDocDate = if (documentDateIso.isBlank()) {
                ""
            } else {
                DateSearchParser.normalizeToIsoOrEmpty(documentDateIso)
            }
            val safeFields = extractedFields
                .map { it.trim().take(290) }
                .filter { it.isNotBlank() }
                .take(30)

            // Matches isEditDocumentAction() in firestore.rules
            docRef.update(
                mapOf<String, Any>(
                    "title" to safeTitle,
                    "category" to category.name,
                    "documentDateIso" to safeDocDate,
                    "filedDateIso" to safeFiled,
                    "referenceNumber" to referenceNumber.trim().take(120),
                    "organization" to organization.trim().take(200),
                    "contractorOrParty" to contractorOrParty.trim().take(200),
                    "summary" to summary.trim().take(1000),
                    "extractedFields" to safeFields,
                    "ocrFullText" to ocrFullText.trim().take(50000),
                    "localPdfFileName" to localPdfFileName.trim().take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleStarred(docId: String, starred: Boolean): Result<Unit> {
        val docRef = companyCollection.document(docId)
        val docPath = docRef.path
        return try {
            require(docId.isNotBlank() && docId.matches(ID_REGEX)) { "Invalid document ID." }

            // Matches isToggleStarAction() in firestore.rules
            docRef.update(
                mapOf<String, Any>(
                    "starred" to starred,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(docId: String): Result<Unit> {
        val docRef = companyCollection.document(docId)
        val docPath = docRef.path
        return try {
            require(docId.isNotBlank() && docId.matches(ID_REGEX)) { "Invalid document ID." }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        const val COMPANY_COLLECTION_NAME = "company_documents"
        const val COMPANY_DOCS_PATH = "company_documents"
        private val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
        private const val QUERY_LIMIT = 500L
        private const val MIN_NON_BLANK_ISO_DATE = "0001-01-01"

        private val DOCUMENT_SORT_ORDER = compareByDescending<ScannedDocument> { it.filedDateIso }
            .thenByDescending { it.createdAt?.seconds ?: 0L }

        internal fun normalizeDateRangeBounds(
            startDateIso: String?,
            endDateIso: String?
        ): Pair<String?, String?> {
            val validStart = startDateIso?.trim()?.takeIf { DateSearchParser.isValidIsoDate(it) }
            val validEnd = endDateIso?.trim()?.takeIf { DateSearchParser.isValidIsoDate(it) }
            return if (validStart != null && validEnd != null && validStart > validEnd) {
                validEnd to validStart
            } else {
                validStart to validEnd
            }
        }

        internal fun mergeDistinctDocuments(
            first: List<ScannedDocument>,
            second: List<ScannedDocument>
        ): List<ScannedDocument> {
            val merged = LinkedHashMap<String, ScannedDocument>(first.size + second.size)
            for (doc in first) {
                merged[doc.id] = doc
            }
            for (doc in second) {
                merged.putIfAbsent(doc.id, doc)
            }
            return merged.values.toList()
        }

        internal fun matchesKeyword(doc: ScannedDocument, normalizedKeyword: String): Boolean {
            if (normalizedKeyword.isBlank()) return true
            val tokens = normalizedKeyword.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return true
            val haystack = buildString {
                append(doc.title).append(' ')
                append(doc.localPdfFileName).append(' ')
                append(doc.referenceNumber).append(' ')
                append(doc.organization).append(' ')
                append(doc.contractorOrParty).append(' ')
                append(doc.summary).append(' ')
                append(doc.extractedFields.joinToString(" ")).append(' ')
                append(doc.ocrFullText).append(' ')
                append(doc.categoryEnum.displayName).append(' ')
                append(doc.categoryEnum.shortLabel).append(' ')
                append(doc.filedDateIso).append(' ')
                append(doc.documentDateIso).append(' ')
                if (doc.filedDateIso.isNotBlank()) {
                    append(DateSearchParser.formatSlashDate(doc.filedDateIso)).append(' ')
                }
                if (doc.documentDateIso.isNotBlank()) {
                    append(DateSearchParser.formatSlashDate(doc.documentDateIso))
                }
            }.lowercase(Locale.US)
            return tokens.all { token -> haystack.contains(token) }
        }
    }
}
