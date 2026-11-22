package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DocumentCategory
import com.example.ui.components.DocDatePickerDialog
import com.example.ui.theme.MonoTranscriptStyle
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.VerifiedTeal
import com.example.util.DateSearchParser
import com.example.viewmodel.ScanDraft
import com.example.viewmodel.ScanFlowState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    scanState: ScanFlowState,
    isExtractingWithAi: Boolean = false,
    onAutoFillWithAi: () -> Unit = {},
    onUpdateDraft: ((ScanDraft) -> ScanDraft) -> Unit,
    onSaveDocument: () -> Unit,
    onCancel: () -> Unit
) {
    val isSaving = (scanState as? ScanFlowState.Review)?.isSaving == true
    BackHandler(enabled = !isSaving) {
        onCancel()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (scanState) {
                            is ScanFlowState.Review -> "Verify Details & Save PDF"
                            is ScanFlowState.Error -> "Scan Interrupted"
                            else -> "Scan Document"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        enabled = !isSaving,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("scan_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel scan"
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
            when (scanState) {
                is ScanFlowState.Error -> {
                    ScanErrorView(
                        state = scanState,
                        onCancel = onCancel
                    )
                }

                is ScanFlowState.Review -> {
                    ReviewDraftView(
                        draft = scanState.draft,
                        isSaving = scanState.isSaving,
                        isExtractingWithAi = isExtractingWithAi,
                        onAutoFillWithAi = onAutoFillWithAi,
                        onUpdateDraft = onUpdateDraft,
                        onSaveDocument = onSaveDocument
                    )
                }

                ScanFlowState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ScanErrorView(
    state: ScanFlowState.Error,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.previewBitmap != null) {
            Image(
                bitmap = state.previewBitmap.asImageBitmap(),
                contentDescription = "Captured document preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Could not open this scan",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("manual_entry_button")
        ) {
            Icon(imageVector = Icons.Default.EditNote, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Go Back to Vault")
        }
    }
}

@Composable
private fun ReviewDraftView(
    draft: ScanDraft,
    isSaving: Boolean,
    isExtractingWithAi: Boolean = false,
    onAutoFillWithAi: () -> Unit = {},
    onUpdateDraft: ((ScanDraft) -> ScanDraft) -> Unit,
    onSaveDocument: () -> Unit
) {
    // Keep editable date text fields decoupled from ISO normalization so typing never jumps
    var docDateText by remember(draft.bitmap, draft.documentDateIso) {
        mutableStateOf(
            if (draft.documentDateIso.isNotBlank()) DateSearchParser.formatSlashDate(draft.documentDateIso)
            else ""
        )
    }
    var filedDateText by remember(draft.bitmap, draft.filedDateIso) {
        mutableStateOf(DateSearchParser.formatSlashDate(draft.filedDateIso))
    }
    var showDocDatePicker by remember { mutableStateOf(false) }
    var showFiledDatePicker by remember { mutableStateOf(false) }

    val parsedDocDate = remember(docDateText) { DateSearchParser.parseDateQuery(docDateText) }
    val parsedFiledDate = remember(filedDateText) { DateSearchParser.parseDateQuery(filedDateText) }
    val docDateError = docDateText.isNotBlank() && parsedDocDate == null
    val filedDateError = parsedFiledDate == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 700.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Compact Scan Preview + OCR Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = draft.bitmap.asImageBitmap(),
                        contentDescription = "Scanned page thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 72.dp, height = 92.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (draft.ocrFullText.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = VerifiedTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (draft.ocrFullText.isNotBlank()) "OCR EXTRACTED" else "MANUAL REVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = VerifiedTeal
                            )
                        }
                        Text(
                            text = "Check the extracted dates & fields below. Saving builds a searchable A4 PDF automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // AI Auto-Fill Action Button
            Button(
                onClick = onAutoFillWithAi,
                enabled = !isExtractingWithAi && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_ai_auto_fill"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isExtractingWithAi) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning Document with Gemini AI...")
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (draft.ocrFullText.isNotBlank()) "Re-Scan with AI" else "Auto-Fill Fields with AI"
                    )
                }
            }

            // Document Title
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> onUpdateDraft { it.copy(title = v.take(200)) } },
                label = { Text("Document Title") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("draft_title_input")
            )

            // Category Selection Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DOCUMENT TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DocumentCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = draft.category == cat,
                            onClick = { onUpdateDraft { it.copy(category = cat) } },
                            label = { Text(cat.shortLabel) }
                        )
                    }
                }
            }

            // Dual Date Indexing Row (Document Date + Filed Date)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = docDateText,
                    onValueChange = { raw ->
                        docDateText = raw
                        val parsed = DateSearchParser.parseDateQuery(raw)
                        onUpdateDraft {
                            it.copy(
                                documentDateIso = when {
                                    raw.isBlank() -> ""
                                    parsed != null -> parsed.primaryIso
                                    else -> it.documentDateIso
                                }
                            )
                        }
                    },
                    label = { Text("Document Date") },
                    placeholder = { Text("DD/MM/YYYY") },
                    singleLine = true,
                    isError = docDateError,
                    supportingText = {
                        Text(
                            text = when {
                                docDateText.isBlank() -> "Optional (blank on form)"
                                parsedDocDate != null -> DateSearchParser.formatFriendlyDate(parsedDocDate.primaryIso)
                                else -> "Use DD/MM/YYYY"
                            }
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showDocDatePicker = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Pick document date"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("draft_doc_date_input")
                )

                OutlinedTextField(
                    value = filedDateText,
                    onValueChange = { raw ->
                        filedDateText = raw
                        val parsed = DateSearchParser.parseDateQuery(raw)
                        if (parsed != null) {
                            onUpdateDraft { it.copy(filedDateIso = parsed.primaryIso) }
                        }
                    },
                    label = { Text("Filed Date") },
                    placeholder = { Text("DD/MM/YYYY") },
                    singleLine = true,
                    isError = filedDateError,
                    supportingText = {
                        Text(
                            text = if (parsedFiledDate != null) {
                                DateSearchParser.formatFriendlyDate(parsedFiledDate.primaryIso)
                            } else {
                                "Required DD/MM/YYYY"
                            }
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showFiledDatePicker = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Pick filed date"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("draft_filed_date_input")
                )
            }

            // Reference Number & Party
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft.referenceNumber,
                    onValueChange = { v -> onUpdateDraft { it.copy(referenceNumber = v.take(120)) } },
                    label = { Text("Slip / Ref No.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.contractorOrParty,
                    onValueChange = { v -> onUpdateDraft { it.copy(contractorOrParty = v.take(200)) } },
                    label = { Text("Contractor / Dept") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = draft.organization,
                onValueChange = { v -> onUpdateDraft { it.copy(organization = v.take(200)) } },
                label = { Text("Company / Project") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = draft.summary,
                onValueChange = { v -> onUpdateDraft { it.copy(summary = v.take(1000)) } },
                label = { Text("Summary") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = draft.extractedFieldsText,
                onValueChange = { v -> onUpdateDraft { it.copy(extractedFieldsText = v) } },
                label = { Text("Extracted Fields & Line Items (one per line)") },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = draft.ocrFullText,
                onValueChange = { v -> onUpdateDraft { it.copy(ocrFullText = v.take(50000)) } },
                label = { Text("Full Searchable OCR Transcript") },
                textStyle = MonoTranscriptStyle,
                minLines = 5,
                maxLines = 12,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("draft_ocr_text_input")
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Bottom Save & Generate Searchable PDF Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onSaveDocument,
                    enabled = !isSaving && !docDateError && !filedDateError && draft.title.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_scanned_doc_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Building Searchable PDF & Saving…")
                    } else {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.save_and_create_pdf),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showDocDatePicker) {
        DocDatePickerDialog(
            initialIsoDate = draft.documentDateIso.ifBlank { DateSearchParser.todayIso() },
            title = "Select Document Date",
            onDismiss = { showDocDatePicker = false },
            onDateSelected = { iso ->
                docDateText = DateSearchParser.formatSlashDate(iso)
                onUpdateDraft { it.copy(documentDateIso = iso) }
            }
        )
    }

    if (showFiledDatePicker) {
        DocDatePickerDialog(
            initialIsoDate = draft.filedDateIso.ifBlank { DateSearchParser.todayIso() },
            title = "Select Filed Date",
            onDismiss = { showFiledDatePicker = false },
            onDateSelected = { iso ->
                filedDateText = DateSearchParser.formatSlashDate(iso)
                onUpdateDraft { it.copy(filedDateIso = iso) }
            }
        )
    }
}
