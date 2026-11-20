package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DateMatchMode
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.model.UiState
import com.example.ui.components.DocDatePickerDialog
import com.example.ui.components.DocumentLedgerCard
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.VerifiedTeal
import com.example.util.DateSearchParser
import com.example.viewmodel.VaultFilterState

@Composable
fun VaultScreen(
    documentsState: UiState<List<ScannedDocument>>,
    filteredDocuments: List<ScannedDocument>,
    filterState: VaultFilterState,
    starredOnly: Boolean,
    isExportingPdf: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleAlternateDate: () -> Unit,
    onSelectExplicitDate: (String?) -> Unit,
    onSelectRangeStart: (String?) -> Unit,
    onSelectRangeEnd: (String?) -> Unit,
    onClearDateRange: () -> Unit,
    onClearSearchAndDate: () -> Unit,
    onSelectCategory: (DocumentCategory?) -> Unit,
    onSelectDateMatchMode: (DateMatchMode) -> Unit,
    onClearFilters: () -> Unit,
    onOpenDocument: (ScannedDocument) -> Unit,
    onToggleStar: (ScannedDocument) -> Unit,
    onSharePdf: (ScannedDocument) -> Unit,
    onExportDayPdf: (String, List<ScannedDocument>) -> Unit,
    onRequestScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
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

    val hasAnyFilter = filterState.query.isNotBlank() ||
        filterState.explicitDateIso != null ||
        filterState.hasActiveDateRange ||
        filterState.selectedCategory != null

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Instant Boss Date Lookup Bar
            item(key = "search_bar") {
                OutlinedTextField(
                    value = filterState.query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search documents",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (filterState.query.isNotBlank() ||
                                filterState.explicitDateIso != null ||
                                filterState.hasActiveDateRange
                            ) {
                                IconButton(
                                    onClick = onClearSearchAndDate,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("vault_date_picker_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Pick exact date to filter",
                                    tint = if (filterState.activeDateIso != null || filterState.hasActiveDateRange) {
                                        SafetyAmber
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_input")
                )
            }

            // Quick Date Range Chips (From / To)
            item(key = "date_range_bar") {
                val (effectiveStart, effectiveEnd) = filterState.effectiveDateRange
                val startDisplay = if (filterState.hasActiveDateRange && effectiveStart != null) {
                    "From: ${DateSearchParser.formatSlashDate(effectiveStart)}"
                } else {
                    "From date"
                }
                val endDisplay = if (filterState.hasActiveDateRange && effectiveEnd != null) {
                    "To: ${DateSearchParser.formatSlashDate(effectiveEnd)}"
                } else {
                    "To date"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterState.hasActiveDateRange && effectiveStart != null,
                        onClick = { showRangeStartPicker = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(startDisplay, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("vault_range_start_chip")
                    )
                    FilterChip(
                        selected = filterState.hasActiveDateRange && effectiveEnd != null,
                        onClick = { showRangeEndPicker = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(endDisplay, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("vault_range_end_chip")
                    )
                    if (filterState.hasExplicitRange) {
                        AssistChip(
                            onClick = onClearDateRange,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear date range",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Clear range", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("vault_clear_range_chip")
                        )
                    }
                }
            }

            // Smart Date or Date-Range Detection Banner
            item(key = "active_date_banner") {
                val activeDateIso = filterState.activeDateIso
                val (rangeStart, rangeEnd) = filterState.effectiveDateRange
                val showBanner = activeDateIso != null || filterState.hasActiveDateRange
                AnimatedVisibility(visible = showBanner) {
                    if (activeDateIso != null) {
                        val parsed = filterState.parsedQueryDate
                        val alternateIso = if (filterState.explicitDateIso == null) {
                            if (filterState.useAlternateDateFormat) parsed?.primaryIso else parsed?.alternateIso
                        } else null

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "DATE FILTER ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${DateSearchParser.formatSlashDate(activeDateIso)} • ${DateSearchParser.formatFriendlyDate(activeDateIso)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (displayedDocs.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = { onExportDayPdf(activeDateIso, displayedDocs) },
                                                enabled = !isExportingPdf,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("vault_export_day_pdf")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureAsPdf,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Day PDF", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        if (filterState.explicitDateIso != null) {
                                            IconButton(
                                                onClick = { onSelectExplicitDate(null) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear date filter"
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DateMatchMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = filterState.dateMatchMode == mode,
                                            onClick = { onSelectDateMatchMode(mode) },
                                            label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) }
                                        )
                                    }
                                    if (alternateIso != null) {
                                        AssistChip(
                                            onClick = onToggleAlternateDate,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.SwapHoriz,
                                                    contentDescription = "Switch DD/MM and MM/DD date format",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = "Try ${DateSearchParser.formatFriendlyDate(alternateIso)}",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            },
                                            modifier = Modifier.testTag("toggle_date_format_chip")
                                        )
                                    }
                                }
                            }
                        }
                    } else if (filterState.hasActiveDateRange) {
                        val rangeLabel = buildString {
                            append(rangeStart?.let { DateSearchParser.formatSlashDate(it) } ?: "Any")
                            append("  →  ")
                            append(rangeEnd?.let { DateSearchParser.formatSlashDate(it) } ?: "Any")
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "DATE RANGE FILTER ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = rangeLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (filterState.parsedQueryDateRange != null) {
                                                onClearSearchAndDate()
                                            } else {
                                                onClearDateRange()
                                            }
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("banner_clear_range_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear date range filter"
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DateMatchMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = filterState.dateMatchMode == mode,
                                            onClick = { onSelectDateMatchMode(mode) },
                                            label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category Filter Chips
            item(key = "category_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterState.selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("All (${baseDocs.size})") },
                        modifier = Modifier.testTag("cat_chip_all")
                    )
                    DocumentCategory.entries.forEach { category ->
                        FilterChip(
                            selected = filterState.selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                            label = { Text(category.shortLabel) },
                            modifier = Modifier.testTag("cat_chip_${category.name}")
                        )
                    }
                }
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

                        items(items = displayedDocs, key = { it.id }) { doc ->
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
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (hasFilters) Icons.Default.Search else Icons.Default.DocumentScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp)
            )
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
                    hasFilters -> "No documents were found for that date or keyword. Try switching DD/MM format or clearing filters."
                    starredOnly -> "Star important slips or inspection protocols so your boss can find them in one tap."
                    else -> stringResource(R.string.empty_vault_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (hasFilters) {
                OutlinedButton(
                    onClick = onClearFilters,
                    modifier = Modifier.testTag("empty_clear_filters_button")
                ) {
                    Text("Clear Search & Filters")
                }
            } else if (!starredOnly) {
                Button(
                    onClick = onRequestScan,
                    modifier = Modifier.testTag("empty_scan_first_doc_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan First Document")
                }
            }
        }
    }
}
