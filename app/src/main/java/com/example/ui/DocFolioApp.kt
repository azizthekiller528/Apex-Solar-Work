package com.example.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.BuildConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.data.MediaRelayService
import com.example.data.SiteExpenseRepository
import com.example.data.SiteLocationRepository
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.DarkSecondary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidDockTabItem
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidGlassOrbButton
import com.example.ui.theme.LiquidGlassTabDock
import com.example.ui.theme.LiquidGlassTone
import com.example.ui.theme.LocalHudLiquidBackdrop
import com.example.ui.theme.drawLiquidAmbientBackground
import com.example.ui.theme.liquidCapsuleShape
import com.example.ui.theme.liquidGlassSurface
import com.example.ui.theme.liquidSquircleShape
import com.example.ui.theme.opticalLiquidGlass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import com.example.util.AppUpdateManager
import com.example.viewmodel.AppUpdateState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.DocumentRepository
import com.example.data.local.DocFolioDatabase
import com.example.data.local.LocalDocumentCacheRepository
import com.example.firebase.signOut
import com.example.model.UiState
import com.example.ui.components.ScanSourceBottomSheet
import com.example.util.ImageStorageHelper
import com.example.util.SearchablePdfGenerator
import com.example.viewmodel.DocFolioViewModel
import com.example.viewmodel.OneShotEvent
import com.example.viewmodel.PdfAction
import com.example.viewmodel.ScanFlowState
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    VAULT(R.string.nav_vault, Icons.Filled.Folder, Icons.Outlined.Folder, "tab_vault"),
    BY_DATE(R.string.nav_by_date, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "tab_by_date"),
    EXPENSES(R.string.nav_expenses, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "tab_expenses"),
    SITES(R.string.nav_sites, Icons.Filled.LocationOn, Icons.Outlined.LocationOn, "tab_sites"),
    STARRED(R.string.nav_starred, Icons.Filled.Star, Icons.Outlined.StarBorder, "tab_starred")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocFolioApp(
    currentUser: FirebaseUser? = null,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isDarkTheme: Boolean = false,
    onThemeModeChange: (AppThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val effectiveUid = currentUser?.uid ?: "apex_team"
    val viewModel: DocFolioViewModel = viewModel(
        key = effectiveUid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val localDb = DocFolioDatabase.getInstance(app)
                DocFolioViewModel(
                    repository = DocumentRepository(db),
                    currentUserId = effectiveUid,
                    localCache = LocalDocumentCacheRepository(localDb.documentDao()),
                    docsDirPath = ImageStorageHelper.getDocsDirectory(app).absolutePath,
                    siteExpenseRepository = SiteExpenseRepository(localDb.siteExpenseDao()),
                    siteLocationRepository = SiteLocationRepository(localDb.siteLocationDao(), app)
                )
            }
        }
    )

    val documentsState by viewModel.documentsState.collectAsStateWithLifecycle()
    val filteredDocuments by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val firestoreSearchState by viewModel.firestoreSearchState.collectAsStateWithLifecycle()
    val selectedDateLookupIso by viewModel.selectedDateLookupIso.collectAsStateWithLifecycle()
    val dateLookupMode by viewModel.dateLookupMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val isExportingPdf by viewModel.isExportingPdf.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val allExpenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val allMaterials by viewModel.allMaterials.collectAsStateWithLifecycle()
    val selectedExpenseDateIso by viewModel.selectedExpenseDateIso.collectAsStateWithLifecycle()
    val allSiteLocations by viewModel.allSiteLocations.collectAsStateWithLifecycle()
    val isSavingSiteLocation by viewModel.isSavingSiteLocation.collectAsStateWithLifecycle()

    val currentAppVersionCode = remember(context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (_: Exception) {
            com.example.BuildConfig.VERSION_CODE.toLong()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForAppUpdates(currentVersionCode = currentAppVersionCode, silent = true)
        com.example.util.DeviceTelemetryManager.recordHeartbeatAsync(context, this)
    }

    LaunchedEffect(effectiveUid) {
        withContext(Dispatchers.IO) {
            val localDb = DocFolioDatabase.getInstance(context)
            val repo = LocalDocumentCacheRepository(localDb.documentDao())
            repo.seedFromAssetsIfEmpty(context, effectiveUid)
            repo.syncUpdatedSeedPhotos(context)
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.VAULT) }
    var selectedDocId by rememberSaveable { mutableStateOf<String?>(null) }
    var showScanSourceSheet by remember { mutableStateOf(false) }
    var showCameraView by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var isBulkExporting by remember { mutableStateOf(false) }
    var isCloudSyncing by remember { mutableStateOf(false) }

    var pendingSavePdfSource by remember { mutableStateOf<File?>(null) }

    // Zero-permission Android Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.startScanFromUri(context, uri)
        }
    }

    // Zero-permission SAF PDF export launcher
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val sourceFile = pendingSavePdfSource
        pendingSavePdfSource = null
        if (destUri != null && sourceFile != null && sourceFile.exists()) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            sourceFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        } ?: throw IllegalStateException("Could not open destination file.")
                    }
                    snackbarHostState.showSnackbar("Searchable PDF saved to device")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Failed to save PDF")
                }
            }
        }
    }

    // One-shot ViewModel event collector
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OneShotEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.text)
                }

                is OneShotEvent.SharePdf -> {
                    try {
                        val shareIntent = SearchablePdfGenerator.buildSharePdfIntent(
                            context = context,
                            pdfFile = event.file,
                            subject = event.subject
                        )
                        context.startActivity(shareIntent)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("No app available to share PDF")
                    }
                }

                is OneShotEvent.OpenPdf -> {
                    try {
                        val viewIntent = SearchablePdfGenerator.buildViewPdfIntent(context, event.file)
                        context.startActivity(viewIntent)
                    } catch (_: ActivityNotFoundException) {
                        snackbarHostState.showSnackbar("No PDF viewer installed — opening Share sheet instead")
                        try {
                            context.startActivity(
                                SearchablePdfGenerator.buildSharePdfIntent(
                                    context,
                                    event.file,
                                    "DocFolio Searchable PDF"
                                )
                            )
                        } catch (_: Exception) {
                            // Ignore if no handlers exist on stripped emulator
                        }
                    }
                }

                is OneShotEvent.SavePdfWithSaf -> {
                    pendingSavePdfSource = event.sourceFile
                    savePdfLauncher.launch(event.suggestedName)
                }

                is OneShotEvent.OpenDocumentDetail -> {
                    selectedDocId = event.docId
                }
            }
        }
    }

    val allDocs = (documentsState as? UiState.Success)?.data ?: emptyList()
    val selectedDoc = remember(allDocs, selectedDocId) {
        selectedDocId?.let { id -> allDocs.firstOrNull { it.id == id } }
    }

    val isExtractingWithAi by viewModel.isExtractingWithAi.collectAsStateWithLifecycle()

    // Sub-screen 1: Active Scan / OCR Verification Flow
    if (scanState !is ScanFlowState.Idle) {
        ScanReviewScreen(
            scanState = scanState,
            isExtractingWithAi = isExtractingWithAi,
            onAutoFillWithAi = viewModel::runAiAutoFillOnCurrentDraft,
            onUpdateDraft = viewModel::updateScanDraft,
            onSaveDocument = { viewModel.saveScannedDocument(context) },
            onCancel = viewModel::dismissScanFlow
        )
        return
    }

    // Sub-screen 1b: Live CameraX Document Scanner (with Accompanist Permissions)
    if (showCameraView) {
        CameraView(
            onPhotoCaptured = { uri ->
                showCameraView = false
                viewModel.startScanFromUri(context, uri)
            },
            onPickFromGallery = {
                showCameraView = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClose = { showCameraView = false }
        )
        return
    }

    // Sub-screen 2: Document Detail & Searchable PDF Inspector
    if (selectedDoc != null) {
        DocumentDetailScreen(
            doc = selectedDoc,
            isExportingPdf = isExportingPdf,
            onBack = { selectedDocId = null },
            onToggleStar = { viewModel.toggleStar(selectedDoc) },
            onPdfAction = { action ->
                viewModel.prepareSingleDocumentPdf(context, selectedDoc, action)
            },
            onSaveEdits = { title, cat, docDate, filedDate, ref, org, party, summary, fields, ocr ->
                viewModel.updateExistingDocument(
                    context = context,
                    existing = selectedDoc,
                    title = title,
                    category = cat,
                    documentDateIso = docDate,
                    filedDateIso = filedDate,
                    referenceNumber = ref,
                    organization = org,
                    contractorOrParty = party,
                    summary = summary,
                    extractedFields = fields,
                    ocrFullText = ocr
                )
            },
            onDelete = {
                viewModel.deleteDocument(context, selectedDoc) {
                    selectedDocId = null
                }
            },
            onCopyFeedback = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
        return
    }

    // Primary Adaptive Layout: Edge-to-Edge Live Backdrop Layer + Floating Optical Liquid Glass HUD
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        val userInitial = (currentUser?.displayName ?: currentUser?.email ?: "Apex")
            .firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        val density = LocalDensity.current
        val currentIsDark by rememberUpdatedState(isDarkTheme)
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        var topBarClearance by remember(statusTop) { mutableStateOf(statusTop + 76.dp) }
        var bottomBarClearance by remember(useNavigationRail, navBottom) {
            mutableStateOf(if (useNavigationRail) navBottom + 16.dp else navBottom + 86.dp)
        }

        val dockTabs = MainTab.entries.map { tab ->
            LiquidDockTabItem(
                id = tab.name,
                label = stringResource(tab.labelRes),
                selectedIcon = tab.selectedIcon,
                unselectedIcon = tab.unselectedIcon,
                testTag = tab.testTag
            )
        }

        val contentBackdrop = rememberLayerBackdrop {
            drawLiquidAmbientBackground(isDark = currentIsDark)
            drawContent()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // LAYER 1 (CAPTURED BY BACKDROP): Full-screen edge-to-edge scrolling body
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
            ) {
                CompositionLocalProvider(LocalHudLiquidBackdrop provides null) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useNavigationRail) {
                            Spacer(modifier = Modifier.width(102.dp))
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                MainTab.VAULT -> VaultScreen(
                                    documentsState = documentsState,
                                    filteredDocuments = filteredDocuments,
                                    filterState = filterState,
                                    firestoreSearchState = firestoreSearchState,
                                    starredOnly = false,
                                    isExportingPdf = isExportingPdf,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onToggleAlternateDate = viewModel::toggleAlternateDateInterpretation,
                                    onSelectExplicitDate = viewModel::setExplicitDateFilter,
                                    onSelectDateRange = viewModel::setDateRange,
                                    onSelectRangeStart = viewModel::setDateRangeStart,
                                    onSelectRangeEnd = viewModel::setDateRangeEnd,
                                    onClearDateRange = viewModel::clearDateRange,
                                    onClearSearchAndDate = viewModel::clearSearchAndDateFilters,
                                    onSelectCategory = viewModel::setCategoryFilter,
                                    onSelectDateMatchMode = viewModel::setVaultDateMatchMode,
                                    onRetryCloudSearch = viewModel::retryFirestoreSearch,
                                    onClearFilters = viewModel::clearVaultFilters,
                                    onOpenDocument = { doc -> selectedDocId = doc.id },
                                    onToggleStar = viewModel::toggleStar,
                                    onSharePdf = { doc ->
                                        viewModel.prepareSingleDocumentPdf(context, doc, PdfAction.SHARE)
                                    },
                                    onExportDayPdf = { dateIso, docs ->
                                        viewModel.exportDailyBundlePdf(context, dateIso, docs)
                                    },
                                    onRequestScan = { showScanSourceSheet = true },
                                    topContentPadding = topBarClearance + 4.dp,
                                    bottomContentPadding = bottomBarClearance + 14.dp
                                )

                                MainTab.BY_DATE -> DateLookupScreen(
                                    allDocuments = allDocs,
                                    selectedDateIso = selectedDateLookupIso,
                                    dateMatchMode = dateLookupMode,
                                    isExportingPdf = isExportingPdf,
                                    onSelectDate = viewModel::selectLookupDate,
                                    onSelectMatchMode = viewModel::setDateLookupMode,
                                    onOpenDocument = { doc -> selectedDocId = doc.id },
                                    onToggleStar = viewModel::toggleStar,
                                    onSharePdf = { doc ->
                                        viewModel.prepareSingleDocumentPdf(context, doc, PdfAction.SHARE)
                                    },
                                    onExportDayPdf = { dateIso, docs ->
                                        viewModel.exportDailyBundlePdf(context, dateIso, docs)
                                    },
                                    topContentPadding = topBarClearance + 4.dp,
                                    bottomContentPadding = bottomBarClearance + 14.dp
                                )

                                MainTab.EXPENSES -> ExpensesScreen(
                                    allExpenses = allExpenses,
                                    allMaterials = allMaterials,
                                    selectedDateIso = selectedExpenseDateIso,
                                    onSelectDate = viewModel::selectExpenseDate,
                                    onAddExpense = viewModel::addExpense,
                                    onUpdateExpense = viewModel::updateExpense,
                                    onDeleteExpense = { id, dateIso -> viewModel.deleteExpense(id, dateIso) },
                                    onAddMaterial = viewModel::addMaterial,
                                    onUpdateMaterial = viewModel::updateMaterial,
                                    onDeleteMaterial = { id, dateIso -> viewModel.deleteMaterial(id, dateIso) },
                                    onShareReport = viewModel::shareWhatsAppReport,
                                    topContentPadding = topBarClearance + 4.dp,
                                    bottomContentPadding = bottomBarClearance + 14.dp
                                )

                                MainTab.SITES -> SitesScreen(
                                    sites = allSiteLocations,
                                    isSaving = isSavingSiteLocation,
                                    onSaveSite = viewModel::saveSiteLocation,
                                    onTogglePin = viewModel::togglePinSiteLocation,
                                    onDeleteSite = viewModel::deleteSiteLocation,
                                    onFeedback = viewModel::showMessage,
                                    topContentPadding = topBarClearance + 4.dp,
                                    bottomContentPadding = bottomBarClearance + 14.dp
                                )

                                MainTab.STARRED -> VaultScreen(
                                    documentsState = documentsState,
                                    filteredDocuments = filteredDocuments,
                                    filterState = filterState,
                                    firestoreSearchState = firestoreSearchState,
                                    starredOnly = true,
                                    isExportingPdf = isExportingPdf,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onToggleAlternateDate = viewModel::toggleAlternateDateInterpretation,
                                    onSelectExplicitDate = viewModel::setExplicitDateFilter,
                                    onSelectDateRange = viewModel::setDateRange,
                                    onSelectRangeStart = viewModel::setDateRangeStart,
                                    onSelectRangeEnd = viewModel::setDateRangeEnd,
                                    onClearDateRange = viewModel::clearDateRange,
                                    onClearSearchAndDate = viewModel::clearSearchAndDateFilters,
                                    onSelectCategory = viewModel::setCategoryFilter,
                                    onSelectDateMatchMode = viewModel::setVaultDateMatchMode,
                                    onRetryCloudSearch = viewModel::retryFirestoreSearch,
                                    onClearFilters = viewModel::clearVaultFilters,
                                    onOpenDocument = { doc -> selectedDocId = doc.id },
                                    onToggleStar = viewModel::toggleStar,
                                    onSharePdf = { doc ->
                                        viewModel.prepareSingleDocumentPdf(context, doc, PdfAction.SHARE)
                                    },
                                    onExportDayPdf = { dateIso, docs ->
                                        viewModel.exportDailyBundlePdf(context, dateIso, docs)
                                    },
                                    onRequestScan = { showScanSourceSheet = true },
                                    topContentPadding = topBarClearance + 4.dp,
                                    bottomContentPadding = bottomBarClearance + 14.dp
                                )
                            }
                        }
                    }
                }
            }

            // LAYER 2 (FLOATING OPTICAL LIQUID GLASS CHROME — OUTSIDE layerBackdrop, NEVER CYCLIC)
            CompositionLocalProvider(LocalHudLiquidBackdrop provides contentBackdrop) {
                // Floating Optical Liquid Glass Top Header Bar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            val measured = with(density) { size.height.toDp() }
                            if (measured > 0.dp && measured != topBarClearance) {
                                topBarClearance = measured
                            }
                        }
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = liquidCapsuleShape(),
                        tone = LiquidGlassTone.DOCK,
                        elevation = 10.dp,
                        backdrop = contentBackdrop
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .liquidGlassSurface(
                                                shape = CircleShape,
                                                tone = LiquidGlassTone.SUBTLE
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "v${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    text = when (currentTab) {
                                        MainTab.VAULT -> "OCR Searchable PDF Archive"
                                        MainTab.BY_DATE -> "Boss Date Register"
                                        MainTab.EXPENSES -> "Daily Kharcha & Site Stock"
                                        MainTab.SITES -> "Site & Store GPS Locations"
                                        MainTab.STARRED -> "Priority Documents"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                IconButton(
                                    onClick = { onThemeModeChange(AppThemeMode.nextManualToggle(isDarkTheme)) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("day_night_toggle_button")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .liquidGlassSurface(
                                                shape = CircleShape,
                                                tone = LiquidGlassTone.SUBTLE
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                            contentDescription = if (isDarkTheme) "Switch to Day Mode" else "Switch to Night Mode",
                                            tint = if (isDarkTheme) DarkSecondary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Box {
                                    IconButton(
                                        onClick = { showAccountMenu = true },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("account_menu_button")
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape,
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                            ),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = userInitial,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showAccountMenu,
                                        onDismissRequest = { showAccountMenu = false },
                                        shape = RoundedCornerShape(20.dp),
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = currentUser?.displayName ?: "Apex Solar Vault",
                                                            style = MaterialTheme.typography.titleSmall
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            shape = CircleShape
                                                        ) {
                                                            Text(
                                                                text = "v${BuildConfig.VERSION_NAME}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Build ${BuildConfig.VERSION_CODE} • Company Cloud Archive",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = { showAccountMenu = false },
                                            enabled = false
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isBulkExporting) "Exporting to Phone..." else "Save All Slips to Downloads") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showAccountMenu = false
                                                if (!isBulkExporting) {
                                                    scope.launch {
                                                        isBulkExporting = true
                                                        snackbarHostState.showSnackbar("Exporting all slips to Downloads/ApexSolar...")
                                                        val docs = (documentsState as? UiState.Success)?.data ?: emptyList()
                                                        val (photos, pdfs) = ImageStorageHelper.exportAllDocumentsToPhone(context, docs)
                                                        isBulkExporting = false
                                                        snackbarHostState.showSnackbar("Saved $photos photos to Gallery & $pdfs PDFs to Downloads!")
                                                    }
                                                }
                                            },
                                            enabled = !isBulkExporting
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isCloudSyncing) "Syncing from Cloud..." else "Sync Photos from Telegram Cloud") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.CloudDownload,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showAccountMenu = false
                                                if (!isCloudSyncing) {
                                                    scope.launch {
                                                        isCloudSyncing = true
                                                        snackbarHostState.showSnackbar("Connecting to Telegram relay to sync photos...")
                                                        val docs = (documentsState as? UiState.Success)?.data ?: emptyList()
                                                        val downloaded = MediaRelayService.syncAllMissingPhotos(context, docs)
                                                        isCloudSyncing = false
                                                        if (downloaded > 0) {
                                                            snackbarHostState.showSnackbar("Downloaded $downloaded missing photos from Telegram cloud!")
                                                        } else {
                                                            snackbarHostState.showSnackbar("All photos are already downloaded on phone!")
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isCloudSyncing
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Theme: ${themeMode.label}") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = when (themeMode) {
                                                        AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                                        AppThemeMode.LIGHT -> Icons.Filled.LightMode
                                                        AppThemeMode.DARK -> Icons.Filled.DarkMode
                                                    },
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                onThemeModeChange(AppThemeMode.nextCycle(themeMode))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Check for Updates (v${BuildConfig.VERSION_NAME})") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.SystemUpdate,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showAccountMenu = false
                                                viewModel.checkForAppUpdates(currentVersionCode = currentAppVersionCode, silent = false)
                                            }
                                        )
                                        if (currentUser != null) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.sign_out)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showAccountMenu = false
                                                    showSignOutDialog = true
                                                },
                                                modifier = Modifier.testTag("sign_out_menu_item")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tablet Floating Optical NavigationRail
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = Color.Transparent,
                        header = {
                            LiquidGlassOrbButton(
                                onClick = { showScanSourceSheet = true },
                                icon = Icons.Default.DocumentScanner,
                                contentDescription = stringResource(R.string.action_scan_document),
                                backdrop = contentBackdrop,
                                tone = LiquidGlassTone.ACCENT,
                                size = 56.dp,
                                isDark = isDarkTheme,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .testTag("scan_doc_rail_fab")
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 10.dp, top = topBarClearance + 4.dp, bottom = 10.dp)
                            .navigationBarsPadding()
                            .opticalLiquidGlass(
                                backdrop = contentBackdrop,
                                shape = { liquidSquircleShape(28.dp) },
                                tone = LiquidGlassTone.DOCK,
                                isDark = isDarkTheme,
                                blurRadius = 8.dp,
                                refractionHeight = 16.dp,
                                refractionAmount = 26.dp,
                                hasShadow = true
                            )
                            .fillMaxHeight()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MainTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }

                // Phone Floating Optical 5-Tab Pill Dock + Detached Scan Orb (Zero card overlap!)
                if (!useNavigationRail) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                val measured = with(density) { size.height.toDp() }
                                if (measured > 0.dp && measured != bottomBarClearance) {
                                    bottomBarClearance = measured
                                }
                            }
                            .navigationBarsPadding()
                            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidGlassTabDock(
                                tabs = dockTabs,
                                selectedIndex = MainTab.entries.indexOf(currentTab).coerceAtLeast(0),
                                onSelectIndex = { idx ->
                                    MainTab.entries.getOrNull(idx)?.let { currentTab = it }
                                },
                                backdrop = contentBackdrop,
                                isDark = isDarkTheme,
                                modifier = Modifier.weight(1f)
                            )

                            if (currentTab != MainTab.EXPENSES && currentTab != MainTab.SITES) {
                                LiquidGlassOrbButton(
                                    onClick = { showScanSourceSheet = true },
                                    icon = Icons.Default.DocumentScanner,
                                    contentDescription = stringResource(R.string.action_scan_document),
                                    backdrop = contentBackdrop,
                                    tone = LiquidGlassTone.ACCENT,
                                    size = 64.dp,
                                    isDark = isDarkTheme,
                                    modifier = Modifier.testTag("scan_doc_fab")
                                )
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomBarClearance + 4.dp)
                )
            }
        }
    }

    if (showScanSourceSheet) {
        ScanSourceBottomSheet(
            onDismiss = { showScanSourceSheet = false },
            onTakePhoto = {
                showScanSourceSheet = false
                showCameraView = true
            },
            onPickFromGallery = {
                showScanSourceSheet = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickSampleForm = { assetPath ->
                showScanSourceSheet = false
                viewModel.startScanFromBundledAsset(context, assetPath)
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out of DocFolio?") },
            text = { Text("Your indexed documents remain safely backed up in the cloud for your next sign-in.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        signOut(credentialManager = credentialManager, scope = scope)
                    },
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    when (val state = appUpdateState) {
        is AppUpdateState.Available -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdateDialog,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Update Available • v${state.info.versionName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A new version of Apex Solar Work is ready to install.")
                        if (state.info.releaseNotes.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.info.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.downloadAndInstallUpdate(context, state.info) }) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissUpdateDialog) {
                        Text("Later")
                    }
                }
            )
        }

        is AppUpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdateDialog,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Downloading Update...") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${state.percent}%", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Downloading in background via Android system. You can minimize the app safely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissUpdateDialog) {
                        Text("Hide")
                    }
                }
            )
        }

        is AppUpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdateDialog,
                title = { Text("Download Complete") },
                text = { Text("Tap Install to complete updating to the latest version.") },
                confirmButton = {
                    Button(onClick = {
                        AppUpdateManager.installApk(context, state.apkFile).onFailure { err ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Install launch failed: ${err.message}")
                            }
                        }
                    }) {
                        Text("Install")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissUpdateDialog) {
                        Text("Dismiss")
                    }
                }
            )
        }

        else -> Unit
    }
}
