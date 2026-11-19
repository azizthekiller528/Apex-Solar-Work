package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class DocumentCategory(val displayName: String, val shortLabel: String) {
    MATERIAL_SLIP("Material Requisition Slip", "Material Slip"),
    INSPECTION_PROTOCOL("Inspection Protocol", "Inspection"),
    DELIVERY_CHALLAN("Delivery Challan", "Challan"),
    INVOICE_RECEIPT("Invoice / Receipt", "Invoice"),
    SITE_REPORT("Site Report / Log", "Site Report"),
    OTHER("General Document", "Document");

    companion object {
        fun fromRaw(raw: String?): DocumentCategory {
            if (raw.isNullOrBlank()) return OTHER
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: OTHER
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ScannedDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = DocumentCategory.OTHER.name,
    val documentDateIso: String = "",
    val filedDateIso: String = "",
    val referenceNumber: String = "",
    val organization: String = "",
    val contractorOrParty: String = "",
    val summary: String = "",
    val extractedFields: List<String> = emptyList(),
    val ocrFullText: String = "",
    val localImageFileName: String = "",
    val localPdfFileName: String = "",
    val previewBase64: String = "",
    val pageCount: Long = 1L,
    val starred: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: DocumentCategory
        get() = DocumentCategory.fromRaw(category)

    val effectiveSortDate: String
        get() = filedDateIso.ifBlank { documentDateIso }
}

data class OcrExtractionResult(
    val title: String,
    val category: DocumentCategory,
    val documentDateIso: String,
    val referenceNumber: String,
    val organization: String,
    val contractorOrParty: String,
    val summary: String,
    val extractedFields: List<String>,
    val ocrFullText: String
)

enum class DateMatchMode(val label: String) {
    EITHER("Filed or Doc Date"),
    FILED_ONLY("Filed Date"),
    DOCUMENT_ONLY("Document Date")
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
