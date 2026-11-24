package com.example.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DocDatePickerDialog
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoTranscriptStyle
import com.example.ui.theme.OnSafetyAmberContainer
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.VerifiedTeal
import com.example.util.DateSearchParser
import com.example.util.ImageStorageHelper
import com.example.viewmodel.PdfAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    doc: ScannedDocument,
    isExportingPdf: Boolean,
    onBack: () -> Unit,
    onToggleStar: () -> Unit,
    onPdfAction: (PdfAction) -> Unit,
    onSaveEdits: (
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
    ) -> Unit,
    onDelete: () -> Unit,
    onCopyFeedback: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var scanBitmap by remember(doc.id, doc.localImageFileName, doc.previewBase64) {
        mutableStateOf<Bitmap?>(null)
    }
    var isRotating by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var ocrSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(doc.id, doc.localImageFileName, doc.previewBase64) {
        scanBitmap = ImageStorageHelper.loadDocumentBitmap(
            context = context,
            localImageFileName = doc.localImageFileName,
            previewBase64 = doc.previewBase64,
            docId = doc.id
        )
    }

    val (highlightedTranscript, matchCount) = remember(doc.ocrFullText, ocrSearchQuery) {
        buildHighlightedText(
            source = doc.ocrFullText.ifBlank { "(No OCR transcript stored)" },
            needle = ocrSearchQuery.trim(),
            highlightBg = SafetyAmberContainer,
            highlightFg = OnSafetyAmberContainer
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to vault"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_star_button")
                    ) {
                        Icon(
                            imageVector = if (doc.starred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (doc.starred) "Unstar document" else "Star document",
                            tint = if (doc.starred) SafetyAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showEditSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit metadata and OCR"
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete document",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Scanned Image Preview Card (tap to zoom)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = scanBitmap != null) { showZoomDialog = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val bmp = scanBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Scanned image of ${doc.title}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .clickable(enabled = !isRotating) {
                                        if (!isRotating) {
                                            isRotating = true
                                            scope.launch {
                                                try {
                                                    val rotated = ImageStorageHelper.rotateImageFile90(
                                                        context = context,
                                                        localImageFileName = doc.localImageFileName.ifBlank { "${doc.id}.jpg" },
                                                        currentBitmap = scanBitmap,
                                                        docId = doc.id
                                                    )
                                                    if (rotated != null) {
                                                        scanBitmap = rotated
                                                        onCopyFeedback("Photo rotated 90°")
                                                    }
                                                } finally {
                                                    isRotating = false
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                        contentDescription = "Rotate 90°",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Rotate 90°",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Zoom scan",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Inspect",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Searchable OCR record ready",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Searchable PDF Actions Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onPdfAction(PdfAction.OPEN) },
                        enabled = !isExportingPdf,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_open_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.open_pdf), maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = { onPdfAction(PdfAction.SHARE) },
                        enabled = !isExportingPdf,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_share_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.share_pdf), maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { onPdfAction(PdfAction.SAVE) },
                        enabled = !isExportingPdf,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_save_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.save_pdf), maxLines = 1)
                    }
                }

                // Save Photo to Phone Gallery Button
                var isSavingPhoto by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        val bmp = scanBitmap
                        if (bmp != null && !isSavingPhoto) {
                            isSavingPhoto = true
                            scope.launch {
                                try {
                                    val savedUri = withContext(Dispatchers.IO) {
                                        ImageStorageHelper.saveBitmapToGallery(
                                            context = context,
                                            bitmap = bmp,
                                            title = doc.title.ifBlank { doc.id }
                                        )
                                    }
                                    if (savedUri != null) {
                                        onCopyFeedback("Photo saved to Gallery (Pictures/ApexSolar)")
                                    } else {
                                        onCopyFeedback("Failed to save photo to Gallery")
                                    }
                                } finally {
                                    isSavingPhoto = false
                                }
                            }
                        } else if (bmp == null) {
                            onCopyFeedback("Photo loading or downloading from cloud...")
                        }
                    },
                    enabled = scanBitmap != null && !isSavingPhoto,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_save_photo_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Full-Resolution Photo to Gallery", maxLines = 1)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CategoryBadge(category = doc.categoryEnum)
                            if (doc.referenceNumber.isNotBlank()) {
                                Text(
                                    text = "REF #${doc.referenceNumber}",
                                    style = MonoCodeStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val effectiveDateIso = doc.documentDateIso.ifBlank { doc.filedDateIso }
                        val hasDiffFiled = doc.filedDateIso.isNotBlank() &&
                                doc.documentDateIso.isNotBlank() &&
                                doc.filedDateIso != doc.documentDateIso

                        DateInfoBox(
                            label = "DOCUMENT DATE",
                            primary = if (effectiveDateIso.isNotBlank()) {
                                DateSearchParser.formatSlashDate(effectiveDateIso)
                            } else {
                                "Blank on form"
                            },
                            secondary = if (effectiveDateIso.isNotBlank()) {
                                val friendly = DateSearchParser.formatFriendlyDate(effectiveDateIso)
                                if (hasDiffFiled) "$friendly • Filed: ${DateSearchParser.formatSlashDate(doc.filedDateIso)}" else friendly
                            } else {
                                "Tap Edit to set"
                            },
                            accent = VerifiedTeal,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (doc.organization.isNotBlank() || doc.contractorOrParty.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            if (doc.organization.isNotBlank()) {
                                KeyValueRow("Company / Project", doc.organization)
                            }
                            if (doc.contractorOrParty.isNotBlank()) {
                                KeyValueRow("Contractor / Dept", doc.contractorOrParty)
                            }
                        }

                        if (doc.summary.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "SUMMARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyAmber
                            )
                            Text(
                                text = doc.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Structured Line Items & Dispatch Details
                val (materials, totalQty, dispatchDetails) = remember(doc.extractedFields) {
                    val dropPattern = Regex("^(Gate\\s*Pass\\s*No|Slip\\s*No|Challan\\s*No|Doc\\s*No|Remarks|Sub\\s*Contractor|Company)\\s*:", RegexOption.IGNORE_CASE)
                    val datePattern = Regex("^Date\\s*:\\s*\\d", RegexOption.IGNORE_CASE)
                    val dispatchPattern = Regex("^(Vehicle\\s*No|Outward\\s*No|Inward\\s*No|Sign|Requisition\\s*by|Received\\s*by|To|Dept|Driver|Issue\\s*Type)\\b", RegexOption.IGNORE_CASE)
                    val totalPattern = Regex("^Total\\s*(Qty|Quantity)\\s*:", RegexOption.IGNORE_CASE)

                    val matList = mutableListOf<Pair<String, String>>()
                    val dispList = mutableListOf<Pair<String, String>>()
                    var tot: String? = null

                    for (rawLine in doc.extractedFields) {
                        val line = rawLine.trim()
                        if (line.isBlank() || dropPattern.containsMatchIn(line) || datePattern.containsMatchIn(line)) {
                            continue
                        }
                        if (totalPattern.containsMatchIn(line)) {
                            tot = line.substringAfter(":").trim()
                        } else if (dispatchPattern.containsMatchIn(line)) {
                            val parts = line.split(":", limit = 2)
                            if (parts.size == 2) {
                                dispList.add(parts[0].trim() to parts[1].trim())
                            } else {
                                dispList.add("Detail" to line)
                            }
                        } else {
                            val colonIdx = line.lastIndexOf(':')
                            if (colonIdx > 0 && colonIdx < line.length - 1) {
                                val desc = line.substring(0, colonIdx).trim()
                                val qty = line.substring(colonIdx + 1).trim()
                                matList.add(desc to qty)
                            } else {
                                matList.add(line to "")
                            }
                        }
                    }
                    Triple(matList, tot, dispList)
                }

                if (materials.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "MATERIALS & LINE ITEMS (${materials.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafetyAmber
                                )
                                Text(
                                    text = "QUANTITY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            materials.forEachIndexed { idx, (desc, qty) ->
                                if (idx > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp)
                                    )
                                    if (qty.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = formatQuantityForDisplay(qty),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (totalQty != null) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Surface(
                                    color = VerifiedTeal.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "TOTAL QUANTITY",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = VerifiedTeal
                                        )
                                        Text(
                                            text = formatQuantityForDisplay(totalQty),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (dispatchDetails.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TRANSPORT & DISPATCH DETAILS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            dispatchDetails.forEachIndexed { idx, (label, value) ->
                                if (idx > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                }
                                KeyValueRow(label, value)
                            }
                        }
                    }
                }

                if (materials.isEmpty() && dispatchDetails.isEmpty() && doc.extractedFields.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "EXTRACTED FIELDS (${doc.extractedFields.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyAmber
                            )
                            doc.extractedFields.forEachIndexed { idx, line ->
                                if (idx > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Searchable Full OCR Transcript Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "SEARCHABLE OCR TRANSCRIPT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VerifiedTeal
                                )
                                if (ocrSearchQuery.isNotBlank()) {
                                    Text(
                                        text = "$matchCount match(es) highlighted",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SafetyAmber
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(doc.ocrFullText))
                                    onCopyFeedback("Full OCR transcript copied")
                                },
                                modifier = Modifier.testTag("copy_ocr_text_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy OCR text",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy")
                            }
                        }

                        OutlinedTextField(
                            value = ocrSearchQuery,
                            onValueChange = { ocrSearchQuery = it },
                            placeholder = { Text("Find word or number inside this document…") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (ocrSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { ocrSearchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear find"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ocr_find_input")
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = highlightedTranscript,
                                    style = MonoTranscriptStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showZoomDialog && scanBitmap != null) {
        ZoomablePhotoDialog(
            bitmap = scanBitmap!!,
            title = doc.title,
            onRotate = {
                if (!isRotating) {
                    isRotating = true
                    scope.launch {
                        try {
                            val rotated = ImageStorageHelper.rotateImageFile90(
                                context = context,
                                localImageFileName = doc.localImageFileName.ifBlank { "${doc.id}.jpg" },
                                currentBitmap = scanBitmap,
                                docId = doc.id
                            )
                            if (rotated != null) {
                                scanBitmap = rotated
                                onCopyFeedback("Photo rotated 90°")
                            }
                        } finally {
                            isRotating = false
                        }
                    }
                }
            },
            onDismiss = { showZoomDialog = false }
        )
    }

    if (showEditSheet) {
        EditDocumentBottomSheet(
            doc = doc,
            onDismiss = { showEditSheet = false },
            onSave = { title, cat, docDate, filedDate, ref, org, party, summary, fields, ocr ->
                showEditSheet = false
                onSaveEdits(title, cat, docDate, filedDate, ref, org, party, summary, fields, ocr)
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete document?") },
            text = {
                Text("This permanently removes \"${doc.title}\" and its local searchable PDF.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_doc_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DateInfoBox(
    label: String,
    primary: String,
    secondary: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ZoomablePhotoDialog(
    bitmap: Bitmap,
    title: String,
    onRotate: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                scale = targetScale
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                val maxPanX = ((size.width * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
                                val maxPanY = ((size.height * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
                                val targetX = ((centerX - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxPanX, maxPanX)
                                val targetY = ((centerY - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxPanY, maxPanY)
                                offset = Offset(targetX, targetY)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        if (nextScale <= 1.01f) {
                            offset = Offset.Zero
                        } else {
                            val maxPanX = ((size.width * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                            val maxPanY = ((size.height * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                            val newX = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                            val newY = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            offset = Offset(newX, newY)
                        }
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Zoomable view of $title",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Double-tap or pinch to zoom",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 8.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                            onRotate()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate 90°",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close zoom view",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDocumentBottomSheet(
    doc: ScannedDocument,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    var title by remember { mutableStateOf(doc.title) }
    var category by remember { mutableStateOf(doc.categoryEnum) }
    var docDateText by remember {
        mutableStateOf(
            DateSearchParser.formatSlashDate(doc.documentDateIso.ifBlank { doc.filedDateIso })
        )
    }
    var ref by remember { mutableStateOf(doc.referenceNumber) }
    var org by remember { mutableStateOf(doc.organization) }
    var party by remember { mutableStateOf(doc.contractorOrParty) }
    var summary by remember { mutableStateOf(doc.summary) }
    var fieldsText by remember { mutableStateOf(doc.extractedFields.joinToString("\n")) }
    var ocrText by remember { mutableStateOf(doc.ocrFullText) }

    var showDocPicker by remember { mutableStateOf(false) }

    val parsedDoc = remember(docDateText) { DateSearchParser.parseDateQuery(docDateText) }
    val docErr = docDateText.isNotBlank() && parsedDoc == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Edit Document Metadata & OCR",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DocumentCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.shortLabel) }
                    )
                }
            }

            OutlinedTextField(
                value = docDateText,
                onValueChange = { docDateText = it },
                label = { Text("Document Date (DD/MM/YYYY)") },
                placeholder = { Text("DD/MM/YYYY") },
                isError = docErr,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showDocPicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick document date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ref,
                onValueChange = { ref = it.take(120) },
                label = { Text("Slip / Reference No.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = org,
                onValueChange = { org = it.take(200) },
                label = { Text("Company / Project") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = party,
                onValueChange = { party = it.take(200) },
                label = { Text("Contractor / Department") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it.take(1000) },
                label = { Text("Summary") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = fieldsText,
                onValueChange = { fieldsText = it },
                label = { Text("Extracted Fields (one per line)") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ocrText,
                onValueChange = { ocrText = it.take(50000) },
                label = { Text("Full Searchable OCR Transcript") },
                textStyle = MonoTranscriptStyle,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val fields = fieldsText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(30)
                    val effectiveIso = parsedDoc?.primaryIso ?: doc.documentDateIso.ifBlank { doc.filedDateIso }
                    onSave(
                        title,
                        category,
                        effectiveIso,
                        effectiveIso,
                        ref,
                        org,
                        party,
                        summary,
                        fields,
                        ocrText
                    )
                },
                enabled = title.isNotBlank() && !docErr,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_edit_doc_button")
            ) {
                Text("Save Changes & Rebuild PDF")
            }
        }
    }

    if (showDocPicker) {
        DocDatePickerDialog(
            initialIsoDate = parsedDoc?.primaryIso ?: DateSearchParser.todayIso(),
            title = "Select Document Date",
            onDismiss = { showDocPicker = false },
            onDateSelected = { iso -> docDateText = DateSearchParser.formatSlashDate(iso) }
        )
    }
}

internal fun buildHighlightedText(
    source: String,
    needle: String,
    highlightBg: Color,
    highlightFg: Color
): Pair<AnnotatedString, Int> {
    if (needle.isBlank()) return AnnotatedString(source) to 0
    var count = 0
    val annotated = buildAnnotatedString {
        append(source)
        var startIdx = 0
        while (startIdx < source.length) {
            val found = source.indexOf(needle, startIndex = startIdx, ignoreCase = true)
            if (found < 0) break
            val end = (found + needle.length).coerceAtMost(source.length)
            addStyle(
                style = SpanStyle(
                    background = highlightBg,
                    color = highlightFg,
                    fontWeight = FontWeight.Bold
                ),
                start = found,
                end = end
            )
            count++
            startIdx = end
        }
    }
    return annotated to count
}

internal fun formatQuantityForDisplay(rawQty: String): String {
    val trimmed = rawQty.trim()
    if (trimmed.isEmpty()) return ""
    val regex = Regex("""^([\d,]+)(?:\.0+)?(?:\.([0-9]+))?\s*([A-Za-z]+)?$""")
    val match = regex.find(trimmed)
    if (match != null) {
        val intPart = match.groupValues[1].replace(",", "")
        val decPart = match.groupValues[2]
        val unit = match.groupValues[3].uppercase()
        val numStr = if (decPart.isNotEmpty()) "$intPart.$decPart" else intPart
        return when (unit) {
            "EA", "NOS", "PIECE", "PIECES", "" -> "$numStr pcs"
            else -> "$numStr ${unit.lowercase()}"
        }
    }
    return trimmed
        .replace(Regex("""\.000\b"""), "")
        .replace(Regex("""\bEA\b""", RegexOption.IGNORE_CASE), "pcs")
        .trim()
}
