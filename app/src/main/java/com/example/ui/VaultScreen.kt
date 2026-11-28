package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DateMatchMode
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.model.UiState
import com.example.ui.components.DocDatePickerDialog
import com.example.ui.components.DocumentLedgerCard
import com.example.ui.components.DocumentSearchFilterBar
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidGlassPillButton
import com.example.ui.theme.LiquidGlassTone
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.VerifiedTeal
import com.example.util.DateSearchParser
import com.example.viewmodel.FirestoreSearchState
import com.example.viewmodel.VaultFilterState

@Composable
fun VaultScreen(
    documentsState: UiState<List<ScannedDocument>>,
    filteredDocuments: List<ScannedDocument>,
    filterState: VaultFilterState,
    firestoreSearchState: FirestoreSearchState,
    starredOnly: Boolean,
    isExportingPdf: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleAlternateDate: () -> Unit,
    onSelectExplicitDate: (String?) -> Unit,
    onSelectDateRange: (String?, String?) -> Unit,
    onSelectRangeStart: (String?) -> Unit,
    onSelectRangeEnd: (String?) -> Unit,
    onClearDateRange: () -> Unit,
    onClearSearchAndDate: () -> Unit,
    onSelectCategory: (DocumentCategory?) -> Unit,
    onSelectDateMatchMode: (DateMatchMode) -> Unit,
    onRetryCloudSearch: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenDocument: (ScannedDocument) -> Unit,
    onToggleStar: (ScannedDocument) -> Unit,
    onSharePdf: (ScannedDocument) -> Unit,
    onExportDayPdf: (String, List<ScannedDocument>) -> Unit,
    onRequestScan: () -> Unit,
    modifier: Modifier = Modifier,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = 104.dp
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showRangeStartPicker by remember { mutableStateOf(false) }
    var showRangeEndPicker by remember { mutableStateOf(false) }

    val allDocs = (documentsState as? UiState.Success)?.data ?: emptyList()
    val baseDocs = remember(allDocs, starredOnly) {
        if (starredOnly) allDocs.filter { it.starred } else allDocs
    }
    val displayedDocs = remember(filteredDocuments, starredOnly) {
        if (starredOnly) filteredDocuments.filter { it.starred } else filteredDocuments
    }

    val hasAnyFilter = filterState.hasAnyActiveFilter

    val todayIso = remember { DateSearchParser.todayIso() }
    val todayCount = remember(allDocs, todayIso) {
        allDocs.count { it.filedDateIso == todayIso || it.documentDateIso == todayIso }
    }
    val starredCount = remember(allDocs) { allDocs.count { it.starred } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topContentPadding,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dedicated Search & Date-Range Filter Component (Firestore query-backed)
            item(key = "search_filter_bar") {
                DocumentSearchFilterBar(
                    filterState = filterState,
                    firestoreSearchState = firestoreSearchState,
                    displayedDocs = displayedDocs,
                    totalBaseCount = baseDocs.size,
                    isExportingPdf = isExportingPdf,
                    onQueryChange = onQueryChange,
                    onToggleAlternateDate = onToggleAlternateDate,
                    onSelectExplicitDate = onSelectExplicitDate,
                    onSelectDateRange = onSelectDateRange,
                    onClearDateRange = onClearDateRange,
                    onClearSearchAndDate = onClearSearchAndDate,
                    onSelectCategory = onSelectCategory,
                    onSelectDateMatchMode = onSelectDateMatchMode,
                    onRetryCloudSearch = onRetryCloudSearch,
                    onExportDayPdf = onExportDayPdf,
                    onOpenExactDatePicker = { showDatePicker = true },
                    onOpenRangeStartPicker = { showRangeStartPicker = true },
                    onOpenRangeEndPicker = { showRangeEndPicker = true }
                )
            }

            // Summary Metrics Strip (when in main Vault unfiltered)
            if (!starredOnly && !hasAnyFilter && allDocs.isNotEmpty()) {
                item(key = "metrics_strip") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricMiniCard(
                            label = "INDEXED PDFS",
                            value = allDocs.size.toString(),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            label = "FILED TODAY",
                            value = todayCount.toString(),
                            accent = VerifiedTeal,
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            label = "STARRED",
                            value = starredCount.toString(),
                            accent = SafetyAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Content State
            when (documentsState) {
                is UiState.Loading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Syncing document archive…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "error") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = documentsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    if (displayedDocs.isEmpty()) {
                        item(key = "empty") {
                            VaultEmptyState(
                                hasFilters = hasAnyFilter,
                                starredOnly = starredOnly,
                                onClearFilters = onClearFilters,
                                onRequestScan = onRequestScan
                            )
                        }
                    } else {
                        item(key = "result_count") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (hasAnyFilter) {
                                        "${displayedDocs.size} matching document(s)"
                                    } else if (starredOnly) {
                                        "${displayedDocs.size} starred document(s)"
                                    } else {
                                        "RECENTLY FILED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (hasAnyFilter) {
                                    TextButton(
                                        onClick = onClearFilters,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Reset", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        items(
                            items = displayedDocs,
                            key = { it.id },
                            contentType = { "doc_card" }
                        ) { doc ->
                            DocumentLedgerCard(
                                doc = doc,
                                onClick = { onOpenDocument(doc) },
                                onToggleStar = { onToggleStar(doc) },
                                onSharePdf = { onSharePdf(doc) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DocDatePickerDialog(
            initialIsoDate = filterState.activeDateIso ?: todayIso,
            title = "Filter Vault by Date",
            onDismiss = { showDatePicker = false },
            onDateSelected = { iso ->
                onSelectExplicitDate(iso)
            }
        )
    }

    if (showRangeStartPicker) {
        DocDatePickerDialog(
            initialIsoDate = filterState.rangeStartDateIso ?: filterState.effectiveDateRange.first ?: todayIso,
            title = "Select Start Date (From)",
            onDismiss = { showRangeStartPicker = false },
            onDateSelected = { iso ->
                onSelectRangeStart(iso)
            }
        )
    }

    if (showRangeEndPicker) {
        DocDatePickerDialog(
            initialIsoDate = filterState.rangeEndDateIso ?: filterState.effectiveDateRange.second ?: todayIso,
            title = "Select End Date (To)",
            onDismiss = { showRangeEndPicker = false },
            onDateSelected = { iso ->
                onSelectRangeEnd(iso)
            }
        )
    }
}

@Composable
private fun MetricMiniCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        tone = LiquidGlassTone.CARD
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VaultEmptyState(
    hasFilters: Boolean,
    starredOnly: Boolean,
    onClearFilters: () -> Unit,
    onRequestScan: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(26.dp),
        tone = LiquidGlassTone.CARD
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasFilters) Icons.Default.Search else Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = when {
                    hasFilters -> "No matching documents"
                    starredOnly -> "No starred documents yet"
                    else -> stringResource(R.string.empty_vault_title)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = when {
                    hasFilters -> "No documents matched that keyword or date range. Try switching date format or clearing filters."
                    starredOnly -> "Star important slips or inspection protocols so your boss can find them in one tap."
                    else -> stringResource(R.string.empty_vault_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (hasFilters) {
                LiquidGlassPillButton(
                    onClick = onClearFilters,
                    tone = LiquidGlassTone.DOCK,
                    elevation = 4.dp,
                    modifier = Modifier.testTag("empty_clear_filters_button")
                ) {
                    Text("Clear Search & Filters", fontWeight = FontWeight.SemiBold)
                }
            } else if (!starredOnly) {
                LiquidGlassPillButton(
                    onClick = onRequestScan,
                    tone = LiquidGlassTone.ACCENT,
                    modifier = Modifier.testTag("empty_scan_first_doc_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan First Document", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
