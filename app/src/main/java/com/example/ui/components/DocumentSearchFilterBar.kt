package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DateMatchMode
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.VerifiedTeal
import com.example.util.DateSearchParser
import com.example.viewmodel.FirestoreSearchState
import com.example.viewmodel.VaultFilterState

private data class DateRangePreset(
    val id: String,
    val labelRes: Int,
    val startIso: String,
    val endIso: String
)

/**
 * Reusable Material 3 Search & Date-Range Filter component backed by live Firestore queries.
 * Supports keyword search, custom From/To date range pickers, 1-tap date-range presets,
 * DD/MM ↔ MM/DD disambiguation, DateMatchMode switching, category chips, and cloud query status.
 */
@Composable
fun DocumentSearchFilterBar(
    filterState: VaultFilterState,
    firestoreSearchState: FirestoreSearchState,
    displayedDocs: List<ScannedDocument>,
    totalBaseCount: Int,
    isExportingPdf: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleAlternateDate: () -> Unit,
    onSelectExplicitDate: (String?) -> Unit,
    onSelectDateRange: (String?, String?) -> Unit,
    onClearDateRange: () -> Unit,
    onClearSearchAndDate: () -> Unit,
    onSelectCategory: (DocumentCategory?) -> Unit,
    onSelectDateMatchMode: (DateMatchMode) -> Unit,
    onRetryCloudSearch: () -> Unit,
    onExportDayPdf: (String, List<ScannedDocument>) -> Unit,
    onOpenExactDatePicker: () -> Unit,
    onOpenRangeStartPicker: () -> Unit,
    onOpenRangeEndPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val todayIso = remember { DateSearchParser.todayIso() }
    val presets = remember(todayIso) {
        listOf(
            DateRangePreset(
                id = "today",
                labelRes = R.string.search_preset_today,
                startIso = todayIso,
                endIso = todayIso
            ),
            DateRangePreset(
                id = "last_7d",
                labelRes = R.string.search_preset_last_7_days,
                startIso = DateSearchParser.daysAgoIso(6),
                endIso = todayIso
            ),
            DateRangePreset(
                id = "last_30d",
                labelRes = R.string.search_preset_last_30_days,
                startIso = DateSearchParser.daysAgoIso(29),
                endIso = todayIso
            ),
            DateRangePreset(
                id = "this_month",
                labelRes = R.string.search_preset_this_month,
                startIso = DateSearchParser.startOfCurrentMonthIso(),
                endIso = todayIso
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Keyword & Smart Date Search Input
        Column(modifier = Modifier.fillMaxWidth()) {
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
                            onClick = onOpenExactDatePicker,
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

            AnimatedVisibility(visible = firestoreSearchState is FirestoreSearchState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .height(2.5.dp)
                        .testTag("firestore_search_loading")
                )
            }
        }

        // 2. Date Range Filter Bar (Custom From/To Pickers + 1-Tap Presets)
        val (effectiveStart, effectiveEnd) = filterState.effectiveDateRange
        val startDisplay = if (filterState.hasActiveDateRange && effectiveStart != null) {
            "From: ${DateSearchParser.formatSlashDate(effectiveStart)}"
        } else {
            stringResource(R.string.search_from_date)
        }
        val endDisplay = if (filterState.hasActiveDateRange && effectiveEnd != null) {
            "To: ${DateSearchParser.formatSlashDate(effectiveEnd)}"
        } else {
            stringResource(R.string.search_to_date)
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
                onClick = onOpenRangeStartPicker,
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
                onClick = onOpenRangeEndPicker,
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

            presets.forEach { preset ->
                val isSelected = filterState.rangeStartDateIso == preset.startIso &&
                    filterState.rangeEndDateIso == preset.endIso
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            onClearDateRange()
                        } else {
                            onSelectDateRange(preset.startIso, preset.endIso)
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(preset.labelRes),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.testTag("date_preset_${preset.id}")
                )
            }

            if (filterState.hasExplicitRange) {
                AssistChip(
                    onClick = onClearDateRange,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.search_clear_range),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.search_clear_range),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.testTag("vault_clear_range_chip")
                )
            }
        }

        // 3. Non-Blocking Cloud Search Error Banner (with Retry)
        AnimatedVisibility(visible = firestoreSearchState is FirestoreSearchState.Error) {
            val errorMsg = (firestoreSearchState as? FirestoreSearchState.Error)?.message ?: ""
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("firestore_search_error_banner"),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Cloud search error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRetryCloudSearch,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("firestore_search_retry_button")
                    ) {
                        Text(
                            text = stringResource(R.string.search_retry),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // 4. Smart Active Date or Date-Range Banner
        val activeDateIso = filterState.activeDateIso
        val showBanner = activeDateIso != null || filterState.hasActiveDateRange
        AnimatedVisibility(visible = showBanner) {
            if (activeDateIso != null) {
                ActiveSingleDateBanner(
                    activeDateIso = activeDateIso,
                    filterState = filterState,
                    firestoreSearchState = firestoreSearchState,
                    displayedDocs = displayedDocs,
                    isExportingPdf = isExportingPdf,
                    onExportDayPdf = onExportDayPdf,
                    onSelectExplicitDate = onSelectExplicitDate,
                    onSelectDateMatchMode = onSelectDateMatchMode,
                    onToggleAlternateDate = onToggleAlternateDate
                )
            } else if (filterState.hasActiveDateRange) {
                ActiveDateRangeBanner(
                    rangeStart = effectiveStart,
                    rangeEnd = effectiveEnd,
                    filterState = filterState,
                    firestoreSearchState = firestoreSearchState,
                    displayedDocs = displayedDocs,
                    isExportingPdf = isExportingPdf,
                    onExportDayPdf = onExportDayPdf,
                    onSelectDateMatchMode = onSelectDateMatchMode,
                    onClear = {
                        if (filterState.parsedQueryDateRange != null) {
                            onClearSearchAndDate()
                        } else {
                            onClearDateRange()
                        }
                    }
                )
            }
        }

        // 5. Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterState.selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text("All ($totalBaseCount)") },
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
}

@Composable
private fun ActiveSingleDateBanner(
    activeDateIso: String,
    filterState: VaultFilterState,
    firestoreSearchState: FirestoreSearchState,
    displayedDocs: List<ScannedDocument>,
    isExportingPdf: Boolean,
    onExportDayPdf: (String, List<ScannedDocument>) -> Unit,
    onSelectExplicitDate: (String?) -> Unit,
    onSelectDateMatchMode: (DateMatchMode) -> Unit,
    onToggleAlternateDate: () -> Unit
) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "DATE FILTER ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (firestoreSearchState is FirestoreSearchState.Success &&
                            firestoreSearchState.filter == filterState
                        ) {
                            CloudQueryStatusPill()
                        }
                    }
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
}

