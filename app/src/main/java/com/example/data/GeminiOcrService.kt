package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.DocumentCategory
import com.example.model.OcrExtractionResult
import com.example.util.DateSearchParser
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiOcrService"

class GeminiOcrService {

    private val ocrSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(
                description = "Concise, descriptive document title including form type and slip/ref number or key item if visible (e.g. 'Material Requisition Slip #6717 — Nut Bolt 8mm' or 'Solar System Inspection Protocol')."
            ),
            "category" to Schema.enumeration(
                values = DocumentCategory.entries.map { it.name },
                description = "Best matching document category."
            ),
            "documentDateIso" to Schema.string(
                description = "Date written or printed on the document normalized to YYYY-MM-DD (note: DD/MM/YYYY format is common on Indian site slips, e.g. 24/02/2026 -> 2026-02-24). Return empty string '' if the Date field on the document is blank or missing."
            ),
            "referenceNumber" to Schema.string(
                description = "Stamped slip number, form number, work order, challan, or invoice reference (e.g. '6717 / FCN-ST-13 (R1)'). Empty string if none."
            ),
            "organization" to Schema.string(
                description = "Issuing company, agency, or project header (e.g. 'Kalpataru Projects International Ltd.' or 'Kalpataru / Jal Jeevan Mission'). Empty string if none."
            ),
            "contractorOrParty" to Schema.string(
                description = "Sub-contractor, department, employee, scheme name, or vendor (e.g. 'Apex Solar — MEP'). Empty string if none."
            ),
            "summary" to Schema.string(
                description = "1-2 sentence concise summary of what this document records, including key materials, quantities, or inspection checklist scope."
            ),
            "extractedFields" to Schema.array(
                items = Schema.string(),
                description = "Up to 20 structured 'Key: Value' entries extracted from printed headers, handwritten ink fields, and table rows (e.g. 'Item 1: Nut Bolt 8mm | Req: 440 | Issued: 440', 'Remarks: Returnable')."
            ),
            "ocrFullText" to Schema.string(
                description = "Complete verbatim OCR transcript of ALL printed text, table headers, checkboxes, stamps, and handwritten ink entries visible in the document photo so every word and number is searchable."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ocrSchema
            }
        )
    }

    suspend fun analyzeDocumentPhoto(bitmap: Bitmap): OcrExtractionResult {
        val prompt = """
            You are an expert OCR and industrial/office document indexing engine.
            Carefully inspect this document photograph—including BOTH printed form tables AND handwritten pen ink (such as pink carbon Material Requisition & Issue Slips, Solar System Inspection Protocols, delivery challans, invoices, and site checklists).
            
            Extraction rules:
            1. Read handwritten dates carefully. On site/office forms, numeric dates like 24/02/2026 or 12/1/2026 use DD/MM/YYYY format—convert to ISO YYYY-MM-DD (e.g. 24/02/2026 -> 2026-02-24). Ignore old printed form-revision dates (like 'Date: 01/07/2017' in a form header) if a newer handwritten transaction Date is present. If the document's Date field is blank, return "" for documentDateIso.
            2. Capture stamped slip numbers (e.g. 6717), form codes (e.g. FCN-ST-13 (R1)), vehicle numbers, contractor/sub-contractor names, department names, scheme names, and every line item with Required/Issued quantities.
            3. In ocrFullText, transcribe ALL legible printed and handwritten text cleanly line-by-line for full-text search and searchable PDF embedding.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            val jsonString = response.text
                ?: throw IllegalStateException("OCR engine returned an empty response.")
            return parseExtractionJson(jsonString)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini OCR timed out after 60s", e)
            throw IllegalStateException("OCR scan timed out after 60 seconds. Please check your connection and try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini OCR failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to scan document with AI OCR.", e)
        }
    }

    internal fun parseExtractionJson(jsonString: String): OcrExtractionResult {
        val json = JSONObject(jsonString)
        val rawTitle = json.optString("title", "").trim().ifBlank { "Scanned Document" }
        val category = DocumentCategory.fromRaw(json.optString("category", "OTHER"))
        val rawDocDate = json.optString("documentDateIso", "").trim()
        val normalizedDate = DateSearchParser.normalizeToIsoOrEmpty(rawDocDate)
        val ref = json.optString("referenceNumber", "").trim()
        val org = json.optString("organization", "").trim()
        val party = json.optString("contractorOrParty", "").trim()
        val summary = json.optString("summary", "").trim()
        val ocrText = json.optString("ocrFullText", "").trim()

        val fieldsArray = json.optJSONArray("extractedFields")
        val fields = buildList {
            if (fieldsArray != null) {
                for (i in 0 until minOf(fieldsArray.length(), 30)) {
                    val item = fieldsArray.optString(i, "").trim()
                    if (item.isNotBlank()) add(item.take(290))
                }
            }
        }

        return OcrExtractionResult(
            title = rawTitle.take(200),
            category = category,
            documentDateIso = normalizedDate,
            referenceNumber = ref.take(120),
            organization = org.take(200),
            contractorOrParty = party.take(200),
            summary = summary.take(1000),
            extractedFields = fields,
            ocrFullText = ocrText.take(50000)
        )
    }
}
