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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DocumentCategory
import com.example.ui.components.DocDatePickerDialog
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidGlassChip
import com.example.ui.theme.LiquidGlassPillButton
import com.example.ui.theme.LiquidGlassTone
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MonoTranscriptStyle
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.VerifiedTeal
import com.example.ui.theme.drawLiquidAmbientBackground
import com.example.ui.theme.liquidCapsuleShape
import com.example.ui.theme.liquidGlassSurface
import com.example.ui.theme.liquidSquircleShape
import com.example.util.DateSearchParser
import com.example.viewmodel.ScanDraft
import com.example.viewmodel.ScanFlowState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

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

    val isDark = LocalIsDarkTheme.current
    val currentIsDark by rememberUpdatedState(isDark)
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var topBarClearance by remember(statusTop) { mutableStateOf(statusTop + 70.dp) }

    val contentBackdrop = rememberLayerBackdrop {
        drawLiquidAmbientBackground(isDark = currentIsDark)
        drawContent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // LAYER 1 (CAPTURED BY BACKDROP): Full-bleed form / error body
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
            contentAlignment = Alignment.TopCenter
        ) {
            when (scanState) {
                is ScanFlowState.Error -> {
                    ScanErrorView(
                        state = scanState,
                        topContentPadding = topBarClearance + 8.dp,
                        onCancel = onCancel
                    )
                }

                is ScanFlowState.Review -> {
                    ReviewDraftView(
                        draft = scanState.draft,
                        isSaving = scanState.isSaving,
                        isExtractingWithAi = isExtractingWithAi,
                        topContentPadding = topBarClearance + 4.dp,
                        onAutoFillWithAi = onAutoFillWithAi,
                        onUpdateDraft = onUpdateDraft,
                        onSaveDocument = onSaveDocument
                    )
                }

                ScanFlowState.Idle -> Unit
            }
        }

        // LAYER 2: Floating Optical Top Header Capsule
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
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Text(
                        text = when (scanState) {
                            is ScanFlowState.Review -> "Verify Details & Save PDF"
                            is ScanFlowState.Error -> "Scan Interrupted"
                            else -> "Scan Document"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanErrorView(
    state: ScanFlowState.Error,
    topContentPadding: Dp,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = topContentPadding, bottom = 24.dp),
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
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = liquidSquircleShape(22.dp),
            tone = LiquidGlassTone.CARD
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
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Could not open this scan",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LiquidGlassPillButton(
            onClick = onCancel,
            tone = LiquidGlassTone.DOCK,
            elevation = 6.dp,
            backdrop = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("manual_entry_button")
        ) {
            Icon(imageVector = Icons.Default.EditNote, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Go Back to Vault", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReviewDraftView(
    draft: ScanDraft,
    isSaving: Boolean,
    isExtractingWithAi: Boolean = false,
    topContentPadding: Dp = 12.dp,
    onAutoFillWithAi: () -> Unit = {},
    onUpdateDraft: ((ScanDraft) -> ScanDraft) -> Unit,
    onSaveDocument: () -> Unit
) {
    // Keep editable date text fields decoupled from ISO normalization so typing never jumps
    var docDateText by remember(draft.bitmap) {
        mutableStateOf(
            if (draft.documentDateIso.isNotBlank()) DateSearchParser.formatSlashDate(draft.documentDateIso)
            else ""
        )
    }
    LaunchedEffect(draft.documentDateIso) {
        val currentParsed = DateSearchParser.parseDateQuery(docDateText)?.primaryIso ?: ""
        if (currentParsed != draft.documentDateIso) {
            docDateText = if (draft.documentDateIso.isNotBlank()) {
                DateSearchParser.formatSlashDate(draft.documentDateIso)
            } else ""
        }
    }
    var filedDateText by remember(draft.bitmap) {
        mutableStateOf(DateSearchParser.formatSlashDate(draft.filedDateIso))
    }
    LaunchedEffect(draft.filedDateIso) {
        val currentParsed = DateSearchParser.parseDateQuery(filedDateText)?.primaryIso ?: ""
        if (currentParsed != draft.filedDateIso) {
            filedDateText = DateSearchParser.formatSlashDate(draft.filedDateIso)
        }
    }
    var showDocDatePicker by remember { mutableStateOf(false) }
    var showFiledDatePicker by remember { mutableStateOf(false) }

    val parsedDocDate = remember(docDateText) { DateSearchParser.parseDateQuery(docDateText) }
    val parsedFiledDate = remember(filedDateText) { DateSearchParser.parseDateQuery(filedDateText) }
    val docDateError = docDateText.isNotBlank() && parsedDocDate == null
    val filedDateError = parsedFiledDate == null

    val isDark = LocalIsDarkTheme.current
    val currentIsDark by rememberUpdatedState(isDark)
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var bottomBarClearance by remember(navBottom) { mutableStateOf(navBottom + 86.dp) }
    val formBackdrop = rememberLayerBackdrop {
        drawLiquidAmbientBackground(isDark = currentIsDark)
        drawContent()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(formBackdrop)
                .widthIn(max = 700.dp)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = topContentPadding,
                    bottom = bottomBarClearance + 14.dp
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Compact Scan Preview + OCR Status Banner
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = liquidSquircleShape(24.dp),
                tone = LiquidGlassTone.CARD
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = draft.bitmap.asImageBitmap(),
                        contentDescription = "Scanned page thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 72.dp, height = 92.dp)
                            .clip(RoundedCornerShape(14.dp))
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

            // Quick Save info banner
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                tone = LiquidGlassTone.SUBTLE
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Photo ready. Confirm date below and tap Save at the bottom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // AI Auto-Fill Action Button (Optional)
            LiquidGlassPillButton(
                onClick = onAutoFillWithAi,
                enabled = !isExtractingWithAi && !isSaving,
                tone = LiquidGlassTone.DOCK,
                elevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_ai_auto_fill")
            ) {
                if (isExtractingWithAi) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning with Gemini AI (optional)...", fontWeight = FontWeight.Medium)
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (draft.ocrFullText.isNotBlank()) "Re-Scan with AI (Optional)" else "Auto-Fill with AI (Optional)",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Document Title
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> onUpdateDraft { it.copy(title = v.take(200)) } },
                label = { Text("Document Title") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("draft_title_input")
            )

            // Category Selection Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        LiquidGlassChip(
                            selected = draft.category == cat,
                            onClick = { onUpdateDraft { it.copy(category = cat) } },
                            label = cat.shortLabel
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

        // Bottom Save & Generate Searchable PDF Bar (Optical Liquid Glass)
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
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = liquidCapsuleShape(),
                tone = LiquidGlassTone.DOCK,
                elevation = 10.dp,
                backdrop = formBackdrop
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    LiquidGlassPillButton(
                        onClick = onSaveDocument,
                        enabled = !isSaving && !docDateError && !filedDateError && draft.title.isNotBlank(),
                        tone = LiquidGlassTone.ACCENT,
                        elevation = 6.dp,
                        backdrop = null,
                        contentPadding = PaddingValues(vertical = 13.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_scanned_doc_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Building Searchable PDF & Saving…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.save_and_create_pdf),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
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
