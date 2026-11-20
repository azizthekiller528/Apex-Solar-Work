package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.ScannedDocument
import java.io.File

/**
 * Local Room SQLite cache entity storing scanned document metadata
 * (filename, dates, reference/organization/party fields, local file paths, and searchable OCR content)
 * mirrored from the user's Cloud Firestore vault for fast reactive keyword & date-range queries.
 */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "filedDateIso"]),
        Index(value = ["userId", "documentDateIso"])
    ]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val fileName: String,
    val title: String,
    val category: String,
    val documentDateIso: String,
    val filedDateIso: String,
    val referenceNumber: String = "",
    val organization: String = "",
    val contractorOrParty: String = "",
    val summary: String = "",
    val localImagePath: String,
    val localPdfPath: String,
    val ocrContent: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

fun ScannedDocument.toDocumentEntity(docsDirPath: String): DocumentEntity {
    val resolvedFileName = localPdfFileName.ifBlank {
        localImageFileName.ifBlank { "${id}_searchable.pdf" }
    }
    val resolvedImagePath = if (localImageFileName.isNotBlank() && docsDirPath.isNotBlank()) {
        File(docsDirPath, localImageFileName).absolutePath
    } else {
        localImageFileName
    }
    val resolvedPdfPath = if (localPdfFileName.isNotBlank() && docsDirPath.isNotBlank()) {
        File(docsDirPath, localPdfFileName).absolutePath
    } else {
        localPdfFileName
    }

    return DocumentEntity(
        id = id,
        userId = userId,
        fileName = resolvedFileName,
        title = title,
        category = category,
        documentDateIso = documentDateIso,
        filedDateIso = filedDateIso,
        referenceNumber = referenceNumber,
        organization = organization,
        contractorOrParty = contractorOrParty,
        summary = summary,
        localImagePath = resolvedImagePath,
        localPdfPath = resolvedPdfPath,
        ocrContent = ocrFullText,
        updatedAtMillis = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
    )
}
