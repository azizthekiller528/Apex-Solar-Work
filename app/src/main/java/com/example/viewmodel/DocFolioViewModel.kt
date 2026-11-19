package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DocumentRepository
import com.example.data.GeminiOcrService
import com.example.data.local.DocumentEntity
import com.example.data.local.LocalDocumentCacheRepository
import com.example.model.DateMatchMode
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.model.UiState
import com.example.util.DateSearchParser
import com.example.util.ImageStorageHelper
import com.example.util.ParsedDateQuery
import com.example.util.SearchablePdfGenerator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class ScanDraft(
    val bitmap: Bitmap,
    val title: String,
    val category: DocumentCategory,
    val documentDateIso: String,
    val filedDateIso: String,
    val referenceNumber: String,
    val organization: String,
    val contractorOrParty: String,
    val summary: String,
    val extractedFieldsText: String,
    val ocrFullText: String
)

sealed interface ScanFlowState {
    data object Idle : ScanFlowState
    data class ProcessingOcr(val previewBitmap: Bitmap) : ScanFlowState
    data class Review(val draft: ScanDraft, val isSaving: Boolean = false) : ScanFlowState
    data class Error(val message: String, val previewBitmap: Bitmap? = null) : ScanFlowState
}

data class VaultFilterState(
    val query: String = "",
    val selectedCategory: DocumentCategory? = null,
    val explicitDateIso: String? = null,
    val useAlternateDateFormat: Boolean = false,
    val dateMatchMode: DateMatchMode = DateMatchMode.EITHER
) {
    val parsedQueryDate: ParsedDateQuery?
        get() = DateSearchParser.parseDateQuery(query)

    val activeDateIso: String?
        get() = explicitDateIso ?: parsedQueryDate?.let { parsed ->
            if (useAlternateDateFormat && parsed.alternateIso != null) {
                parsed.alternateIso
            } else {
                parsed.primaryIso
            }
        }
}

sealed interface OneShotEvent {
    data class ShowMessage(val text: String) : OneShotEvent
    data class SharePdf(val file: File, val subject: String) : OneShotEvent
    data class OpenPdf(val file: File) : OneShotEvent
    data class SavePdfWithSaf(val sourceFile: File, val suggestedName: String) : OneShotEvent
    data class OpenDocumentDetail(val docId: String) : OneShotEvent
}

