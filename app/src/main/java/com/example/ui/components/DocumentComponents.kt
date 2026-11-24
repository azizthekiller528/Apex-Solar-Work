package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SlipRose
import com.example.ui.theme.SlipRoseContainer
import com.example.ui.theme.VerifiedTeal
import com.example.ui.theme.VerifiedTealContainer
import com.example.util.DateSearchParser
import com.example.util.ImageStorageHelper
import com.example.util.ThumbnailImageLoader
import java.io.File

fun categoryIcon(category: DocumentCategory): ImageVector = when (category) {
    DocumentCategory.MATERIAL_SLIP -> Icons.Outlined.Inventory2
    DocumentCategory.INSPECTION_PROTOCOL -> Icons.AutoMirrored.Filled.FactCheck
    DocumentCategory.DELIVERY_CHALLAN -> Icons.Default.LocalShipping
    DocumentCategory.INVOICE_RECEIPT -> Icons.AutoMirrored.Filled.ReceiptLong
    DocumentCategory.SITE_REPORT -> Icons.AutoMirrored.Filled.Assignment
    DocumentCategory.OTHER -> Icons.Default.Description
}

@Composable
fun CategoryBadge(
    category: DocumentCategory,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (category) {
        DocumentCategory.MATERIAL_SLIP -> SlipRoseContainer to SlipRose
        DocumentCategory.INSPECTION_PROTOCOL -> VerifiedTealContainer to VerifiedTeal
        DocumentCategory.DELIVERY_CHALLAN -> SafetyAmberContainer to SafetyAmber
        DocumentCategory.INVOICE_RECEIPT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DocumentCategory.SITE_REPORT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        DocumentCategory.OTHER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = category.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1
            )
        }
    }
}

private sealed interface ThumbnailSourceState {
    data object Loading : ThumbnailSourceState
    data class Ready(val file: File) : ThumbnailSourceState
    data object Unavailable : ThumbnailSourceState
}

