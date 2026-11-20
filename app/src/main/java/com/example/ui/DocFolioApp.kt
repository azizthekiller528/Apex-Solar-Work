package com.example.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.DocumentRepository
import com.example.data.GeminiOcrService
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
    STARRED(R.string.nav_starred, Icons.Filled.Star, Icons.Outlined.StarBorder, "tab_starred")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocFolioAuthenticatedRoot(
    currentUser: FirebaseUser
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: DocFolioViewModel = viewModel(
        key = currentUser.uid,
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
                    ocrService = GeminiOcrService(),
                    currentUserId = currentUser.uid,
                    localCache = LocalDocumentCacheRepository(localDb.documentDao()),
                    docsDirPath = ImageStorageHelper.getDocsDirectory(app).absolutePath
                )
            }
        }
    )

    val documentsState by viewModel.documentsState.collectAsStateWithLifecycle()
    val filteredDocuments by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val selectedDateLookupIso by viewModel.selectedDateLookupIso.collectAsStateWithLifecycle()
    val dateLookupMode by viewModel.dateLookupMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val isExportingPdf by viewModel.isExportingPdf.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.VAULT) }
    var selectedDocId by remember { mutableStateOf<String?>(null) }
    var showScanSourceSheet by remember { mutableStateOf(false) }
    var showCameraView by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

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

    // Sub-screen 1: Active Scan / OCR Verification Flow
    if (scanState !is ScanFlowState.Idle) {
        ScanReviewScreen(
            scanState = scanState,
            onUpdateDraft = viewModel::updateScanDraft,
            onRetryOcr = viewModel::retryOcrOnLastBitmap,
            onProceedManual = viewModel::proceedManualFromError,
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

    // Primary Adaptive Layout (NavigationBar on Compact, NavigationRail on Medium/Expanded)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        val userInitial = (currentUser.displayName ?: currentUser.email ?: "U")
            .firstOrNull()?.uppercaseChar()?.toString() ?: "U"

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when (currentTab) {
                                    MainTab.VAULT -> "OCR Searchable PDF Archive"
                                    MainTab.BY_DATE -> "Boss Date Register"
                                    MainTab.STARRED -> "Priority Documents"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showAccountMenu = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("account_menu_button")
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = androidx.compose.foundation.shape.CircleShape,
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
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = currentUser.displayName ?: "Signed in",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            if (!currentUser.email.isNullOrBlank()) {
                                                Text(
                                                    text = currentUser.email ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = { showAccountMenu = false },
                                    enabled = false
                                )
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!useNavigationRail) {
                    ExtendedFloatingActionButton(
                        onClick = { showScanSourceSheet = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null
                            )
                        },
                        text = { Text(stringResource(R.string.action_scan_document)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("scan_doc_fab")
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            FloatingActionButton(
                                onClick = { showScanSourceSheet = true },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .testTag("scan_doc_rail_fab")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = stringResource(R.string.action_scan_document)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxHeight()
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
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        MainTab.VAULT -> VaultScreen(
                            documentsState = documentsState,
                            filteredDocuments = filteredDocuments,
                            filterState = filterState,
                            starredOnly = false,
                            isExportingPdf = isExportingPdf,
                            onQueryChange = viewModel::updateSearchQuery,
                            onToggleAlternateDate = viewModel::toggleAlternateDateInterpretation,
                            onSelectExplicitDate = viewModel::setExplicitDateFilter,
                            onSelectRangeStart = viewModel::setDateRangeStart,
                            onSelectRangeEnd = viewModel::setDateRangeEnd,
                            onClearDateRange = viewModel::clearDateRange,
                            onClearSearchAndDate = viewModel::clearSearchAndDateFilters,
                            onSelectCategory = viewModel::setCategoryFilter,
                            onSelectDateMatchMode = viewModel::setVaultDateMatchMode,
                            onClearFilters = viewModel::clearVaultFilters,
                            onOpenDocument = { doc -> selectedDocId = doc.id },
                            onToggleStar = viewModel::toggleStar,
                            onSharePdf = { doc ->
                                viewModel.prepareSingleDocumentPdf(context, doc, PdfAction.SHARE)
                            },
                            onExportDayPdf = { dateIso, docs ->
                                viewModel.exportDailyBundlePdf(context, dateIso, docs)
                            },
                            onRequestScan = { showScanSourceSheet = true }
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
                            }
                        )

                        MainTab.STARRED -> VaultScreen(
                            documentsState = documentsState,
                            filteredDocuments = filteredDocuments,
                            filterState = filterState,
                            starredOnly = true,
                            isExportingPdf = isExportingPdf,
                            onQueryChange = viewModel::updateSearchQuery,
                            onToggleAlternateDate = viewModel::toggleAlternateDateInterpretation,
                            onSelectExplicitDate = viewModel::setExplicitDateFilter,
                            onSelectRangeStart = viewModel::setDateRangeStart,
                            onSelectRangeEnd = viewModel::setDateRangeEnd,
                            onClearDateRange = viewModel::clearDateRange,
                            onClearSearchAndDate = viewModel::clearSearchAndDateFilters,
                            onSelectCategory = viewModel::setCategoryFilter,
                            onSelectDateMatchMode = viewModel::setVaultDateMatchMode,
                            onClearFilters = viewModel::clearVaultFilters,
                            onOpenDocument = { doc -> selectedDocId = doc.id },
                            onToggleStar = viewModel::toggleStar,
                            onSharePdf = { doc ->
                                viewModel.prepareSingleDocumentPdf(context, doc, PdfAction.SHARE)
                            },
                            onExportDayPdf = { dateIso, docs ->
                                viewModel.exportDailyBundlePdf(context, dateIso, docs)
                            },
                            onRequestScan = { showScanSourceSheet = true }
                        )
                    }
                }
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
}
