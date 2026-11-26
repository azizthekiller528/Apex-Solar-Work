package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DocumentRepository
import com.example.data.GeminiOcrService
import com.example.data.MediaRelayService
import com.example.data.SiteExpenseRepository
import com.example.data.SiteLocationRepository
import com.example.data.local.DocumentEntity
import com.example.data.local.LocalDocumentCacheRepository
import com.example.data.local.toScannedDocument
import com.example.data.model.SiteExpense
import com.example.data.model.SiteLocation
import com.example.data.model.SiteLocationParser
import com.example.data.model.SiteMaterialRecord
import com.example.model.DateMatchMode
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.model.UiState
import com.example.util.AppUpdateInfo
import com.example.util.AppUpdateManager
import com.example.util.DateSearchParser
import com.example.util.ImageStorageHelper
import com.example.util.ParsedDateQuery
import com.example.util.ParsedDateRange
import com.example.util.SearchablePdfGenerator
import com.example.util.ThumbnailImageLoader
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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
    data class Review(val draft: ScanDraft, val isSaving: Boolean = false) : ScanFlowState
    data class Error(val message: String, val previewBitmap: Bitmap? = null) : ScanFlowState
}

data class VaultFilterState(
    val query: String = "",
    val selectedCategory: DocumentCategory? = null,
    val explicitDateIso: String? = null,
    val rangeStartDateIso: String? = null,
    val rangeEndDateIso: String? = null,
    val useAlternateDateFormat: Boolean = false,
    val dateMatchMode: DateMatchMode = DateMatchMode.EITHER
) {
    val parsedQueryDate: ParsedDateQuery?
        get() = DateSearchParser.parseDateQuery(query)

    val parsedQueryDateRange: ParsedDateRange?
        get() = DateSearchParser.parseDateRangeQuery(query)

    val hasExplicitRange: Boolean
        get() = rangeStartDateIso != null || rangeEndDateIso != null

    val activeDateIso: String?
        get() = if (hasExplicitRange || parsedQueryDateRange != null) {
            null
        } else {
            explicitDateIso ?: parsedQueryDate?.let { parsed ->
                if (useAlternateDateFormat && parsed.alternateIso != null) {
                    parsed.alternateIso
                } else {
                    parsed.primaryIso
                }
            }
        }

    val effectiveDateRange: Pair<String?, String?>
        get() = when {
            hasExplicitRange -> rangeStartDateIso to rangeEndDateIso
            parsedQueryDateRange != null -> parsedQueryDateRange!!.let { it.startIso to it.endIso }
            activeDateIso != null -> activeDateIso to activeDateIso
            else -> null to null
        }

    val hasActiveDateRange: Boolean
        get() = hasExplicitRange || parsedQueryDateRange != null

    val isPureDateOrRangeQuery: Boolean
        get() = (parsedQueryDate != null || parsedQueryDateRange != null) &&
            explicitDateIso == null &&
            !hasExplicitRange

    val hasAnyActiveFilter: Boolean
        get() = query.isNotBlank() ||
            selectedCategory != null ||
            explicitDateIso != null ||
            hasExplicitRange
}

sealed interface OneShotEvent {
    data class ShowMessage(val text: String) : OneShotEvent
    data class SharePdf(val file: File, val subject: String) : OneShotEvent
    data class OpenPdf(val file: File) : OneShotEvent
    data class SavePdfWithSaf(val sourceFile: File, val suggestedName: String) : OneShotEvent
    data class OpenDocumentDetail(val docId: String) : OneShotEvent
}

sealed interface FirestoreSearchState {
    data object Idle : FirestoreSearchState
    data object Loading : FirestoreSearchState
    data class Success(
        val filter: VaultFilterState,
        val documents: List<ScannedDocument>
    ) : FirestoreSearchState
    data class Error(val message: String) : FirestoreSearchState
}

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(val info: AppUpdateInfo) : AppUpdateState
    data class Downloading(val info: AppUpdateInfo, val percent: Int) : AppUpdateState
    data class ReadyToInstall(val apkFile: File) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

