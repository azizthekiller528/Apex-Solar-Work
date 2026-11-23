package com.example.data.model

data class SiteExpense(
    val id: String = "",
    val dateIso: String = "", // e.g. "2026-11-20"
    val title: String = "",   // e.g. "Diesel", "Driver Breakfast & Lunch"
    val amount: Double = 0.0, // e.g. 1000.0
    val paidBy: String = "Aziz", // e.g. "Aziz", "Nadeem", "Office"
    val quantityNote: String = "", // e.g. "1 kg", "111 pcs", "from Store"
    val category: String = "FUEL", // "FUEL", "FOOD", "HARDWARE", "REPAIR", "OTHER"
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class SiteMaterialRecord(
    val id: String = "",
    val dateIso: String = "", // e.g. "2026-11-18"
    val sNo: Int = 1,
    val itemName: String = "", // e.g. "J Bolt", "Cement", "Pole"
    val qtyOut: String = "",   // e.g. "96", "8", "2 Half / 2 U Full = 4"
    val qtyInStore: String = "", // e.g. "53", "0", "27"
    val remarks: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)
