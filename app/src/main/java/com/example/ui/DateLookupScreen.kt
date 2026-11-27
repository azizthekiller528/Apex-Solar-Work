package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DateMatchMode
import com.example.model.ScannedDocument
import com.example.ui.components.DocDatePickerDialog
import com.example.ui.components.DocumentLedgerCard
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.VerifiedTeal
import com.example.util.DateSearchParser
import com.example.viewmodel.DocFolioViewModel

@Composable
fun DateLookupScreen(
    allDocuments: List<ScannedDocument>,
    selectedDateIso: String,
    dateMatchMode: DateMatchMode,
    isExportingPdf: Boolean,
    onSelectDate: (String) -> Unit,
    onSelectMatchMode: (DateMatchMode) -> Unit,
    onOpenDocument: (ScannedDocument) -> Unit,
    onToggleStar: (ScannedDocument) -> Unit,
    onSharePdf: (ScannedDocument) -> Unit,
    onExportDayPdf: (String, List<ScannedDocument>) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var jumpInput by remember { mutableStateOf(DateSearchParser.formatSlashDate(selectedDateIso)) }
    var jumpError by remember { mutableStateOf<String?>(null) }
    var alternateIsoOffer by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val initialYm = remember(selectedDateIso) { DateSearchParser.parseYearMonth(selectedDateIso) }
    var displayYear by remember { mutableIntStateOf(initialYm.first) }
    var displayMonth by remember { mutableIntStateOf(initialYm.second) }

    LaunchedEffect(selectedDateIso) {
        val (y, m) = DateSearchParser.parseYearMonth(selectedDateIso)
        displayYear = y
        displayMonth = m
        val currentParsed = DateSearchParser.parseDateQuery(jumpInput)
        if (currentParsed?.primaryIso != selectedDateIso && currentParsed?.alternateIso != selectedDateIso) {
            jumpInput = DateSearchParser.formatSlashDate(selectedDateIso)
            jumpError = null
            alternateIsoOffer = null
        }
    }

    // Map YYYY-MM-DD -> count of documents matching the active mode
    val countByDate = remember(allDocuments, dateMatchMode) {
        val map = mutableMapOf<String, Int>()
        for (doc in allDocuments) {
            val dates = when (dateMatchMode) {
                DateMatchMode.EITHER -> setOf(doc.filedDateIso, doc.documentDateIso).filter { it.isNotBlank() }
                DateMatchMode.FILED_ONLY -> listOf(doc.filedDateIso).filter { it.isNotBlank() }
                DateMatchMode.DOCUMENT_ONLY -> listOf(doc.documentDateIso).filter { it.isNotBlank() }
            }
            for (d in dates) {
                map[d] = (map[d] ?: 0) + 1
            }
        }
        map
    }

    val docsForSelectedDate = remember(allDocuments, selectedDateIso, dateMatchMode) {
        allDocuments.filter {
            DocFolioViewModel.matchesDocumentOnDate(it, selectedDateIso, dateMatchMode)
        }
    }

    val recentDatesWithDocs = remember(countByDate) {
        countByDate.entries
            .sortedByDescending { it.key }
            .take(6)
    }

    fun applyTypedJump() {
        val parsed = DateSearchParser.parseDateQuery(jumpInput)
        if (parsed != null) {
            jumpError = null
            alternateIsoOffer = parsed.alternateIso
            onSelectDate(parsed.primaryIso)
            focusManager.clearFocus()
        } else {
            jumpError = "Enter a valid date like 12/1/2026 or 24/02/2026"
            alternateIsoOffer = null
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quick Boss Date Jump Card
            item(key = "boss_date_jump") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "BOSS DATE LOOKUP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafetyAmber
                                )
                                Text(
                                    text = "Find all documents for any day",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            AssistChip(
                                onClick = {
                                    val today = DateSearchParser.todayIso()
                                    jumpInput = DateSearchParser.formatSlashDate(today)
                                    jumpError = null
                                    alternateIsoOffer = null
                                    onSelectDate(today)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Today,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Today") },
                                modifier = Modifier.testTag("jump_today_chip")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = jumpInput,
                                onValueChange = {
                                    jumpInput = it
                                    jumpError = null
                                },
                                placeholder = { Text("e.g. 12/1/2026 or 24/02/2026") },
                                singleLine = true,
                                isError = jumpError != null,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showDatePicker = true },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = "Open date picker"
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { applyTypedJump() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("date_jump_input")
                            )

                            Button(
                                onClick = { applyTypedJump() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("date_jump_go_button")
                            ) {
                                Text("Go")
                            }
                        }

                        if (jumpError != null) {
                            Text(
                                text = jumpError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        val altIso = alternateIsoOffer
                        if (altIso != null) {
                            AssistChip(
                                onClick = {
                                    val prev = selectedDateIso
                                    onSelectDate(altIso)
                                    alternateIsoOffer = prev
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Switch date interpretation",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text("Did you mean ${DateSearchParser.formatFriendlyDate(altIso)}?")
                                },
                                modifier = Modifier.testTag("date_lookup_alt_chip")
                            )
                        }

                        // Date Match Mode Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DateMatchMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = dateMatchMode == mode,
                                    onClick = { onSelectMatchMode(mode) },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }
                }
            }

            // Active Filing Dates Quick Pills (instant jump to days that have scans!)
            if (recentDatesWithDocs.isNotEmpty()) {
                item(key = "active_dates_strip") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "DATES WITH FILED DOCUMENTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentDatesWithDocs.forEach { (iso, count) ->
                                val isSelected = iso == selectedDateIso
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectDate(iso)
                                        jumpInput = DateSearchParser.formatSlashDate(iso)
                                        jumpError = null
                                        alternateIsoOffer = null
                                    },
                                    label = {
                                        Text(
                                            text = "${DateSearchParser.formatSlashDate(iso)} ($count)",
                                            style = MonoCodeStyle
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Month Filing Calendar
            item(key = "calendar_card") {
                FilingCalendarCard(
                    year = displayYear,
                    month = displayMonth,
                    selectedDateIso = selectedDateIso,
                    countByDate = countByDate,
                    onPreviousMonth = {
                        if (displayMonth == 1) {
                            displayMonth = 12
                            displayYear -= 1
                        } else {
                            displayMonth -= 1
                        }
                    },
                    onNextMonth = {
                        if (displayMonth == 12) {
                            displayMonth = 1
                            displayYear += 1
                        } else {
                            displayMonth += 1
                        }
                    },
                    onSelectDay = { iso ->
                        onSelectDate(iso)
                        jumpInput = DateSearchParser.formatSlashDate(iso)
                        jumpError = null
                        alternateIsoOffer = null
                    }
                )
            }

            // Selected Day Header + Export Day PDF
            item(key = "selected_day_header") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = DateSearchParser.formatFriendlyDate(selectedDateIso),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${DateSearchParser.formatSlashDate(selectedDateIso)} • ${docsForSelectedDate.size} document(s)",
                                style = MonoCodeStyle,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        if (docsForSelectedDate.isNotEmpty()) {
                            Button(
                                onClick = { onExportDayPdf(selectedDateIso, docsForSelectedDate) },
                                enabled = !isExportingPdf,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("export_day_pdf_button")
                            ) {
                                if (isExportingPdf) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.export_day_pdf))
                            }
                        }
                    }
                }
            }

            if (docsForSelectedDate.isEmpty()) {
                item(key = "no_docs_for_day") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No documents recorded on ${DateSearchParser.formatSlashDate(selectedDateIso)}",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Dates with amber dots on the calendar have filed documents.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(items = docsForSelectedDate, key = { it.id }) { doc ->
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

    if (showDatePicker) {
        DocDatePickerDialog(
            initialIsoDate = selectedDateIso,
            title = "Select Filing Date",
            onDismiss = { showDatePicker = false },
            onDateSelected = { iso ->
                onSelectDate(iso)
                jumpInput = DateSearchParser.formatSlashDate(iso)
                jumpError = null
                alternateIsoOffer = null
            }
        )
    }
}