class DocFolioViewModel(
    private val repository: DocumentRepository,
    private val currentUserId: String,
    private val localCache: LocalDocumentCacheRepository? = null,
    private val docsDirPath: String = "",
    private val siteExpenseRepository: SiteExpenseRepository? = null,
    private val siteLocationRepository: SiteLocationRepository? = null
) : ViewModel() {

    private val localDocsFlow: Flow<List<ScannedDocument>> =
        (localCache?.observeCachedDocuments(currentUserId) ?: emptyFlow())
            .map { list ->
                list.map { it.toScannedDocument() }
                    .filterNot { DocumentRepository.isHiddenFromVault(it) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Local Room cache read warning: ${error.message}")
                emit(emptyList())
            }
            .onStart { emit(emptyList()) }

    private val cloudDocsFlow: Flow<List<ScannedDocument>?> =
        repository.observeDocuments(currentUserId)
            .map<List<ScannedDocument>, List<ScannedDocument>?> { it }
            .onEach { docs ->
                if (!docs.isNullOrEmpty()) {
                    if (localCache != null) {
                        try {
                            localCache.syncFromCloud(currentUserId, docs, docsDirPath)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Local Room cache sync warning: ${e.message}")
                        }
                    }
                    if (siteExpenseRepository != null) {
                        try {
                            siteExpenseRepository.syncFromCloudDocuments(docs)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Site expenses cloud sync warning: ${e.message}")
                        }
                    }
                    if (siteLocationRepository != null) {
                        try {
                            siteLocationRepository.syncFromCloudDocuments(docs)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Site locations cloud sync warning: ${e.message}")
                        }
                    }
                }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Cloud Firestore observe warning: ${error.message}")
                emit(null)
            }
            .onStart { emit(null) }

    val documentsState: StateFlow<UiState<List<ScannedDocument>>> =
        combine(localDocsFlow, cloudDocsFlow) { localDocs, cloudDocs ->
            val cloud = (cloudDocs ?: emptyList()).filterNot { DocumentRepository.isHiddenFromVault(it) }
            val docs = when {
                cloud.isEmpty() -> localDocs
                localDocs.isEmpty() -> cloud
                else -> DocumentRepository.mergeDistinctDocuments(cloud, localDocs)
                    .sortedWith(DocumentRepository.DOCUMENT_SORT_ORDER)
            }
            UiState.Success(docs)
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

    private val ocrService = GeminiOcrService()
    private val _isExtractingWithAi = MutableStateFlow(false)
    val isExtractingWithAi: StateFlow<Boolean> = _isExtractingWithAi.asStateFlow()

    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()

    private val _events = MutableSharedFlow<OneShotEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<OneShotEvent> = _events.asSharedFlow()

    private val _searchRetryTrigger = MutableStateFlow(0)

    val allExpenses: StateFlow<List<SiteExpense>> =
        (siteExpenseRepository?.allExpensesFlow() ?: emptyFlow())
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Site expenses observe error: ${error.message}")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val allMaterials: StateFlow<List<SiteMaterialRecord>> =
        (siteExpenseRepository?.allMaterialsFlow() ?: emptyFlow())
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Site materials observe error: ${error.message}")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    private val _selectedExpenseDateIso = MutableStateFlow<String>("")
    val selectedExpenseDateIso: StateFlow<String> = _selectedExpenseDateIso.asStateFlow()

    val allSiteLocations: StateFlow<List<SiteLocation>> =
        (siteLocationRepository?.allSitesFlow() ?: emptyFlow())
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Site locations observe error: ${error.message}")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    private val _isSavingSiteLocation = MutableStateFlow(false)
    val isSavingSiteLocation: StateFlow<Boolean> = _isSavingSiteLocation.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                siteExpenseRepository?.seedInitialDataIfEmpty()
                syncAllLocalDaysToCloud()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to seed initial site expenses: ${e.message}")
            }
            try {
                val locRepo = siteLocationRepository
                if (locRepo != null) {
                    locRepo.seedDefaultSitesIfNeeded()
                    val cloudResult = repository.getDocument(SiteLocationRepository.CLOUD_SYNC_DOC_ID)
                    if (cloudResult.isSuccess) {
                        val existingCloud = cloudResult.getOrNull()
                        val cloudSites = existingCloud
                            ?.takeIf { it.ocrFullText.contains(SiteLocationRepository.LOCATIONS_DELIMITER_START) }
                            ?.let { SiteLocationRepository.parseLocationsFromOcrText(it.ocrFullText) }
                            .orEmpty()
                        val cloudHasV27Pack = cloudSites.any { it.id in SiteLocationRepository.V2_7_ADDED_SITE_IDS }
                        if (existingCloud == null || (!locRepo.hasSyncedSitePackToCloud() && !cloudHasV27Pack)) {
                            locRepo.insertSitesIfMissing(SiteLocationRepository.DEFAULT_SITE_LOCATIONS)
                            if (syncAllSiteLocationsToCloud()) {
                                locRepo.markSitePackSyncedToCloud()
                            }
                        } else {
                            locRepo.markSitePackSyncedToCloud()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to seed initial site locations: ${e.message}")
            }
        }
    }

    @OptIn(FlowPreview::class)
    private val debouncedFilterState: Flow<VaultFilterState> = _filterState
        .debounce { filter ->
            if (filter.query.isBlank() ||
                filter.explicitDateIso != null ||
                filter.hasExplicitRange ||
                filter.parsedQueryDate != null ||
                filter.parsedQueryDateRange != null
            ) {
                0L
            } else {
                220L
            }
        }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val firestoreSearchState: StateFlow<FirestoreSearchState> =
        combine(debouncedFilterState, _searchRetryTrigger) { filter, _ -> filter }
            .flatMapLatest { filter ->
                if (!filter.hasAnyActiveFilter) {
                    flowOf(FirestoreSearchState.Idle)
                } else {
                    val keyword = if (filter.isPureDateOrRangeQuery) "" else filter.query.trim()
                    val (startIso, endIso) = filter.effectiveDateRange
                    repository.queryDocumentsByKeywordOrDateRange(
                        userId = currentUserId,
                        keyword = keyword,
                        startDateIso = startIso,
                        endDateIso = endIso,
                        category = filter.selectedCategory,
                        dateMatchMode = filter.dateMatchMode
                    )
                        .map<List<ScannedDocument>, FirestoreSearchState> { docs ->
                            FirestoreSearchState.Success(
                                filter = filter,
                                documents = docs.filterNot { DocumentRepository.isHiddenFromVault(it) }
                            )
                        }
                        .onStart { emit(FirestoreSearchState.Loading) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Firestore search query warning", rootError)
                            emit(
                                FirestoreSearchState.Error(
                                    rootError.localizedMessage ?: "Cloud search unavailable — showing cached results"
                                )
                            )
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = FirestoreSearchState.Idle
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val roomFilteredDocuments: StateFlow<List<DocumentEntity>?> =
        if (localCache == null) {
            MutableStateFlow(null)
        } else {
            _filterState
                .flatMapLatest { filter ->
                    val keyword = if (filter.isPureDateOrRangeQuery) "" else filter.query.trim()
                    val (startIso, endIso) = filter.effectiveDateRange
                    localCache.queryDocumentsByKeywordOrDateRange(
                        userId = currentUserId,
                        keyword = keyword,
                        startDateIso = startIso,
                        endDateIso = endIso,
                        category = filter.selectedCategory?.name,
                        dateMatchMode = filter.dateMatchMode
                    )
                }
                .map<List<DocumentEntity>, List<DocumentEntity>?> { list ->
                    list.filterNot { DocumentRepository.isHiddenFromVault(it.toScannedDocument()) }
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Local Room reactive query warning: ${error.message}")
                    emit(null)
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = null
                )
        }

    val filteredDocuments: StateFlow<List<ScannedDocument>> =
        combine(
            documentsState,
            _filterState,
            firestoreSearchState
        ) { state, filter, cloudSearch ->
            val docs = (state as? UiState.Success)?.data ?: emptyList()
            if (!filter.hasAnyActiveFilter) {
                docs
            } else if (cloudSearch is FirestoreSearchState.Success && cloudSearch.filter == filter && cloudSearch.documents.isNotEmpty()) {
                cloudSearch.documents
            } else {
                applyVaultFilter(docs, filter)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun retryFirestoreSearch() {
        _searchRetryTrigger.value += 1
    }

    fun updateSearchQuery(newQuery: String) {
        val current = _filterState.value
        val typedDate = DateSearchParser.parseDateQuery(newQuery)
        val typedRange = DateSearchParser.parseDateRangeQuery(newQuery)
        val clearExplicitDates = typedDate != null || typedRange != null
        _filterState.value = current.copy(
            query = newQuery,
            explicitDateIso = if (clearExplicitDates) null else current.explicitDateIso,
            rangeStartDateIso = if (clearExplicitDates) null else current.rangeStartDateIso,
            rangeEndDateIso = if (clearExplicitDates) null else current.rangeEndDateIso,
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
        val queryIsDateOrRange = DateSearchParser.parseDateQuery(current.query) != null ||
            DateSearchParser.parseDateRangeQuery(current.query) != null
        _filterState.value = current.copy(
            explicitDateIso = isoDate,
            rangeStartDateIso = if (isoDate != null) null else current.rangeStartDateIso,
            rangeEndDateIso = if (isoDate != null) null else current.rangeEndDateIso,
            query = if (isoDate != null && queryIsDateOrRange) "" else current.query,
            useAlternateDateFormat = false
        )
    }

    fun setDateRangeStart(isoDate: String?) {
        val current = _filterState.value
        setDateRange(
            startIso = isoDate,
            endIso = current.rangeEndDateIso ?: current.parsedQueryDateRange?.endIso
        )
    }

    fun setDateRangeEnd(isoDate: String?) {
        val current = _filterState.value
        setDateRange(
            startIso = current.rangeStartDateIso ?: current.parsedQueryDateRange?.startIso,
            endIso = isoDate
        )
    }

    fun setDateRange(startIso: String?, endIso: String?) {
        val current = _filterState.value
        val validStart = startIso?.takeIf { DateSearchParser.isValidIsoDate(it) }
        val validEnd = endIso?.takeIf { DateSearchParser.isValidIsoDate(it) }
        val (orderedStart, orderedEnd) = if (
            validStart != null && validEnd != null && validStart > validEnd
        ) {
            validEnd to validStart
        } else {
            validStart to validEnd
        }
        val queryIsDateOrRange = DateSearchParser.parseDateQuery(current.query) != null ||
            DateSearchParser.parseDateRangeQuery(current.query) != null
        val settingRange = orderedStart != null || orderedEnd != null
        _filterState.value = current.copy(
            explicitDateIso = if (settingRange) null else current.explicitDateIso,
            rangeStartDateIso = orderedStart,
            rangeEndDateIso = orderedEnd,
            query = if (settingRange && queryIsDateOrRange) "" else current.query,
            useAlternateDateFormat = false
        )
    }

    fun clearDateRange() {
        _filterState.value = _filterState.value.copy(
            rangeStartDateIso = null,
            rangeEndDateIso = null
        )
    }

    fun clearSearchAndDateFilters() {
        _filterState.value = _filterState.value.copy(
            query = "",
            explicitDateIso = null,
            rangeStartDateIso = null,
            rangeEndDateIso = null,
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
                openManualReview(bitmap)
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
                openManualReview(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bundled form asset", e)
                _scanState.value = ScanFlowState.Error(e.localizedMessage ?: "Could not load sample form.")
            }
        }
    }

    /**
     * Opens the review screen directly with an empty draft.
     *
     * Auto-OCR is intentionally disabled: on a phone uplink the Gemini request regularly
     * exceeded its timeout, so capturing a document now skips straight to manual entry.
     * The extraction path is preserved in [com.example.data.GeminiOcrService] and can be
     * re-wired here if a reliable OCR connection is available later.
     */
    private fun openManualReview(bitmap: Bitmap) {
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

    fun updateScanDraft(transform: (ScanDraft) -> ScanDraft) {
        val current = _scanState.value
        if (current is ScanFlowState.Review && !current.isSaving) {
            _scanState.value = current.copy(draft = transform(current.draft))
        }
    }

    fun checkForDuplicate(draft: ScanDraft): ScannedDocument? {
        val existingDocs = (documentsState.value as? UiState.Success)?.data ?: return null
        val draftRef = draft.referenceNumber.trim()
        val draftDocDate = DateSearchParser.normalizeToIsoOrEmpty(draft.documentDateIso)
        val draftTitle = draft.title.trim()

        return existingDocs.firstOrNull { doc ->
            val refMatch = draftRef.isNotBlank() &&
                doc.referenceNumber.isNotBlank() &&
                doc.referenceNumber.equals(draftRef, ignoreCase = true) &&
                draftDocDate.isNotBlank() &&
                doc.documentDateIso == draftDocDate

            val titleRefMatch = draftRef.isNotBlank() &&
                doc.referenceNumber.isNotBlank() &&
                doc.referenceNumber.equals(draftRef, ignoreCase = true) &&
                doc.title.equals(draftTitle, ignoreCase = true)

            refMatch || titleRefMatch
        }
    }

    fun runAiAutoFillOnCurrentDraft() {
        val current = _scanState.value
        if (current !is ScanFlowState.Review || current.isSaving || _isExtractingWithAi.value) return
        val draftBitmap = current.draft.bitmap

        viewModelScope.launch {
            _isExtractingWithAi.value = true
            try {
                val result = withContext(Dispatchers.Default) {
                    ocrService.analyzeDocumentPhoto(draftBitmap)
                }
                val latest = _scanState.value as? ScanFlowState.Review ?: return@launch
                if (latest.draft.bitmap !== draftBitmap) return@launch
                val latestDraft = latest.draft
                _scanState.value = latest.copy(
                    draft = latestDraft.copy(
                        title = result.title.ifBlank { latestDraft.title },
                        category = result.category,
                        documentDateIso = if (result.documentDateIso.isNotBlank()) result.documentDateIso else latestDraft.documentDateIso,
                        referenceNumber = result.referenceNumber.ifBlank { latestDraft.referenceNumber },
                        organization = result.organization.ifBlank { latestDraft.organization },
                        contractorOrParty = result.contractorOrParty.ifBlank { latestDraft.contractorOrParty },
                        summary = result.summary.ifBlank { latestDraft.summary },
                        extractedFieldsText = if (result.extractedFields.isNotEmpty()) result.extractedFields.joinToString("\n") else latestDraft.extractedFieldsText,
                        ocrFullText = result.ocrFullText.ifBlank { latestDraft.ocrFullText }
                    )
                )
                _events.emit(OneShotEvent.ShowMessage("AI auto-filled fields from document"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "AI Auto-Fill warning: ${e.message}")
                _events.emit(OneShotEvent.ShowMessage(e.message ?: "AI Auto-Fill unavailable. Please fill manually."))
            } finally {
                _isExtractingWithAi.value = false
            }
        }
    }

    fun dismissScanFlow() {
        _scanState.value = ScanFlowState.Idle
    }

    fun saveScannedDocument(context: Context) {
        val current = _scanState.value
        if (current !is ScanFlowState.Review || current.isSaving) return
        val draft = current.draft

        val duplicate = checkForDuplicate(draft)
        if (duplicate != null) {
            val refInfo = if (duplicate.referenceNumber.isNotBlank()) "#${duplicate.referenceNumber}" else duplicate.title
            val dateInfo = if (duplicate.documentDateIso.isNotBlank()) " from ${DateSearchParser.formatSlashDate(duplicate.documentDateIso)}" else ""
            _events.tryEmit(OneShotEvent.ShowMessage("Note: Similar document $refInfo$dateInfo already in vault."))
        }

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

                        // Asynchronously backup full-res photo and searchable PDF to Telegram relay
                        val photoFile = ImageStorageHelper.getLocalFile(context, imageFileName)
                        val pdfFile = ImageStorageHelper.getLocalFile(context, pdfFileName)
                        viewModelScope.launch {
                            MediaRelayService.backupDocument(docId, photoFile, pdfFile)
                        }

                        _scanState.value = ScanFlowState.Idle
                        _selectedDateLookupIso.value = newDoc.filedDateIso
                        _events.emit(OneShotEvent.ShowMessage("Saved & generated searchable PDF"))
                        _events.emit(OneShotEvent.OpenDocumentDetail(savedId))
                    },
                    onFailure = { err ->
                        val latest = _scanState.value
                        if (latest is ScanFlowState.Review) {
                            _scanState.value = latest.copy(isSaving = false)
                        }
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
                val latest = _scanState.value
                if (latest is ScanFlowState.Review) {
                    _scanState.value = latest.copy(isSaving = false)
                }
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
                    previewBase64 = updatedCopy.previewBase64,
                    docId = updatedCopy.id
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
                    val pdfFile = ImageStorageHelper.getLocalFile(context, pdfFileName)
                    viewModelScope.launch {
                        if (pdfFile != null) MediaRelayService.uploadMedia(existing.id, "pdf", pdfFile)
                    }
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
                    viewModelScope.launch {
                        MediaRelayService.deleteDocumentMedia(doc.id)
                    }
                    ImageStorageHelper.deleteLocalFiles(
                        context = context,
                        imageFileName = doc.localImageFileName,
                        pdfFileName = doc.localPdfFileName,
                        docId = doc.id
                    )
                    ThumbnailImageLoader.evict(
                        context = context,
                        cacheKey = ThumbnailImageLoader.buildCacheKey(
                            docId = doc.id,
                            localImageFileName = doc.localImageFileName,
                            previewBase64 = doc.previewBase64
                        )
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
                        previewBase64 = doc.previewBase64,
                        docId = doc.id
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

    fun checkForAppUpdates(currentVersionCode: Long, silent: Boolean = true) {
        if (_appUpdateState.value is AppUpdateState.Downloading) {
            if (!silent) {
                viewModelScope.launch {
                    _events.emit(OneShotEvent.ShowMessage("Download already in progress. Check notifications."))
                }
            }
            return
        }
        viewModelScope.launch {
            if (!silent) _appUpdateState.value = AppUpdateState.Checking
            val result = AppUpdateManager.checkForUpdate(currentVersionCode)
            result.fold(
                onSuccess = { info ->
                    if (info != null) {
                        _appUpdateState.value = AppUpdateState.Available(info)
                    } else {
                        _appUpdateState.value = AppUpdateState.Idle
                        if (!silent) {
                            _events.emit(OneShotEvent.ShowMessage("Apex Solar Work is up to date"))
                        }
                    }
                },
                onFailure = { error ->
                    _appUpdateState.value = AppUpdateState.Idle
                    if (!silent) {
                        _events.emit(OneShotEvent.ShowMessage("Update check: ${error.message}"))
                    }
                }
            )
        }
    }

    fun downloadAndInstallUpdate(context: Context, info: AppUpdateInfo) {
        val enqueueResult = AppUpdateManager.startBackgroundDownload(context, info)
        enqueueResult.fold(
            onSuccess = { downloadId ->
                _appUpdateState.value = AppUpdateState.Downloading(info, 0)
                viewModelScope.launch {
                    _events.emit(OneShotEvent.ShowMessage("Download started in background. Check notifications."))
                    var completed = false
                    var attempts = 0
                    val maxAttempts = 600 // 10 minutes timeout
                    val expectedApkName = "ApexSolarWork-v${info.versionName}.apk"
                    while (!completed && attempts < maxAttempts && isActive) {
                        attempts++
                        kotlinx.coroutines.delay(1000L)
                        val status = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            AppUpdateManager.queryDownloadStatus(context, downloadId, expectedApkName)
                        }
                        when (status) {
                            is com.example.util.DownloadStatus.Running -> {
                                if (_appUpdateState.value is AppUpdateState.Downloading) {
                                    _appUpdateState.value = AppUpdateState.Downloading(info, status.percent)
                                }
                            }
                            is com.example.util.DownloadStatus.Completed -> {
                                completed = true
                                val apkFile = status.file ?: File(
                                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                        ?: File(context.cacheDir, "updates"),
                                    "ApexSolarWork-v${info.versionName}.apk"
                                )
                                _appUpdateState.value = AppUpdateState.ReadyToInstall(apkFile)
                                val installResult = AppUpdateManager.installApk(context, apkFile)
                                installResult.onFailure { err ->
                                    _events.emit(OneShotEvent.ShowMessage("Install launch failed: ${err.message}"))
                                }
                            }
                            is com.example.util.DownloadStatus.Failed -> {
                                completed = true
                                _appUpdateState.value = AppUpdateState.Error(status.reason)
                                _events.emit(OneShotEvent.ShowMessage("Download failed: ${status.reason}"))
                            }
                        }
                    }
                    if (!completed && attempts >= maxAttempts) {
                        _appUpdateState.value = AppUpdateState.Error("Download timed out")
                        _events.emit(OneShotEvent.ShowMessage("Download timed out"))
                    }
                }
            },
            onFailure = { error ->
                // Fallback to in-app stream download if DownloadManager service is unavailable
                viewModelScope.launch {
                    _appUpdateState.value = AppUpdateState.Downloading(info, 0)
                    val result = AppUpdateManager.downloadApk(context, info) { percent ->
                        _appUpdateState.value = AppUpdateState.Downloading(info, percent)
                    }
                    result.fold(
                        onSuccess = { apkFile ->
                            _appUpdateState.value = AppUpdateState.ReadyToInstall(apkFile)
                            val installResult = AppUpdateManager.installApk(context, apkFile)
                            installResult.onFailure { err ->
                                _events.emit(OneShotEvent.ShowMessage("Install launch failed: ${err.message}"))
                            }
                        },
                        onFailure = { err ->
                            _appUpdateState.value = AppUpdateState.Error(err.message ?: "Download failed")
                            _events.emit(OneShotEvent.ShowMessage("Download failed: ${err.message}"))
                        }
                    )
                }
            }
        )
    }

    fun dismissUpdateDialog() {
        _appUpdateState.value = AppUpdateState.Idle
    }

    fun selectExpenseDate(dateIso: String) {
        _selectedExpenseDateIso.value = dateIso
    }

    fun addExpense(expense: SiteExpense) {
        viewModelScope.launch {
            try {
                siteExpenseRepository?.addExpense(expense)
                _events.emit(OneShotEvent.ShowMessage("Expense added"))
                syncDateToCloud(expense.dateIso)
            } catch (e: Exception) {
                _events.emit(OneShotEvent.ShowMessage("Failed to add expense: ${e.message}"))
            }
        }
    }

    fun deleteExpense(id: String, dateIso: String = "") {
        viewModelScope.launch {
            try {
                siteExpenseRepository?.deleteExpense(id)
                _events.emit(OneShotEvent.ShowMessage("Expense deleted"))
                if (dateIso.isNotBlank()) {
                    syncDateToCloud(dateIso)
                }
            } catch (e: Exception) {
                _events.emit(OneShotEvent.ShowMessage("Failed to delete expense: ${e.message}"))
            }
        }
    }

    fun addMaterial(record: SiteMaterialRecord) {
        viewModelScope.launch {
            try {
                siteExpenseRepository?.addMaterial(record)
                _events.emit(OneShotEvent.ShowMessage("Material record added"))
                syncDateToCloud(record.dateIso)
            } catch (e: Exception) {
                _events.emit(OneShotEvent.ShowMessage("Failed to add material: ${e.message}"))
            }
        }
    }

    fun deleteMaterial(id: String, dateIso: String = "") {
        viewModelScope.launch {
            try {
                siteExpenseRepository?.deleteMaterial(id)
                _events.emit(OneShotEvent.ShowMessage("Material record deleted"))
                if (dateIso.isNotBlank()) {
                    syncDateToCloud(dateIso)
                }
            } catch (e: Exception) {
                _events.emit(OneShotEvent.ShowMessage("Failed to delete material: ${e.message}"))
            }
        }
    }

    fun syncDateToCloud(dateIso: String) {
        if (dateIso.isBlank()) return
        viewModelScope.launch {
            val repo = siteExpenseRepository ?: return@launch
            try {
                val docId = "daily_expenses_${dateIso.replace("-", "_")}"
                val expenses = repo.getExpensesForDate(dateIso)
                val materials = repo.getMaterialsForDate(dateIso)

                if (expenses.isEmpty() && materials.isEmpty()) {
                    val existing = repository.getDocument(docId).getOrNull()
                    if (existing != null) {
                        repository.deleteDocument(docId)
                    }
                    return@launch
                }

                val header = SiteExpenseRepository.formatIsoDateHeader(dateIso)
                val totalAmt = expenses.sumOf { it.amount }.toInt()
                val title = "Daily Kharcha — $header".take(200)
                val summary = "Daily site expense sheet for $header. Total: ₹$totalAmt. ${expenses.size} expenses, ${materials.size} materials."
                    .take(1000)

                val extractedFields = mutableListOf(
                    "Date: $dateIso",
                    "Total: ₹$totalAmt"
                )
                expenses.take(25).forEach { exp ->
                    val qtyStr = if (exp.quantityNote.isNotBlank()) " (${exp.quantityNote})" else ""
                    val payerStr = if (exp.paidBy.isNotBlank()) " [${exp.paidBy}]" else ""
                    extractedFields.add("${exp.title}$qtyStr: ₹${exp.amount.toInt()}$payerStr".take(290))
                }

                val ocrText = buildString {
                    append(SiteExpenseRepository.EXPENSES_DELIMITER_START).append("\n")
                    append(SiteExpenseRepository.serializeExpensesToJson(expenses)).append("\n")
                    append(SiteExpenseRepository.MATERIALS_DELIMITER_START).append("\n")
                    append(SiteExpenseRepository.serializeMaterialsToJson(materials)).append("\n")
                    append(SiteExpenseRepository.DATA_DELIMITER_END).append("\n\n")
                    append(repo.formatWhatsAppReport(header, expenses, materials))
                }.take(50000)

                val existingDoc = repository.getDocument(docId).getOrNull()
                if (existingDoc != null) {
                    repository.updateDocumentMetadata(
                        docId = docId,
                        title = title,
                        category = DocumentCategory.SITE_REPORT,
                        documentDateIso = dateIso,
                        filedDateIso = dateIso,
                        referenceNumber = "EXP-${dateIso.replace("-", "")}",
                        organization = "Apex Solar Work",
                        contractorOrParty = "Aziz (Site Supervisor)",
                        summary = summary,
                        extractedFields = extractedFields,
                        ocrFullText = ocrText,
                        localPdfFileName = existingDoc.localPdfFileName
                    )
                } else {
                    val newDoc = ScannedDocument(
                        id = docId,
                        userId = "apex_team",
                        title = title,
                        category = DocumentCategory.SITE_REPORT.name,
                        documentDateIso = dateIso,
                        filedDateIso = dateIso,
                        referenceNumber = "EXP-${dateIso.replace("-", "")}",
                        organization = "Apex Solar Work",
                        contractorOrParty = "Aziz (Site Supervisor)",
                        summary = summary,
                        extractedFields = extractedFields,
                        ocrFullText = ocrText,
                        localImageFileName = "",
                        localPdfFileName = "",
                        previewBase64 = "",
                        pageCount = 1L,
                        starred = false
                    )
                    repository.createDocument(docId, newDoc)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync daily expenses to cloud for $dateIso: ${e.message}")
            }
        }
    }

    private suspend fun syncAllLocalDaysToCloud() {
        val repo = siteExpenseRepository ?: return
        try {
            val dates = repo.getAllExpenseDates()
            for (dateIso in dates) {
                val docId = "daily_expenses_${dateIso.replace("-", "_")}"
                val existing = repository.getDocument(docId).getOrNull()
                if (existing == null) {
                    syncDateToCloud(dateIso)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync all local days to cloud: ${e.message}")
        }
    }

    fun shareWhatsAppReport(
        context: Context,
        dateFormatted: String,
        expenses: List<SiteExpense>,
        materials: List<SiteMaterialRecord>
    ) {
        val repo = siteExpenseRepository ?: return
        val text = repo.formatWhatsAppReport(dateFormatted, expenses, materials)
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val genericIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(genericIntent, "Share Daily Site Report"))
            } catch (ex: Exception) {
                viewModelScope.launch {
                    _events.emit(OneShotEvent.ShowMessage("Could not open share sheet: ${ex.message}"))
                }
            }
        }
    }

    fun showMessage(message: String) {
        _events.tryEmit(OneShotEvent.ShowMessage(message))
    }

    fun saveSiteLocation(
        existingId: String?,
        name: String,
        siteType: String,
        address: String,
        locationInput: String,
        notes: String,
        isPinned: Boolean
    ) {
        val repo = siteLocationRepository ?: return
        if (_isSavingSiteLocation.value) return
        _isSavingSiteLocation.value = true

        viewModelScope.launch {
            try {
                val resolved = repo.resolveGoogleMapsLink(locationInput)
                val existing = if (!existingId.isNullOrBlank()) {
                    repo.getAllSites().firstOrNull { it.id == existingId }
                } else {
                    null
                }
                val keepExistingCoords = existing != null && (
                    locationInput.isBlank() ||
                        (resolved.googleMapsUrl.isNotBlank() &&
                            resolved.googleMapsUrl.equals(existing.googleMapsUrl, ignoreCase = true))
                    )
                val lat = resolved.latitude ?: if (keepExistingCoords) existing?.latitude else null
                val lng = resolved.longitude ?: if (keepExistingCoords) existing?.longitude else null
                val mapsUrl = resolved.googleMapsUrl.ifBlank {
                    existing?.googleMapsUrl?.takeIf { locationInput.isBlank() } ?: ""
                }

                val saved = repo.upsertSite(
                    SiteLocation(
                        id = existingId ?: "",
                        name = name,
                        siteType = siteType,
                        addressOrVillage = address,
                        latitude = lat,
                        longitude = lng,
                        googleMapsUrl = mapsUrl,
                        notes = notes,
                        isPinned = isPinned,
                        createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
                    )
                )
                val msg = if (saved.hasCoordinates) {
                    "Site saved (${saved.formattedCoordinates()})"
                } else {
                    "Site link saved"
                }
                _events.emit(OneShotEvent.ShowMessage(msg))
                viewModelScope.launch { syncAllSiteLocationsToCloud() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(OneShotEvent.ShowMessage("Failed to save site: ${e.message}"))
            } finally {
                _isSavingSiteLocation.value = false
            }
        }
    }

    fun togglePinSiteLocation(site: SiteLocation) {
        val repo = siteLocationRepository ?: return
        viewModelScope.launch {
            try {
                repo.upsertSite(site.copy(isPinned = !site.isPinned))
                syncAllSiteLocationsToCloud()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(OneShotEvent.ShowMessage("Failed to update pin: ${e.message}"))
            }
        }
    }

    fun deleteSiteLocation(site: SiteLocation) {
        val repo = siteLocationRepository ?: return
        viewModelScope.launch {
            try {
                repo.deleteSite(site.id)
                _events.emit(OneShotEvent.ShowMessage("Site location deleted"))
                syncAllSiteLocationsToCloud()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(OneShotEvent.ShowMessage("Failed to delete site: ${e.message}"))
            }
        }
    }

    private val siteCloudSyncMutex = Mutex()

    private suspend fun syncAllSiteLocationsToCloud(): Boolean = siteCloudSyncMutex.withLock {
        val repo = siteLocationRepository ?: return@withLock false
        try {
            val sites = repo.getAllSites()
            val docId = SiteLocationRepository.CLOUD_SYNC_DOC_ID
            val todayIso = DateSearchParser.todayIso()
            val title = "Apex Solar Site Locations & GPS (${sites.size})".take(200)
            val summary = "${sites.size} saved site GPS locations for material store & plants.".take(1000)
            val extractedFields = sites.take(28).map { s ->
                val gps = if (s.hasCoordinates) " [${s.formattedCoordinates()}]" else ""
                "${s.name}$gps — ${s.addressOrVillage}".take(290)
            }
            val ocrText = SiteLocationRepository.formatCloudSyncText(sites)
            val cloudResult = repository.getDocument(docId)
            val existingDoc = cloudResult.getOrNull()

            if (cloudResult.isFailure || existingDoc != null) {
                repository.updateDocumentMetadata(
                    docId = docId,
                    title = title,
                    category = DocumentCategory.SITE_REPORT,
                    documentDateIso = todayIso,
                    filedDateIso = todayIso,
                    referenceNumber = "SITE-LOCATIONS",
                    organization = "Apex Solar Work",
                    contractorOrParty = "Aziz (Site Supervisor)",
                    summary = summary,
                    extractedFields = extractedFields,
                    ocrFullText = ocrText,
                    localPdfFileName = existingDoc?.localPdfFileName ?: ""
                ).isSuccess
            } else {
                val newDoc = ScannedDocument(
                    id = docId,
                    userId = "apex_team",
                    title = title,
                    category = DocumentCategory.SITE_REPORT.name,
                    documentDateIso = todayIso,
                    filedDateIso = todayIso,
                    referenceNumber = "SITE-LOCATIONS",
                    organization = "Apex Solar Work",
                    contractorOrParty = "Aziz (Site Supervisor)",
                    summary = summary,
                    extractedFields = extractedFields,
                    ocrFullText = ocrText,
                    localImageFileName = "",
                    localPdfFileName = "",
                    previewBase64 = "",
                    pageCount = 1L,
                    starred = false
                )
                repository.createDocument(docId, newDoc).isSuccess
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync site locations to cloud: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "DocFolioVM"

        fun applyVaultFilter(
            docs: List<ScannedDocument>,
            filter: VaultFilterState
        ): List<ScannedDocument> {
            val (startIso, endIso) = filter.effectiveDateRange
            val rawQuery = filter.query.trim()
            val isPureDateOrRange = filter.isPureDateOrRangeQuery

            return docs.filter { doc ->
                val matchesCategory = filter.selectedCategory == null ||
                    doc.categoryEnum == filter.selectedCategory

                val matchesDate = if (startIso != null || endIso != null) {
                    matchesDocumentInDateRange(doc, startIso, endIso, filter.dateMatchMode)
                } else {
                    true
                }

                val matchesText = if (rawQuery.isBlank() || isPureDateOrRange) {
                    true
                } else {
                    DocumentRepository.matchesKeyword(doc, rawQuery.lowercase(Locale.US))
                }

                matchesCategory && matchesDate && matchesText
            }
        }

        fun matchesDocumentOnDate(
            doc: ScannedDocument,
            targetIso: String,
            mode: DateMatchMode
        ): Boolean = matchesDocumentInDateRange(doc, targetIso, targetIso, mode)

        fun matchesDocumentInDateRange(
            doc: ScannedDocument,
            startIso: String?,
            endIso: String?,
            mode: DateMatchMode
        ): Boolean {
            fun inRange(iso: String): Boolean {
                if (iso.isBlank()) return false
                val afterStart = startIso == null || iso >= startIso
                val beforeEnd = endIso == null || iso <= endIso
                return afterStart && beforeEnd
            }
            return when (mode) {
                DateMatchMode.EITHER -> inRange(doc.filedDateIso) || inRange(doc.documentDateIso)
                DateMatchMode.FILED_ONLY -> inRange(doc.filedDateIso)
                DateMatchMode.DOCUMENT_ONLY -> inRange(doc.documentDateIso)
            }
        }
    }
}

enum class PdfAction {
    OPEN,
    SHARE,
    SAVE
}
