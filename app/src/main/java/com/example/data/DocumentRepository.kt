package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.util.DateSearchParser
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DocumentRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun generateNewDocumentId(userId: String): String {
        return db.collection("users").document(userId).collection("documents").document().id
    }

    fun observeDocuments(userId: String): Flow<List<ScannedDocument>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank." }
        val path = "users/$userId/documents"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("documents")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ScannedDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<ScannedDocument> { it.filedDateIso }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
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
        )
    }

    suspend fun createDocument(docId: String, doc: ScannedDocument): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents/$docId"
        return try {
            val uid = requireUserId()
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

            val docRef = db.collection("users").document(uid).collection("documents").document(docId)
            docPath = docRef.path

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
                "previewBase64" to doc.previewBase64.take(155000),
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
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents/$docId"
        return try {
            val uid = requireUserId()
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

            val docRef = db.collection("users").document(uid).collection("documents").document(docId)
            docPath = docRef.path

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
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents/$docId"
        return try {
            val uid = requireUserId()
            require(docId.isNotBlank() && docId.matches(ID_REGEX)) { "Invalid document ID." }
            val docRef = db.collection("users").document(uid).collection("documents").document(docId)
            docPath = docRef.path

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
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/documents/$docId"
        return try {
            val uid = requireUserId()
            require(docId.isNotBlank() && docId.matches(ID_REGEX)) { "Invalid document ID." }
            val docRef = db.collection("users").document(uid).collection("documents").document(docId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private companion object {
        val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