class DocFolioViewModel(
    private val repository: DocumentRepository,
    private val ocrService: GeminiOcrService,
    private val currentUserId: String,
    private val localCache: LocalDocumentCacheRepository? = null,
    private val docsDirPath: String = ""
) : ViewModel() {

    val documentsState: StateFlow<UiState<List<ScannedDocument>>> =
        repository.observeDocuments(currentUserId)
            .onEach { docs ->
                if (localCache != null) {
                    try {
                        localCache.syncFromCloud(currentUserId, docs, docsDirPath)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Local Room cache sync warning: ${e.message}")
                    }
                }
            }
            .map<List<ScannedDocument>, UiState<List<ScannedDocument>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing documents", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load documents"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val cachedLocalDocuments: StateFlow<List<DocumentEntity>> =
        (localCache?.observeCachedDocuments(currentUserId) ?: emptyFlow())
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Local Room cache read warning: ${error.message}")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    private val _filterState = MutableStateFlow(VaultFilterState())
    val filterState: StateFlow<VaultFilterState> = _filterState.asStateFlow()

    private val _selectedDateLookupIso = MutableStateFlow(DateSearchParser.todayIso())
    val selectedDateLookupIso: StateFlow<String> = _selectedDateLookupIso.asStateFlow()

    private val _dateLookupMode = MutableStateFlow(DateMatchMode.EITHER)
    val dateLookupMode: StateFlow<DateMatchMode> = _dateLookupMode.asStateFlow()

    private val _scanState = MutableStateFlow<ScanFlowState>(ScanFlowState.Idle)
    val scanState: StateFlow<ScanFlowState> = _scanState.asStateFlow()

    private val _isExportingPdf = MutableStateFlow(false)
    val isExportingPdf: StateFlow<Boolean> = _isExportingPdf.asStateFlow()

    private val _events = MutableSharedFlow<OneShotEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<OneShotEvent> = _events.asSharedFlow()

    val filteredDocuments: StateFlow<List<ScannedDocument>> =
        combine(documentsState, _filterState) { state, filter ->
            val docs = (state as? UiState.Success)?.data ?: return@combine emptyList()
            applyVaultFilter(docs, filter)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun updateSearchQuery(newQuery: String) {
        val current = _filterState.value
        val typedDate = DateSearchParser.parseDateQuery(newQuery)
        _filterState.value = current.copy(
            query = newQuery,
            explicitDateIso = if (typedDate != null) null else current.explicitDateIso,
            useAlternateDateFormat = false
        )
    }

    fun toggleAlternateDateInterpretation() {
        _filterState.value = _filterState.value.copy(
            useAlternateDateFormat = !_filterState.value.useAlternateDateFormat
        )
    }

    fun setExplicitDateFilter(isoDate: String?) {
        val current = _filterState.value
        val queryIsDate = DateSearchParser.parseDateQuery(current.query) != null
        _filterState.value = current.copy(
            explicitDateIso = isoDate,
            query = if (isoDate != null && queryIsDate) "" else current.query,
            useAlternateDateFormat = false
        )
    }

    fun setCategoryFilter(category: DocumentCategory?) {
        _filterState.value = _filterState.value.copy(
            selectedCategory = if (_filterState.value.selectedCategory == category) null else category
        )
    }

    fun setVaultDateMatchMode(mode: DateMatchMode) {
        _filterState.value = _filterState.value.copy(dateMatchMode = mode)
    }

    fun clearVaultFilters() {
        _filterState.value = VaultFilterState()
    }

    fun selectLookupDate(isoDate: String) {
        if (DateSearchParser.isValidIsoDate(isoDate)) {
            _selectedDateLookupIso.value = isoDate
        }
    }

    fun setDateLookupMode(mode: DateMatchMode) {
        _dateLookupMode.value = mode
    }

    fun startScanFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = ImageStorageHelper.loadOrientedBitmapFromUri(context, uri)
                runOcrOnBitmap(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load scan photo", e)
                _scanState.value = ScanFlowState.Error(e.localizedMessage ?: "Could not load selected image.")
            }
        }
    }

    fun startScanFromBundledAsset(context: Context, assetPath: String) {
        viewModelScope.launch {
            try {
                val bitmap = ImageStorageHelper.loadOrientedBitmapFromAsset(context, assetPath)
                runOcrOnBitmap(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bundled form asset", e)
                _scanState.value = ScanFlowState.Error(e.localizedMessage ?: "Could not load sample form.")
            }
        }
    }

    fun retryOcrOnLastBitmap() {
        val current = _scanState.value
        val bitmap = (current as? ScanFlowState.Error)?.previewBitmap ?: return
        viewModelScope.launch {
            runOcrOnBitmap(bitmap)
        }
    }

    fun proceedManualFromError() {
        val current = _scanState.value
        val bitmap = (current as? ScanFlowState.Error)?.previewBitmap ?: return
        _scanState.value = ScanFlowState.Review(
            draft = ScanDraft(
                bitmap = bitmap,
                title = "Scanned Document",
                category = DocumentCategory.OTHER,
                documentDateIso = "",
                filedDateIso = DateSearchParser.todayIso(),
                referenceNumber = "",
                organization = "",
                contractorOrParty = "",
                summary = "",
                extractedFieldsText = "",
                ocrFullText = ""
            )
        )
    }

    private suspend fun runOcrOnBitmap(bitmap: Bitmap) {
        _scanState.value = ScanFlowState.ProcessingOcr(bitmap)
        try {
            val result = ocrService.analyzeDocumentPhoto(bitmap)
            _scanState.value = ScanFlowState.Review(
                draft = ScanDraft(
                    bitmap = bitmap,
                    title = result.title,
                    category = result.category,
                    documentDateIso = result.documentDateIso,
                    filedDateIso = DateSearchParser.todayIso(),
                    referenceNumber = result.referenceNumber,
                    organization = result.organization,
                    contractorOrParty = result.contractorOrParty,
                    summary = result.summary,
                    extractedFieldsText = result.extractedFields.joinToString("\n"),
                    ocrFullText = result.ocrFullText
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _scanState.value = ScanFlowState.Error(
                message = e.localizedMessage ?: "AI OCR analysis failed.",
                previewBitmap = bitmap
            )
        }
    }

    fun updateScanDraft(transform: (ScanDraft) -> ScanDraft) {
        val current = _scanState.value
        if (current is ScanFlowState.Review && !current.isSaving) {
            _scanState.value = current.copy(draft = transform(current.draft))
        }
    }

    fun dismissScanFlow() {
        _scanState.value = ScanFlowState.Idle
    }

    fun saveScannedDocument(context: Context) {
        val current = _scanState.value
        if (current !is ScanFlowState.Review || current.isSaving) return
        val draft = current.draft
        _scanState.value = current.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val docId = repository.generateNewDocumentId(currentUserId)
                val imageFileName = "${docId}_scan.jpg"
                val pdfFileName = "${docId}_searchable.pdf"

                ImageStorageHelper.saveScanBitmapLocally(context, imageFileName, draft.bitmap)
                val previewBase64 = ImageStorageHelper.encodeCompactPreviewBase64(draft.bitmap)

                val fieldsList = draft.extractedFieldsText
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(30)

                val newDoc = ScannedDocument(
                    id = docId,
                    userId = currentUserId,
                    title = draft.title.trim().ifBlank { "Scanned Document" },
                    category = draft.category.name,
                    documentDateIso = DateSearchParser.normalizeToIsoOrEmpty(draft.documentDateIso),
                    filedDateIso = DateSearchParser.normalizeToIsoOrEmpty(draft.filedDateIso)
                        .ifBlank { DateSearchParser.todayIso() },
                    referenceNumber = draft.referenceNumber.trim(),
                    organization = draft.organization.trim(),
                    contractorOrParty = draft.contractorOrParty.trim(),
                    summary = draft.summary.trim(),
                    extractedFields = fieldsList,
                    ocrFullText = draft.ocrFullText.trim(),
                    localImageFileName = imageFileName,
                    localPdfFileName = pdfFileName,
                    previewBase64 = previewBase64,
                    pageCount = 1L,
                    starred = false
                )

                // Generate real searchable PDF locally
                SearchablePdfGenerator.generateDocumentPdf(
                    context = context,
                    document = newDoc,
                    scanBitmap = draft.bitmap,
                    targetFileName = pdfFileName
                )

                val result = repository.createDocument(docId, newDoc)
                result.fold(
                    onSuccess = { savedId ->
                        runCatching { localCache?.upsertDocument(newDoc, docsDirPath) }
                        _scanState.value = ScanFlowState.Idle
                        _selectedDateLookupIso.value = newDoc.filedDateIso
                        _events.emit(OneShotEvent.ShowMessage("Saved & generated searchable PDF"))
                        _events.emit(OneShotEvent.OpenDocumentDetail(savedId))
                    },
                    onFailure = { err ->
                        _scanState.value = current.copy(isSaving = false)
                        _events.emit(
                            OneShotEvent.ShowMessage(
                                err.localizedMessage ?: "Failed to save document to cloud."
                            )
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving scanned document", e)
                _scanState.value = current.copy(isSaving = false)
                _events.emit(OneShotEvent.ShowMessage(e.localizedMessage ?: "Error saving document."))
            }
        }
    }

    fun updateExistingDocument(
        context: Context,
        existing: ScannedDocument,
        title: String,
        category: DocumentCategory,
        documentDateIso: String,
        filedDateIso: String,
        referenceNumber: String,
        organization: String,
        contractorOrParty: String,
        summary: String,
        extractedFields: List<String>,
        ocrFullText: String
    ) {
        viewModelScope.launch {
            val pdfFileName = existing.localPdfFileName.ifBlank { "${existing.id}_searchable.pdf" }
            val updatedCopy = existing.copy(
                title = title.trim().ifBlank { existing.title },
                category = category.name,
                documentDateIso = DateSearchParser.normalizeToIsoOrEmpty(documentDateIso),
                filedDateIso = DateSearchParser.normalizeToIsoOrEmpty(filedDateIso)
                    .ifBlank { existing.filedDateIso },
                referenceNumber = referenceNumber.trim(),
                organization = organization.trim(),
                contractorOrParty = contractorOrParty.trim(),
                summary = summary.trim(),
                extractedFields = extractedFields,
                ocrFullText = ocrFullText.trim(),
                localPdfFileName = pdfFileName
            )

            // Regenerate searchable PDF with updated metadata/OCR
            try {
                val bmp = ImageStorageHelper.loadDocumentBitmap(
                    context = context,
                    localImageFileName = updatedCopy.localImageFileName,
                    previewBase64 = updatedCopy.previewBase64
                )
                SearchablePdfGenerator.generateDocumentPdf(
                    context = context,
                    document = updatedCopy,
                    scanBitmap = bmp,
                    targetFileName = pdfFileName
                )
            } catch (e: Exception) {
                Log.w(TAG, "PDF regeneration warning during edit: ${e.message}")
            }

            val res = repository.updateDocumentMetadata(
                docId = existing.id,
                title = updatedCopy.title,
                category = category,
                documentDateIso = updatedCopy.documentDateIso,
                filedDateIso = updatedCopy.filedDateIso,
                referenceNumber = updatedCopy.referenceNumber,
                organization = updatedCopy.organization,
                contractorOrParty = updatedCopy.contractorOrParty,
                summary = updatedCopy.summary,
                extractedFields = updatedCopy.extractedFields,
                ocrFullText = updatedCopy.ocrFullText,
                localPdfFileName = pdfFileName
            )
            res.fold(
                onSuccess = {
                    runCatching { localCache?.upsertDocument(updatedCopy, docsDirPath) }
                    _events.emit(OneShotEvent.ShowMessage("Document & PDF updated"))
                },
                onFailure = { err ->
                    _events.emit(OneShotEvent.ShowMessage(err.localizedMessage ?: "Update failed"))
                }
            )
        }
    }

    fun toggleStar(doc: ScannedDocument) {
        viewModelScope.launch {
            repository.toggleStarred(doc.id, !doc.starred).onFailure { err ->
                _events.emit(OneShotEvent.ShowMessage(err.localizedMessage ?: "Could not update star"))
            }
        }
    }

    fun deleteDocument(context: Context, doc: ScannedDocument, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteDocument(doc.id).fold(
                onSuccess = {
                    runCatching { localCache?.deleteById(doc.id) }
                    ImageStorageHelper.deleteLocalFiles(
                        context = context,
                        imageFileName = doc.localImageFileName,
                        pdfFileName = doc.localPdfFileName
                    )
                    _events.emit(OneShotEvent.ShowMessage("Document deleted"))
                    onDeleted()
                },
                onFailure = { err ->
                    _events.emit(OneShotEvent.ShowMessage(err.localizedMessage ?: "Delete failed"))
                }
            )
        }
    }

    fun prepareSingleDocumentPdf(
        context: Context,
        doc: ScannedDocument,
        action: PdfAction
    ) {
        if (_isExportingPdf.value) return
        _isExportingPdf.value = true
        viewModelScope.launch {
            try {
                val fileName = doc.localPdfFileName.ifBlank { "${doc.id}_searchable.pdf" }
                val existingFile = ImageStorageHelper.getLocalFile(context, fileName)
                val pdfFile = if (existingFile != null) {
                    existingFile
                } else {
                    val bmp = ImageStorageHelper.loadDocumentBitmap(
                        context = context,
                        localImageFileName = doc.localImageFileName,
                        previewBase64 = doc.previewBase64
                    )
                    SearchablePdfGenerator.generateDocumentPdf(context, doc, bmp, fileName)
                }
                val cleanName = doc.title
                    .replace(Regex("[^a-zA-Z0-9_\\-]+"), "_")
                    .take(40)
                    .trim('_')
                    .ifBlank { "DocFolio_Document" } + ".pdf"

                when (action) {
                    PdfAction.SHARE -> _events.emit(OneShotEvent.SharePdf(pdfFile, doc.title))
                    PdfAction.OPEN -> _events.emit(OneShotEvent.OpenPdf(pdfFile))
                    PdfAction.SAVE -> _events.emit(OneShotEvent.SavePdfWithSaf(pdfFile, cleanName))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF export failed", e)
                _events.emit(OneShotEvent.ShowMessage(e.localizedMessage ?: "Could not prepare PDF"))
            } finally {
                _isExportingPdf.value = false
            }
        }
    }

    fun exportDailyBundlePdf(
        context: Context,
        dateIso: String,
        documents: List<ScannedDocument>
    ) {
        if (documents.isEmpty() || _isExportingPdf.value) return
        _isExportingPdf.value = true
        viewModelScope.launch {
            try {
                val bundleFile = SearchablePdfGenerator.generateDailyBundlePdf(
                    context = context,
                    dateIso = dateIso,
                    documents = documents
                )
                val subject = "DocFolio Daily Filing Report — ${DateSearchParser.formatSlashDate(dateIso)}"
                _events.emit(OneShotEvent.SharePdf(bundleFile, subject))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Daily PDF bundle export failed", e)
                _events.emit(OneShotEvent.ShowMessage(e.localizedMessage ?: "Could not export daily PDF"))
            } finally {
                _isExportingPdf.value = false
            }
        }
    }

    companion object {
        private const val TAG = "DocFolioVM"

        fun applyVaultFilter(
            docs: List<ScannedDocument>,
            filter: VaultFilterState
        ): List<ScannedDocument> {
            val activeDate = filter.activeDateIso
            val rawQuery = filter.query.trim()
            val isPureDateQuery = filter.parsedQueryDate != null && filter.explicitDateIso == null

            return docs.filter { doc ->
                val matchesCategory = filter.selectedCategory == null ||
                    doc.categoryEnum == filter.selectedCategory

                val matchesDate = if (activeDate != null) {
                    matchesDocumentOnDate(doc, activeDate, filter.dateMatchMode)
                } else {
                    true
                }

                val matchesText = if (rawQuery.isBlank() || isPureDateQuery) {
                    true
                } else {
                    val needle = rawQuery.lowercase()
                    doc.title.lowercase().contains(needle) ||
                        doc.referenceNumber.lowercase().contains(needle) ||
                        doc.organization.lowercase().contains(needle) ||
                        doc.contractorOrParty.lowercase().contains(needle) ||
                        doc.summary.lowercase().contains(needle) ||
                        doc.ocrFullText.lowercase().contains(needle) ||
                        doc.extractedFields.any { it.lowercase().contains(needle) } ||
                        DateSearchParser.formatSlashDate(doc.filedDateIso).contains(needle) ||
                        DateSearchParser.formatSlashDate(doc.documentDateIso).contains(needle)
                }

                matchesCategory && matchesDate && matchesText
            }
        }

        fun matchesDocumentOnDate(
            doc: ScannedDocument,
            targetIso: String,
            mode: DateMatchMode
        ): Boolean = when (mode) {
            DateMatchMode.EITHER -> doc.filedDateIso == targetIso || doc.documentDateIso == targetIso
            DateMatchMode.FILED_ONLY -> doc.filedDateIso == targetIso
            DateMatchMode.DOCUMENT_ONLY -> doc.documentDateIso == targetIso
        }
    }
}

enum class PdfAction {
    OPEN,
    SHARE,
    SAVE
}