@Composable
private fun FilingCalendarCard(
    year: Int,
    month: Int,
    selectedDateIso: String,
    countByDate: Map<String, Int>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (String) -> Unit
) {
    val daysCount = remember(year, month) { DateSearchParser.daysInMonth(year, month) }
    val startOffset = remember(year, month) { DateSearchParser.firstDayOfWeekOffset(year, month) }
    val todayIso = remember { DateSearchParser.todayIso() }
    val totalCells = startOffset + daysCount
    val weeks = (totalCells + 6) / 7
    val weekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("calendar_prev_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month"
                    )
                }
                Text(
                    text = DateSearchParser.formatMonthYear(year, month),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("calendar_next_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            for (week in 0 until weeks) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (dow in 0..6) {
                        val dayNum = (week * 7 + dow) - startOffset + 1
                        if (dayNum !in 1..daysCount) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val cellIso = DateSearchParser.buildIso(year, month, dayNum)
                            val count = countByDate[cellIso] ?: 0
                            val isSelected = cellIso == selectedDateIso
                            val isToday = cellIso == todayIso

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            count > 0 -> SafetyAmber.copy(alpha = 0.14f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(
                                                1.5.dp,
                                                VerifiedTeal,
                                                RoundedCornerShape(10.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onSelectDay(cellIso) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected || count > 0) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (count > 0) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else SafetyAmber
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