@Composable
fun DocumentThumbnail(
    doc: ScannedDocument,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasImageSource = doc.localImageFileName.isNotBlank() || doc.previewBase64.isNotBlank() || doc.id.startsWith("apex_doc_")
    val cacheKey = remember(
        doc.id,
        doc.localImageFileName,
        doc.previewBase64.length,
        doc.previewBase64.hashCode()
    ) {
        ThumbnailImageLoader.buildCacheKey(
            docId = doc.id,
            localImageFileName = doc.localImageFileName,
            previewBase64 = doc.previewBase64
        )
    }

    // Bounded to at most 1 retry per cacheKey if Android OS evicted the cacheDir file while backgrounded
    var retryGeneration by remember(cacheKey) { mutableStateOf(0) }

    val sourceState by produceState<ThumbnailSourceState>(
        initialValue = when {
            !hasImageSource -> ThumbnailSourceState.Unavailable
            else -> ImageStorageHelper.peekThumbnailSourceFile(cacheKey)
                ?.let { ThumbnailSourceState.Ready(it) }
                ?: ThumbnailSourceState.Loading
        },
        key1 = cacheKey,
        key2 = hasImageSource,
        key3 = retryGeneration
    ) {
        if (!hasImageSource) {
            value = ThumbnailSourceState.Unavailable
            return@produceState
        }
        if (retryGeneration > 0 || value !is ThumbnailSourceState.Ready) {
            if (retryGeneration > 0) {
                value = ThumbnailSourceState.Loading
            }
            val resolved = ImageStorageHelper.resolveThumbnailSourceFile(
                context = context,
                docId = doc.id,
                localImageFileName = doc.localImageFileName,
                previewBase64 = doc.previewBase64,
                cacheKey = cacheKey
            )
            value = if (resolved != null) {
                ThumbnailSourceState.Ready(resolved)
            } else {
                ThumbnailSourceState.Unavailable
            }
        }
    }

    val imageLoader = remember(context) { ThumbnailImageLoader.get(context) }
    val readyFile = (sourceState as? ThumbnailSourceState.Ready)?.file
    val imageRequest = remember(readyFile, cacheKey, retryGeneration) {
        readyFile?.let { file ->
            ThumbnailImageLoader.buildThumbnailRequest(
                context = context,
                sourceFile = file,
                cacheKey = cacheKey
            )
        }
    }

    Box(
        modifier = modifier
            .testTag("doc_thumbnail_${doc.id}")
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            imageRequest != null -> {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    imageLoader = imageLoader,
                    contentDescription = "Preview of ${doc.title}",
                    contentScale = ContentScale.Crop,
                    onError = {
                        if (retryGeneration == 0 && doc.previewBase64.isNotBlank()) {
                            ImageStorageHelper.invalidateThumbnailSource(cacheKey)
                            ThumbnailImageLoader.evict(context, cacheKey)
                            retryGeneration = 1
                        }
                    },
                    loading = {
                        ThumbnailFallbackPlaceholder(
                            category = doc.categoryEnum,
                            isLoading = true
                        )
                    },
                    error = {
                        ThumbnailFallbackPlaceholder(
                            category = doc.categoryEnum,
                            isLoading = false
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Compact PDF badge overlay for rapid visual scanning in document lists
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "PDF",
                        style = MonoCodeStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            sourceState is ThumbnailSourceState.Loading -> {
                ThumbnailFallbackPlaceholder(
                    category = doc.categoryEnum,
                    isLoading = true
                )
            }

            else -> {
                ThumbnailFallbackPlaceholder(
                    category = doc.categoryEnum,
                    isLoading = false
                )
            }
        }
    }
}

@Composable
private fun ThumbnailFallbackPlaceholder(
    category: DocumentCategory,
    isLoading: Boolean
) {
    val tint = if (isLoading) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLoading) categoryIcon(category) else Icons.Default.PictureAsPdf,
            contentDescription = if (isLoading) "Loading document preview" else "PDF document placeholder",
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
fun DocumentLedgerCard(
    doc: ScannedDocument,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onSharePdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("document_card_${doc.id}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                DocumentThumbnail(
                    doc = doc,
                    modifier = Modifier.size(width = 68.dp, height = 88.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CategoryBadge(category = doc.categoryEnum)
                        if (doc.referenceNumber.isNotBlank()) {
                            Text(
                                text = "#${doc.referenceNumber}",
                                style = MonoCodeStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }

                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val subtitle = buildList {
                        if (doc.contractorOrParty.isNotBlank()) add(doc.contractorOrParty)
                        if (doc.organization.isNotBlank()) add(doc.organization)
                    }.joinToString(" • ")

                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (doc.summary.isNotBlank()) {
                        Text(
                            text = doc.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val effectiveDocDate = doc.documentDateIso.ifBlank { doc.filedDateIso }
                    if (effectiveDocDate.isNotBlank()) {
                        DateStampChip(
                            prefix = "Doc",
                            isoDate = effectiveDocDate,
                            highlighted = true
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("star_doc_${doc.id}")
                    ) {
                        Icon(
                            imageVector = if (doc.starred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (doc.starred) "Remove star" else "Star document",
                            tint = if (doc.starred) SafetyAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onSharePdf,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("share_pdf_${doc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share searchable PDF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DateStampChip(
    prefix: String,
    isoDate: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$prefix ${DateSearchParser.formatSlashDate(isoDate)}",
            style = MonoCodeStyle,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocDatePickerDialog(
    initialIsoDate: String,
    title: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val initialMillis = remember(initialIsoDate) {
        DateSearchParser.isoToUtcMillis(initialIsoDate)
            ?: DateSearchParser.isoToUtcMillis(DateSearchParser.todayIso())
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        onDateSelected(DateSearchParser.utcMillisToIso(millis))
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("date_picker_confirm")
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSourceBottomSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickSampleForm: (assetPath: String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Scan Document to Searchable PDF",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "AI OCR reads printed tables, stamps, and handwritten dates (DD/MM/YYYY) automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            ScanSourceRow(
                icon = Icons.Default.CameraAlt,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Capture with Camera",
                subtitle = "Snap a slip, inspection checklist, or challan directly",
                testTag = "scan_source_camera",
                onClick = {
                    onDismiss()
                    onTakePhoto()
                }
            )

            ScanSourceRow(
                icon = Icons.Default.PhotoLibrary,
                iconBg = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = "Choose from Photo Gallery",
                subtitle = "Import existing document photos with zero storage permissions",
                testTag = "scan_source_gallery",
                onClick = {
                    onDismiss()
                    onPickFromGallery()
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Workspaces,
                    contentDescription = null,
                    tint = VerifiedTeal,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "TEST WITH YOUR 2 UPLOADED FORMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerifiedTeal
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SampleFormChip(
                    title = "Material Slip #6717",
                    subtitle = "Handwritten 24/02/2026",
                    testTag = "scan_sample_slip",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onPickSampleForm("sample_forms/kalpataru_material_slip.jpg")
                    }
                )
                SampleFormChip(
                    title = "Solar Inspection",
                    subtitle = "Protocol table form",
                    testTag = "scan_sample_protocol",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onPickSampleForm("sample_forms/kalpataru_solar_inspection.jpg")
                    }
                )
            }
        }
    }
}

@Composable
private fun ScanSourceRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SampleFormChip(
    title: String,
    subtitle: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VerifiedTeal.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
