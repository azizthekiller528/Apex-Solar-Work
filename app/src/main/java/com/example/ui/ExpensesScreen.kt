package com.example.ui

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SiteExpense
import com.example.data.model.SiteMaterialRecord
import java.text.SimpleDateFormat
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
    onDeleteExpense: (String) -> Unit,
    onAddMaterial: (SiteMaterialRecord) -> Unit,
    onDeleteMaterial: (String) -> Unit,
    onShareReport: (context: Context, dateFormatted: String, expenses: List<SiteExpense>, materials: List<SiteMaterialRecord>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Kharcha (Expenses), 1 = Materials (Stock)
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddMaterialDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Collect all unique dates from expenses and materials, sorted descending
    val availableDates = remember(allExpenses, allMaterials) {
        val dates = (allExpenses.map { it.dateIso } + allMaterials.map { it.dateIso })
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()
        if (dates.isEmpty()) listOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) else dates
    }

    // Determine current effective date
    val effectiveDateIso = if (selectedDateIso.isNotBlank() && selectedDateIso in availableDates) {
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Date Selector Chips Row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                    ) {
                        items(availableDates) { dateIso ->
                            val isSelected = dateIso == effectiveDateIso
                            val chipLabel = formatChipDate(dateIso)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectDate(dateIso) },
                                label = {
                                    Text(
                                        text = chipLabel,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        item {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.height(32.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Pick Date",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pick Date", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sub Tabs: Daily Kharcha vs Site Materials
                    TabRow(
                        selectedTabIndex = activeSubTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = activeSubTab == 0,
                            onClick = { activeSubTab = 0 },
                            text = {
                                Text(
                                    "💰 Daily Kharcha (${currentDayExpenses.size})",
                                    fontWeight = if (activeSubTab == 0) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                        Tab(
                            selected = activeSubTab == 1,
                            onClick = { activeSubTab = 1 },
                            text = {
                                Text(
                                    "📦 Materials In/Out (${currentDayMaterials.size})",
                                    fontWeight = if (activeSubTab == 1) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }

            // Body Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 88.dp)
            ) {
                // Top Banner with Date & 1-Tap WhatsApp Share Button
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formattedDateHeader,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Apex Solar Site Log • $effectiveDateIso",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 1-Tap WhatsApp Share
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
                                    containerColor = Color(0xFF25D366),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (activeSubTab == 0) {
                    // Total Expense Card (Glowing Neon Clipboard Header)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TOTAL EXPENSE",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "₹ ${totalExpenseAmount.toInt()}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (payerBreakdown.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        payerBreakdown.forEach { (payer, amt) ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                    RoundedCornerShape(8.dp)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "₹ ${amt.toInt()}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
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
                                message = "No daily expenses recorded for $effectiveDateIso. Tap '+ Add Expense' below."
                            )
                        }
                    } else {
                        items(currentDayExpenses, key = { it.id }) { expense ->
                            ExpenseItemRow(
                                expense = expense,
                                onDelete = { onDeleteExpense(expense.id) }
                            )
                        }
                    }
                } else {
                    // Materials Stock Register
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                message = "No material stock movements logged for $effectiveDateIso. Tap '+ Add Material' below."
                            )
                        }
                    } else {
                        items(currentDayMaterials, key = { it.id }) { mat ->
                            MaterialItemRow(
                                material = mat,
                                onDelete = { onDeleteMaterial(mat.id) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = {
                if (activeSubTab == 0) {
                    showAddExpenseDialog = true
                } else {
                    showAddMaterialDialog = true
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("expenses_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeSubTab == 0) "Add Expense" else "Add Material",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            defaultDateIso = effectiveDateIso,
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { newExpense ->
                onAddExpense(newExpense)
                showAddExpenseDialog = false
            }
        )
    }

    // Add Material Dialog
    if (showAddMaterialDialog) {
        val nextSNo = (currentDayMaterials.maxOfOrNull { it.sNo } ?: 0) + 1
        AddMaterialDialog(
            defaultDateIso = effectiveDateIso,
            defaultSNo = nextSNo,
            onDismiss = { showAddMaterialDialog = false },
            onConfirm = { newMat ->
                onAddMaterial(newMat)
                showAddMaterialDialog = false
            }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
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

@Composable
private fun ExpenseItemRow(
    expense: SiteExpense,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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
                    "FUEL" -> Color(0xFFEF5350).copy(alpha = 0.15f)
                    "FOOD" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                    "HARDWARE" -> Color(0xFF2196F3).copy(alpha = 0.15f)
                    "REPAIR" -> Color(0xFF9C27B0).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.secondaryContainer
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
                            "FUEL" -> Color(0xFFD32F2F)
                            "FOOD" -> Color(0xFFF57C00)
                            "HARDWARE" -> Color(0xFF1976D2)
                            "REPAIR" -> Color(0xFF7B1FA2)
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (expense.quantityNote.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = expense.quantityNote,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val payer = if (expense.paidBy.isBlank()) "Aziz" else expense.paidBy
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Paid by $payer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            Text(
                text = "₹ ${expense.amount.toInt()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MaterialItemRow(
    material: SiteMaterialRecord,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // S.No
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${material.sNo}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.dp, Color(0xFFEF9A9A), RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = "OUT: ${material.qtyOut}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Left in Store badge
                    if (material.qtyInStore.isNotBlank()) {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = "STORE: ${material.qtyInStore}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateNotice(title: String, message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
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

@Composable
private fun AddExpenseDialog(
    defaultDateIso: String,
    onDismiss: () -> Unit,
    onConfirm: (SiteExpense) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var quantityNote by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf("Aziz") }
    var category by remember { mutableStateOf("FUEL") }

    val quickTitles = listOf("Diesel", "Driver Lunch", "Driver Breakfast", "Iron Wire", "Sariya Ring", "Machine Repair", "Lock and Key")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Daily Expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Aziz", "Nadeem", "Company").forEach { payer ->
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
                                id = "",
                                dateIso = defaultDateIso,
                                title = title.trim(),
                                amount = amt,
                                paidBy = paidBy,
                                quantityNote = quantityNote.trim(),
                                category = category
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
}

@Composable
private fun AddMaterialDialog(
    defaultDateIso: String,
    defaultSNo: Int,
    onDismiss: () -> Unit,
    onConfirm: (SiteMaterialRecord) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var qtyOut by remember { mutableStateOf("") }
    var qtyInStore by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val quickItems = listOf("J Bolt", "Heavy Hex Nut", "Cement", "Pole", "Sariya", "Rafter 3350", "Bracing 1108")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Material Record", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                id = "",
                                dateIso = defaultDateIso,
                                sNo = defaultSNo,
                                itemName = itemName.trim(),
                                qtyOut = qtyOut.trim(),
                                qtyInStore = qtyInStore.trim(),
                                remarks = notes.trim()
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
        val yesterday = parser.format(Date(System.currentTimeMillis() - 86400000L))
        when (iso) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> SimpleDateFormat("dd MMM", Locale.US).format(parsed)
        }
    } catch (_: Exception) {
        iso
    }
}
