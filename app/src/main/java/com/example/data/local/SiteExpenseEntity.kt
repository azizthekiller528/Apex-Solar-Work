package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.SiteExpense

@Entity(tableName = "site_expenses")
data class SiteExpenseEntity(
    @PrimaryKey val id: String,
    val dateIso: String,
    val title: String,
    val amount: Double,
    val paidBy: String,
    val quantityNote: String,
    val category: String,
    val createdAtMillis: Long
) {
    fun toModel(): SiteExpense = SiteExpense(
        id = id,
        dateIso = dateIso,
        title = title,
        amount = amount,
        paidBy = paidBy,
        quantityNote = quantityNote,
        category = category,
        createdAtMillis = createdAtMillis
    )

    companion object {
        fun fromModel(model: SiteExpense): SiteExpenseEntity = SiteExpenseEntity(
            id = model.id,
            dateIso = model.dateIso,
            title = model.title,
            amount = model.amount,
            paidBy = model.paidBy,
            quantityNote = model.quantityNote,
            category = model.category,
            createdAtMillis = model.createdAtMillis
        )
    }
}
