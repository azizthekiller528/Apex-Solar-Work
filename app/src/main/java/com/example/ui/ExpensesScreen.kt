package com.example.ui

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SiteExpense
import com.example.data.model.SiteMaterialRecord
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidGlassChip
import com.example.ui.theme.LiquidGlassPillButton
import com.example.ui.theme.LiquidGlassSegmentedTabs
import com.example.ui.theme.LiquidGlassTone
import com.example.ui.theme.liquidGlassSurface
import com.example.ui.theme.liquidSquircleShape
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    allExpenses: List<SiteExpense>,
    allMaterials: List<SiteMaterialRecord>,
    selectedDateIso: String,
    onSelectDate: (String) -> Unit,
    onAddExpense: (SiteExpense) -> Unit,
    onDeleteExpense: (id: String, dateIso: String) -> Unit,
    onAddMaterial: (SiteMaterialRecord) -> Unit,
    onDeleteMaterial: (id: String, dateIso: String) -> Unit,
    onShareReport: (context: Context, dateFormatted: String, expenses: List<SiteExpense>, materials: List<SiteMaterialRecord>) -> Unit,
    modifier: Modifier = Modifier,
    onUpdateExpense: (previousDateIso: String, SiteExpense) -> Unit = { _, exp -> onAddExpense(exp) },
    onUpdateMaterial: (previousDateIso: String, SiteMaterialRecord) -> Unit = { _, mat -> onAddMaterial(mat) },
    topContentPadding: Dp = 6.dp,
    bottomContentPadding: Dp = 104.dp
) {
    val context = LocalContext.current
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Kharcha (Expenses), 1 = Materials (Stock)
    var showExpenseEditorDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<SiteExpense?>(null) }
    var showMaterialEditorDialog by remember { mutableStateOf(false) }
    var editingMaterial by remember { mutableStateOf<SiteMaterialRecord?>(null) }
    var pendingDeleteExpense by remember { mutableStateOf<SiteExpense?>(null) }
    var pendingDeleteMaterial by remember { mutableStateOf<SiteMaterialRecord?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Collect all unique dates from expenses and materials plus selected date, sorted descending
    val availableDates = remember(allExpenses, allMaterials, selectedDateIso) {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        (listOf(todayIso) + allExpenses.map { it.dateIso } + allMaterials.map { it.dateIso } + (if (selectedDateIso.isNotBlank()) listOf(selectedDateIso) else emptyList()))
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()
    }

    // Determine current effective date
    val effectiveDateIso = if (selectedDateIso.isNotBlank()) {
        selectedDateIso
    } else {
        availableDates.firstOrNull() ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // Filter data for effective date
    val currentDayExpenses = remember(allExpenses, effectiveDateIso) {
        allExpenses.filter { it.dateIso == effectiveDateIso }
    }
    val currentDayMaterials = remember(allMaterials, effectiveDateIso) {
        allMaterials.filter { it.dateIso == effectiveDateIso }
    }

    val totalExpenseAmount = remember(currentDayExpenses) {
        currentDayExpenses.sumOf { it.amount }
    }

    val payerBreakdown = remember(currentDayExpenses) {
        currentDayExpenses.groupBy { if (it.paidBy.isBlank()) "Aziz" else it.paidBy }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val formattedDateHeader = remember(effectiveDateIso) {
        formatIsoDateHeader(effectiveDateIso)
    }

    val dateListState = rememberLazyListState()
    LaunchedEffect(effectiveDateIso, availableDates) {
        val index = availableDates.indexOf(effectiveDateIso)
        if (index >= 0) {
            dateListState.animateScrollToItem(index)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Frosted Glass Date Selector Pills + iOS Segmented Switcher + Inline Actions (Zero Card Overlap)
            LiquidGlassCard(
                shape = liquidSquircleShape(24.dp),
                tone = LiquidGlassTone.DOCK,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = topContentPadding, bottom = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        state = dateListState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        items(availableDates) { dateIso ->
                            val isSelected = dateIso == effectiveDateIso
                            val chipLabel = formatChipDate(dateIso)
                            LiquidGlassChip(
                                selected = isSelected,
                                onClick = { onSelectDate(dateIso) },
                                label = chipLabel
                            )
                        }

                        item {
                            LiquidGlassChip(
                                selected = false,
                                onClick = { showDatePicker = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Pick Date",
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = "Pick Date"
                            )
                        }
                    }

                    // iOS Segmented Glass Switcher: concise labels prevent narrow-phone truncation
                    LiquidGlassSegmentedTabs(
                        tabs = listOf(
                            "💰 Kharcha (${currentDayExpenses.size})",
                            "📦 Materials (${currentDayMaterials.size})"
                        ),
                        selectedIndex = activeSubTab,
                        onSelect = { activeSubTab = it },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidGlassPillButton(
                            onClick = {
                                if (activeSubTab == 0) {
                                    editingExpense = null
                                    showExpenseEditorDialog = true
                                } else {
                                    editingMaterial = null
                                    showMaterialEditorDialog = true
                                }
                            },
                            tone = LiquidGlassTone.ACCENT,
                            elevation = 6.dp,
                            backdrop = null,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("expenses_fab")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeSubTab == 0) "Add Expense" else "Add Material",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                onShareReport(
                                    context,
                                    formattedDateHeader,
                                    currentDayExpenses,
                                    currentDayMaterials
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF168A3E),
                                contentColor = Color.White
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WhatsApp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Body Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding)
            ) {
                // Date Subtitle Card
                item {
                    LiquidGlassCard(
                        shape = liquidSquircleShape(20.dp),
                        tone = LiquidGlassTone.SUBTLE,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedDateHeader,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = effectiveDateIso,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (activeSubTab == 0) {
                    // Total Expense Summary Card
                    item {
                        LiquidGlassCard(
                            shape = liquidSquircleShape(24.dp),
                            tone = LiquidGlassTone.CARD,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TOTAL EXPENSE",
                                        style = MaterialTheme.typography.labelMedium,
                                        letterSpacing = 0.8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "₹ ${formatInr(totalExpenseAmount.toInt())}",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (payerBreakdown.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        payerBreakdown.forEach { (payer, amt) ->
                                            Box(
                                                modifier = Modifier
                                                    .liquidGlassSurface(
                                                        shape = CircleShape,
                                                        tone = LiquidGlassTone.SUBTLE
                                                    )
                                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "$payer: ",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "₹ ${formatInr(amt.toInt())}",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = JetBrainsMonoFontFamily,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (currentDayExpenses.isEmpty()) {
                        item {
                            EmptyStateNotice(
                                title = "No Expenses Logged",
                                message = "No daily expenses recorded for $effectiveDateIso. Tap '+ Add Expense' above."
                            )
                        }
                    } else {
                        items(currentDayExpenses, key = { it.id }) { expense ->
                            ExpenseItemRow(
                                expense = expense,
                                onEdit = {
                                    editingExpense = expense
                                    showExpenseEditorDialog = true
                                },
                                onDelete = { pendingDeleteExpense = expense }
                            )
                        }
                    }
                } else {
                    // Materials Stock Register
                    item {
                        LiquidGlassCard(
                            shape = liquidSquircleShape(20.dp),
                            tone = LiquidGlassTone.SUBTLE,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SITE MATERIAL REGISTER",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${currentDayMaterials.size} Items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (currentDayMaterials.isEmpty()) {
                        item {
                            EmptyStateNotice(
                                title = "No Materials Recorded",
                                message = "No material stock movements logged for $effectiveDateIso. Tap '+ Add Material' above."
                            )
                        }
                    } else {
                        items(currentDayMaterials, key = { it.id }) { mat ->
                            MaterialItemRow(
                                material = mat,
                                onEdit = {
                                    editingMaterial = mat
                                    showMaterialEditorDialog = true
                                },
                                onDelete = { pendingDeleteMaterial = mat }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Expense Dialog
    if (showExpenseEditorDialog) {
        val currentEdit = editingExpense
        ExpenseEditorDialog(
            initial = currentEdit,
            defaultDateIso = effectiveDateIso,
            onDismiss = {
                showExpenseEditorDialog = false
                editingExpense = null
            },
            onConfirm = { savedExpense ->
                val previousDate = currentEdit?.dateIso ?: ""
                showExpenseEditorDialog = false
                editingExpense = null
                if (currentEdit == null) {
                    onAddExpense(savedExpense)
                } else {
                    onUpdateExpense(previousDate, savedExpense)
                }
                onSelectDate(savedExpense.dateIso)
            }
        )
    }

    // Add / Edit Material Dialog
    if (showMaterialEditorDialog) {
        val currentEdit = editingMaterial
        val nextSNo = (currentDayMaterials.maxOfOrNull { it.sNo } ?: 0) + 1
        MaterialEditorDialog(
            initial = currentEdit,
            defaultDateIso = effectiveDateIso,
            defaultSNo = nextSNo,
            onDismiss = {
                showMaterialEditorDialog = false
                editingMaterial = null
            },
            onConfirm = { savedMat ->
                val previousDate = currentEdit?.dateIso ?: ""
                showMaterialEditorDialog = false
                editingMaterial = null
                if (currentEdit == null) {
                    onAddMaterial(savedMat)
                } else {
                    onUpdateMaterial(previousDate, savedMat)
                }
                onSelectDate(savedMat.dateIso)
            }
        )
    }

    pendingDeleteExpense?.let { expToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteExpense = null },
            title = { Text("Delete expense?") },
            text = {
                Text(
                    "Remove \"${expToDelete.title}\" (₹ ${expToDelete.amount.toInt()}) from ${formatChipDate(expToDelete.dateIso)}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteExpense = null
                        onDeleteExpense(expToDelete.id, expToDelete.dateIso)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteExpense = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingDeleteMaterial?.let { matToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMaterial = null },
            title = { Text("Delete material record?") },
            text = {
                Text(
                    "Remove \"${matToDelete.itemName}\" (OUT: ${matToDelete.qtyOut}) from ${formatChipDate(matToDelete.dateIso)}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteMaterial = null
                        onDeleteMaterial(matToDelete.id, matToDelete.dateIso)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMaterial = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val initialMillis = remember(effectiveDateIso) {
            com.example.util.DateSearchParser.isoToUtcMillis(effectiveDateIso)
                ?: com.example.util.DateSearchParser.isoToUtcMillis(com.example.util.DateSearchParser.todayIso())
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val iso = com.example.util.DateSearchParser.utcMillisToIso(millis)
                            onSelectDate(iso)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatInr(amount: Int): String =
    NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)

@Composable
private fun ExpenseItemRow(
    expense: SiteExpense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LiquidGlassCard(
        shape = RoundedCornerShape(22.dp),
        tone = LiquidGlassTone.CARD,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                color = when (expense.category) {
                    "FUEL" -> Color(0xFFEF5350).copy(alpha = 0.16f)
                    "FOOD" -> Color(0xFFFF9800).copy(alpha = 0.16f)
                    "HARDWARE" -> Color(0xFF2196F3).copy(alpha = 0.16f)
                    "REPAIR" -> Color(0xFF9C27B0).copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = when (expense.category) {
                            "FUEL" -> Color(0xFFEF4444)
                            "FOOD" -> Color(0xFFF59E0B)
                            "HARDWARE" -> MaterialTheme.colorScheme.primary
                            "REPAIR" -> Color(0xFFA855F7)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details (untruncated so long items/notes are fully readable)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (expense.quantityNote.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .liquidGlassSurface(shape = CircleShape, tone = LiquidGlassTone.SUBTLE)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = expense.quantityNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val payer = if (expense.paidBy.isBlank()) "Aziz" else expense.paidBy
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Paid by $payer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount + Edit/Delete stacked so left title keeps generous width on phones
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "₹ ${formatInr(expense.amount.toInt())}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit expense",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete expense",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialItemRow(
    material: SiteMaterialRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LiquidGlassCard(
        shape = RoundedCornerShape(22.dp),
        tone = LiquidGlassTone.CARD,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // S.No
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .liquidGlassSurface(shape = CircleShape, tone = LiquidGlassTone.SUBTLE),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${material.sNo}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Item Name & Notes
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = material.itemName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (material.remarks.isNotBlank()) {
                    Text(
                        text = material.remarks,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Out badge
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "OUT: ${material.qtyOut}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Left in Store badge
                    if (material.qtyInStore.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "STORE: ${material.qtyInStore}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit material",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete material",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateNotice(title: String, message: String) {
    LiquidGlassCard(
        shape = RoundedCornerShape(24.dp),
        tone = LiquidGlassTone.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Inventory,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditorDialog(
    initial: SiteExpense?,
    defaultDateIso: String,
    onDismiss: () -> Unit,
    onConfirm: (SiteExpense) -> Unit
) {
    var expenseDateIso by remember { mutableStateOf(initial?.dateIso?.ifBlank { defaultDateIso } ?: defaultDateIso) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amountText by remember {
        mutableStateOf(
            initial?.amount?.let { amt ->
                if (amt % 1.0 == 0.0) {
                    amt.toLong().toString()
                } else {
                    java.math.BigDecimal.valueOf(amt).stripTrailingZeros().toPlainString()
                }
            } ?: ""
        )
    }
    var quantityNote by remember { mutableStateOf(initial?.quantityNote ?: "") }
    var paidBy by remember { mutableStateOf(initial?.paidBy?.ifBlank { "Aziz" } ?: "Aziz") }
    var category by remember { mutableStateOf(initial?.category?.ifBlank { "FUEL" } ?: "FUEL") }

    val quickTitles = listOf("Diesel", "Driver Lunch", "Driver Breakfast", "Iron Wire", "Sariya Ring", "Machine Repair", "Lock and Key")
    val payerOptions = remember(initial?.paidBy) {
        listOfNotNull(initial?.paidBy?.takeIf { it.isNotBlank() }, "Aziz", "Nadeem", "Company").distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "Add Daily Expense" else "Edit Daily Expense",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dedicated Date Selector Row
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Date: " + formatChipDate(expenseDateIso),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = expenseDateIso,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            if (expenseDateIso != todayIso) {
                                OutlinedButton(
                                    onClick = { expenseDateIso = todayIso },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Today", fontSize = 11.sp)
                                }
                            }
                            FilledTonalButton(
                                onClick = { showDatePickerDialog = true },
                                modifier = Modifier.height(30.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Pick Date",
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change", fontSize = 11.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Item") },
                    placeholder = { Text("e.g. Diesel, Sariya Ring") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Quick suggestions chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickTitles) { suggestion ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable {
                                title = suggestion
                                if (suggestion == "Diesel") category = "FUEL"
                                if (suggestion.contains("Lunch") || suggestion.contains("Breakfast")) category = "FOOD"
                                if (suggestion.contains("Repair")) category = "REPAIR"
                                if (suggestion.contains("Ring") || suggestion.contains("Wire")) category = "HARDWARE"
                            }
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("e.g. 1000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = quantityNote,
                    onValueChange = { quantityNote = it },
                    label = { Text("Quantity / Note (Optional)") },
                    placeholder = { Text("e.g. 1 kg, 111 pcs") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Paid By selector
                Text(
                    text = "Paid By",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    payerOptions.forEach { payer ->
                        FilterChip(
                            selected = paidBy == payer,
                            onClick = { paidBy = payer },
                            label = { Text(payer) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amt > 0) {
                        onConfirm(
                            SiteExpense(
                                id = initial?.id ?: "",
                                dateIso = expenseDateIso,
                                title = title.trim(),
                                amount = amt,
                                paidBy = paidBy,
                                quantityNote = quantityNote.trim(),
                                category = category,
                                createdAtMillis = initial?.createdAtMillis ?: System.currentTimeMillis()
                            )
                        )
                    }
                },
                enabled = title.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePickerDialog) {
        val initialMillis = remember(expenseDateIso) {
            com.example.util.DateSearchParser.isoToUtcMillis(expenseDateIso)
                ?: com.example.util.DateSearchParser.isoToUtcMillis(com.example.util.DateSearchParser.todayIso())
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            expenseDateIso = com.example.util.DateSearchParser.utcMillisToIso(millis)
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialEditorDialog(
    initial: SiteMaterialRecord?,
    defaultDateIso: String,
    defaultSNo: Int,
    onDismiss: () -> Unit,
    onConfirm: (SiteMaterialRecord) -> Unit
) {
    var materialDateIso by remember { mutableStateOf(initial?.dateIso?.ifBlank { defaultDateIso } ?: defaultDateIso) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var itemName by remember { mutableStateOf(initial?.itemName ?: "") }
    var qtyOut by remember { mutableStateOf(initial?.qtyOut ?: "") }
    var qtyInStore by remember { mutableStateOf(initial?.qtyInStore ?: "") }
    var notes by remember { mutableStateOf(initial?.remarks ?: "") }

    val quickItems = listOf("J Bolt", "Heavy Hex Nut", "Cement", "Pole", "Sariya", "Rafter 3350", "Bracing 1108")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "Add Material Record" else "Edit Material Record",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dedicated Date Selector Row
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Date: " + formatChipDate(materialDateIso),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = materialDateIso,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            if (materialDateIso != todayIso) {
                                OutlinedButton(
                                    onClick = { materialDateIso = todayIso },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Today", fontSize = 11.sp)
                                }
                            }
                            FilledTonalButton(
                                onClick = { showDatePickerDialog = true },
                                modifier = Modifier.height(30.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Pick Date",
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change", fontSize = 11.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Material / Item Name") },
                    placeholder = { Text("e.g. J Bolt, Cement") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickItems) { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { itemName = item }
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = qtyOut,
                    onValueChange = { qtyOut = it },
                    label = { Text("Quantity OUT (Used/Dispatched)") },
                    placeholder = { Text("e.g. 96, 8 bags") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = qtyInStore,
                    onValueChange = { qtyInStore = it },
                    label = { Text("Left In Store (Balance)") },
                    placeholder = { Text("e.g. 53, 0 left") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Remark / Note (Optional)") },
                    placeholder = { Text("e.g. 2 Half / 2 U Full = 4") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (itemName.isNotBlank() && qtyOut.isNotBlank()) {
                        onConfirm(
                            SiteMaterialRecord(
                                id = initial?.id ?: "",
                                dateIso = materialDateIso,
                                sNo = initial?.sNo ?: defaultSNo,
                                itemName = itemName.trim(),
                                qtyOut = qtyOut.trim(),
                                qtyInStore = qtyInStore.trim(),
                                remarks = notes.trim(),
                                createdAtMillis = initial?.createdAtMillis ?: System.currentTimeMillis()
                            )
                        )
                    }
                },
                enabled = itemName.isNotBlank() && qtyOut.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePickerDialog) {
        val initialMillis = remember(materialDateIso) {
            com.example.util.DateSearchParser.isoToUtcMillis(materialDateIso)
                ?: com.example.util.DateSearchParser.isoToUtcMillis(com.example.util.DateSearchParser.todayIso())
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            materialDateIso = com.example.util.DateSearchParser.utcMillisToIso(millis)
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatIsoDateHeader(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.US)
        val parsed = parser.parse(iso)
        if (parsed != null) formatter.format(parsed) else iso
    } catch (_: Exception) {
        iso
    }
}

private fun formatChipDate(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = parser.parse(iso) ?: return iso
        val today = parser.format(Date())
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterday = parser.format(cal.time)
        when (iso) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> SimpleDateFormat("dd MMM", Locale.US).format(parsed)
        }
    } catch (_: Exception) {
        iso
    }
}