@Composable
private fun ActiveDateRangeBanner(
    rangeStart: String?,
    rangeEnd: String?,
    filterState: VaultFilterState,
    firestoreSearchState: FirestoreSearchState,
    displayedDocs: List<ScannedDocument>,
    isExportingPdf: Boolean,
    onExportDayPdf: (String, List<ScannedDocument>) -> Unit,
    onSelectDateMatchMode: (DateMatchMode) -> Unit,
    onClear: () -> Unit
) {
    val isSingleDayRange = rangeStart != null && rangeStart == rangeEnd
    val singleDate = rangeStart ?: ""
    val rangeLabel = if (isSingleDayRange && singleDate.isNotBlank()) {
        "${DateSearchParser.formatSlashDate(singleDate)} • ${DateSearchParser.formatFriendlyDate(singleDate)}"
    } else {
        buildString {
            append(rangeStart?.let { DateSearchParser.formatSlashDate(it) } ?: "Any")
            append("  →  ")
            append(rangeEnd?.let { DateSearchParser.formatSlashDate(it) } ?: "Any")
        }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isSingleDayRange) "DATE FILTER ACTIVE" else "DATE RANGE FILTER ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (firestoreSearchState is FirestoreSearchState.Success &&
                            firestoreSearchState.filter == filterState
                        ) {
                            CloudQueryStatusPill()
                        }
                    }
                    Text(
                        text = rangeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSingleDayRange && singleDate.isNotBlank() && displayedDocs.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onExportDayPdf(singleDate, displayedDocs) },
                            enabled = !isExportingPdf,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("vault_range_export_day_pdf")
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
                    IconButton(
                        onClick = onClear,
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

@Composable
private fun CloudQueryStatusPill() {
    Surface(
        color = VerifiedTeal.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = stringResource(R.string.search_cloud_query_active),
                tint = VerifiedTeal,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Cloud",
                style = MaterialTheme.typography.labelSmall,
                color = VerifiedTeal
            )
        }
    }
}
