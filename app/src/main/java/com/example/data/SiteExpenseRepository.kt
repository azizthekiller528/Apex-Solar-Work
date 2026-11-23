package com.example.data

import com.example.data.local.SiteExpenseDao
import com.example.data.local.SiteExpenseEntity
import com.example.data.local.SiteMaterialRecordEntity
import com.example.data.model.SiteExpense
import com.example.data.model.SiteMaterialRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class SiteExpenseRepository(
    private val dao: SiteExpenseDao
) {
    fun allExpensesFlow(): Flow<List<SiteExpense>> =
        dao.getAllExpensesFlow().map { list -> list.map { it.toModel() } }

    fun allMaterialsFlow(): Flow<List<SiteMaterialRecord>> =
        dao.getAllMaterialsFlow().map { list -> list.map { it.toModel() } }

    suspend fun addExpense(expense: SiteExpense) = withContext(Dispatchers.IO) {
        val toSave = if (expense.id.isBlank()) expense.copy(id = UUID.randomUUID().toString()) else expense
        dao.insertExpense(SiteExpenseEntity.fromModel(toSave))
    }

    suspend fun deleteExpense(id: String) = withContext(Dispatchers.IO) {
        dao.deleteExpense(id)
    }

    suspend fun addMaterial(record: SiteMaterialRecord) = withContext(Dispatchers.IO) {
        val toSave = if (record.id.isBlank()) record.copy(id = UUID.randomUUID().toString()) else record
        dao.insertMaterial(SiteMaterialRecordEntity.fromModel(toSave))
    }

    suspend fun deleteMaterial(id: String) = withContext(Dispatchers.IO) {
        dao.deleteMaterial(id)
    }

    /**
     * Seeds initial expenses and site material stock from real site logs if database is empty.
     */
    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.getCount() == 0) {
            val seedExpenses = listOf(
                // 20/11/2026 - Today's Expense (Image 2)
                SiteExpense("exp_20_1", "2026-11-20", "Driver Breakfast, Lunch & Drink", 120.0, "Aziz", "", "FOOD"),
                SiteExpense("exp_20_2", "2026-11-20", "Iron Wire", 74.0, "Aziz", "1 kg", "HARDWARE"),
                SiteExpense("exp_20_3", "2026-11-20", "Sariya Ring", 333.0, "Aziz", "111 pcs", "HARDWARE"),
                SiteExpense("exp_20_4", "2026-11-20", "Sariya Ring", 75.0, "Aziz", "25 pcs from Store", "HARDWARE"),
                SiteExpense("exp_20_5", "2026-11-20", "Diesel", 1000.0, "Aziz", "", "FUEL"),

                // 19/11/2026 - Yesterday's Expense (Image 1)
                SiteExpense("exp_19_1", "2026-11-19", "Driver Breakfast & Lunch", 90.0, "Aziz", "", "FOOD"),
                SiteExpense("exp_19_2", "2026-11-19", "Diesel", 1000.0, "Aziz", "", "FUEL"),

                // 18/11/2026 - Yesterday's Expense (Image 3)
                SiteExpense("exp_18_1", "2026-11-18", "Driver Breakfast", 70.0, "Aziz", "", "FOOD"),
                SiteExpense("exp_18_2", "2026-11-18", "Driver Lunch", 100.0, "Aziz", "", "FOOD"),
                SiteExpense("exp_18_3", "2026-11-18", "Diesel", 1500.0, "Aziz", "", "FUEL"),
                SiteExpense("exp_18_4", "2026-11-18", "Petrol", 300.0, "Aziz", "", "FUEL"),

                // 17/11/2026 - Today Expense (Image 5 - with Aziz and Nadeem split!)
                SiteExpense("exp_17_1", "2026-11-17", "Diesel", 1000.0, "Aziz", "", "FUEL"),
                SiteExpense("exp_17_2", "2026-11-17", "Augr Machine Repair", 100.0, "Nadeem", "", "REPAIR"),
                SiteExpense("exp_17_3", "2026-11-17", "Pickup Welding", 100.0, "Nadeem", "", "REPAIR"),
                SiteExpense("exp_17_4", "2026-11-17", "Chuna", 30.0, "Aziz", "2 kg", "OTHER"),
                SiteExpense("exp_17_5", "2026-11-17", "Lock and Key", 50.0, "Aziz", "", "HARDWARE"),
                SiteExpense("exp_17_6", "2026-11-17", "Driver Lunch", 140.0, "Aziz", "", "FOOD"),

                // 16/11/2026 - Yesterday's Expense (Image 6)
                SiteExpense("exp_16_1", "2026-11-16", "Driver Lunch", 60.0, "Aziz", "", "FOOD"),
                SiteExpense("exp_16_2", "2026-11-16", "Labour Chai Pani", 50.0, "Aziz", "", "FOOD"),
                SiteExpense("exp_16_3", "2026-11-16", "Diesel", 500.0, "Aziz", "", "FUEL"),
                SiteExpense("exp_16_4", "2026-11-16", "Sariya and Wire", 790.0, "Aziz", "1 kg Total", "HARDWARE"),
                SiteExpense("exp_16_5", "2026-11-16", "Sariya Ring Cutting", 88.0, "Aziz", "", "HARDWARE")
            )
            dao.insertExpenses(seedExpenses.map { SiteExpenseEntity.fromModel(it) })
        }

        if (dao.getMaterialCount() == 0) {
            val seedMaterials = listOf(
                // 18/11/2026 - Site Materials In/Out (Image 4)
                SiteMaterialRecord("mat_18_1", "2026-11-18", 1, "J Bolt", "96", "53", ""),
                SiteMaterialRecord("mat_18_2", "2026-11-18", 2, "Heavy Hex Nut", "192", "", ""),
                SiteMaterialRecord("mat_18_3", "2026-11-18", 3, "Cement", "8", "0", ""),
                SiteMaterialRecord("mat_18_4", "2026-11-18", 4, "Pole", "7", "27", ""),
                SiteMaterialRecord("mat_18_5", "2026-11-18", 5, "Sariya", "2 Half / 2 U Full = 4", "", "")
            )
            dao.insertMaterials(seedMaterials.map { SiteMaterialRecordEntity.fromModel(it) })
        }
    }

    /**
     * Builds clean WhatsApp formatted report text.
     */
    fun formatWhatsAppReport(
        dateFormatted: String,
        expenses: List<SiteExpense>,
        materials: List<SiteMaterialRecord>
    ): String {
        val sb = StringBuilder()
        sb.append("📋 *APEX SOLAR WORK - DAILY SITE REPORT*\n")
        sb.append("📅 *Date:* $dateFormatted\n\n")

        if (expenses.isNotEmpty()) {
            sb.append("💰 *EXPENSES (DAILY KHARCHA)*\n")
            var total = 0.0
            val payerTotals = mutableMapOf<String, Double>()

            expenses.forEachIndexed { i, exp ->
                val qtyStr = if (exp.quantityNote.isNotBlank()) " (${exp.quantityNote})" else ""
                val payerStr = if (exp.paidBy.isNotBlank()) " [Paid by ${exp.paidBy}]" else ""
                val amtInt = exp.amount.toInt()
                sb.append("${i + 1}. ${exp.title}$qtyStr — *₹$amtInt*$payerStr\n")
                total += exp.amount
                payerTotals[exp.paidBy] = (payerTotals[exp.paidBy] ?: 0.0) + exp.amount
            }

            sb.append("\n━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("💵 *TOTAL EXPENSE: ₹${total.toInt()}*\n")
            payerTotals.forEach { (payer, amt) ->
                val payerLabel = if (payer.isNotBlank()) "Paid by $payer" else "Other"
                sb.append("👤 *$payerLabel:* ₹${amt.toInt()}\n")
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")
        }

        if (materials.isNotEmpty()) {
            sb.append("📦 *SITE MATERIALS IN / OUT RECORD*\n")
            materials.forEach { mat ->
                val inStoreStr = if (mat.qtyInStore.isNotBlank()) " | Left in Store: ${mat.qtyInStore}" else ""
                sb.append("${mat.sNo}. *${mat.itemName}* — OUT: ${mat.qtyOut}$inStoreStr\n")
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        }

        return sb.toString().trim()
    }
}
