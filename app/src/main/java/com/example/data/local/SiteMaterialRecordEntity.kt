package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.SiteMaterialRecord

@Entity(tableName = "site_materials")
data class SiteMaterialRecordEntity(
    @PrimaryKey val id: String,
    val dateIso: String,
    val sNo: Int,
    val itemName: String,
    val qtyOut: String,
    val qtyInStore: String,
    val remarks: String,
    val createdAtMillis: Long
) {
    fun toModel(): SiteMaterialRecord = SiteMaterialRecord(
        id = id,
        dateIso = dateIso,
        sNo = sNo,
        itemName = itemName,
        qtyOut = qtyOut,
        qtyInStore = qtyInStore,
        remarks = remarks,
        createdAtMillis = createdAtMillis
    )

    companion object {
        fun fromModel(model: SiteMaterialRecord): SiteMaterialRecordEntity = SiteMaterialRecordEntity(
            id = model.id,
            dateIso = model.dateIso,
            sNo = model.sNo,
            itemName = model.itemName,
            qtyOut = model.qtyOut,
            qtyInStore = model.qtyInStore,
            remarks = model.remarks,
            createdAtMillis = model.createdAtMillis
        )
    }
}
