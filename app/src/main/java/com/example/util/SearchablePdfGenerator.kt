package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object SearchablePdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in PostScript points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in PostScript points
    private const val MARGIN = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2f)

    /**
     * Generates a multi-page searchable PDF:
     * - Page 1: Scanned document photo with header stamp AND invisible selectable OCR text overlay.
     * - Page 2+: Clean, selectable OCR Index Sheet with metadata, key-value fields, and full OCR transcript.
     */
    suspend fun generateDocumentPdf(
        context: Context,
        document: ScannedDocument,
        scanBitmap: Bitmap?,
        targetFileName: String
    ): File = withContext(Dispatchers.IO) {
        val outputFile = File(ImageStorageHelper.getDocsDirectory(context), targetFileName)
        val pdf = PdfDocument()
        try {
            var pageNum = 1
            // Page 1: Visual scan + invisible searchable OCR text layer
            val scanPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create()
            val scanPage = pdf.startPage(scanPageInfo)
            try {
                drawVisualScanPage(scanPage.canvas, document, scanBitmap)
            } finally {
                pdf.finishPage(scanPage)
            }

            // Page 2+: Selectable OCR transcript & extracted metadata sheet
            appendOcrIndexPages(pdf, document, pageNum)

            FileOutputStream(outputFile).use { fos ->
                pdf.writeTo(fos)
            }
            outputFile
        } finally {
            runCatching { pdf.close() }
        }
    }

    /**
     * Generates a combined searchable Daily Boss Report PDF containing all documents filed on a specific date.
     */
    suspend fun generateDailyBundlePdf(
        context: Context,
        dateIso: String,
        documents: List<ScannedDocument>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val safeDate = dateIso.replace("-", "")
        val outputFile = File(exportDir, "DocFolio_DailyReport_$safeDate.pdf")
        val pdf = PdfDocument()
        try {
            var pageNum = 1
            // Cover Summary Page
            val coverInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create()
            val coverPage = pdf.startPage(coverInfo)
            try {
                drawDailyCoverPage(coverPage.canvas, dateIso, documents)
            } finally {
                pdf.finishPage(coverPage)
            }

            // Append each document's visual scan + OCR sheet
            for (doc in documents) {
                val bmp = ImageStorageHelper.loadDocumentBitmap(
                    context = context,
                    localImageFileName = doc.localImageFileName,
                    previewBase64 = doc.previewBase64,
                    docId = doc.id
                )
                val scanInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create()
                val scanPage = pdf.startPage(scanInfo)
                try {
                    drawVisualScanPage(scanPage.canvas, doc, bmp)
                } finally {
                    pdf.finishPage(scanPage)
                }

                pageNum = appendOcrIndexPages(pdf, doc, pageNum)
            }

            FileOutputStream(outputFile).use { fos ->
                pdf.writeTo(fos)
            }
            outputFile
        } finally {
            runCatching { pdf.close() }
        }
    }

    fun getUriForPdf(context: Context, pdfFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
    }

    fun buildSharePdfIntent(context: Context, pdfFile: File, subject: String): Intent {
        val uri = getUriForPdf(context, pdfFile)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share Searchable PDF")
    }

    fun buildViewPdfIntent(context: Context, pdfFile: File): Intent {
        val uri = getUriForPdf(context, pdfFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun drawVisualScanPage(canvas: Canvas, doc: ScannedDocument, scanBitmap: Bitmap?) {
        canvas.drawColor(Color.WHITE)

        // Subtle architectural header strip
        val headerBg = Paint().apply { color = Color.parseColor("#0F2537") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 54f, headerBg)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6E4F0")
            textSize = 9f
            typeface = Typeface.MONOSPACE
        }

        val clippedTitle = if (doc.title.length > 62) doc.title.take(59) + "..." else doc.title
        canvas.drawText(clippedTitle, MARGIN, 23f, titlePaint)

        val effectiveDateIso = doc.documentDateIso.ifBlank { doc.filedDateIso }
        val dateLabel = if (effectiveDateIso.isNotBlank()) {
            "Date: ${DateSearchParser.formatSlashDate(effectiveDateIso)}"
        } else {
            "Date: —"
        }
        val refLabel = if (doc.referenceNumber.isNotBlank()) " | Ref: ${doc.referenceNumber}" else ""
        val orgLabel = if (doc.organization.isNotBlank()) " | ${doc.organization}" else ""
        canvas.drawText("$dateLabel$refLabel$orgLabel", MARGIN, 41f, metaPaint)

        // Draw crisp scanned image fitted cleanly within the page frame
        val imageTop = 68f
        val imageBottom = PAGE_HEIGHT - 36f
        val availWidth = PAGE_WIDTH - (MARGIN * 2f)
        val availHeight = imageBottom - imageTop

        if (scanBitmap != null) {
            val scale = min(availWidth / scanBitmap.width.toFloat(), availHeight / scanBitmap.height.toFloat())
            val drawW = scanBitmap.width * scale
            val drawH = scanBitmap.height * scale
            val left = (PAGE_WIDTH - drawW) / 2f
            val top = imageTop + (availHeight - drawH) / 2f
            val destRect = RectF(left, top, left + drawW, top + drawH)

            val borderPaint = Paint().apply {
                color = Color.parseColor("#CBD2D9")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawBitmap(scanBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRect(destRect, borderPaint)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4F5D6B")
                textSize = 11f
            }
            canvas.drawText(
                "Original photo unavailable offline; see Page 2 for full searchable OCR record.",
                MARGIN,
                imageTop + 28f,
                fallbackPaint
            )
        }

        // Draw invisible OCR text layer ON TOP of the scan image so PDF viewers index & hit-test Page 1
        val invisibleOcrPaint = Paint().apply {
            color = Color.argb(1, 0, 0, 0) // Imperceptible 0.4% alpha yet emitted into PDF text stream
            textSize = 8f
            typeface = Typeface.SANS_SERIF
        }
        val ocrText = buildSearchableTranscript(doc)
        val ocrLines = wrapText(ocrText, invisibleOcrPaint, CONTENT_WIDTH)
        var textY = 80f
        for (line in ocrLines) {
            if (textY > PAGE_HEIGHT - 40f) break
            canvas.drawText(line, MARGIN, textY, invisibleOcrPaint)
            textY += 10f
        }

        // Footer watermark
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7785")
            textSize = 8f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText(
            "DocFolio Searchable PDF  •  Category: ${doc.categoryEnum.displayName}",
            MARGIN,
            PAGE_HEIGHT - 14f,
            footerPaint
        )
    }

    private fun appendOcrIndexPages(pdf: PdfDocument, doc: ScannedDocument, startPageNumber: Int): Int {
        var pageNum = startPageNumber
        var currentPage: PdfDocument.Page? = pdf.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create()
        )
        try {
            var canvas = requireNotNull(currentPage).canvas
            canvas.drawColor(Color.WHITE)

            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F2537")
                textSize = 15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#C86D1F")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4F5D6B")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#141B22")
                textSize = 9.8f
                typeface = Typeface.SANS_SERIF
            }
            val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#141B22")
                textSize = 8.8f
                typeface = Typeface.MONOSPACE
            }
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#CBD2D9")
                strokeWidth = 1f
            }

            var y = MARGIN + 8f

            fun ensureSpace(neededHeight: Float) {
                if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                    currentPage?.let { pageToFinish ->
                        currentPage = null
                        pdf.finishPage(pageToFinish)
                    }
                    val nextPage = pdf.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create()
                    )
                    currentPage = nextPage
                    canvas = nextPage.canvas
                    canvas.drawColor(Color.WHITE)
                    y = MARGIN + 10f
                }
            }

            // Document title
            for (line in wrapText(doc.title, headingPaint, CONTENT_WIDTH)) {
                ensureSpace(20f)
                canvas.drawText(line, MARGIN, y, headingPaint)
                y += 19f
            }
            y += 4f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
            y += 16f

            // Metadata block
            val metaPairs = listOf(
                "Category" to doc.categoryEnum.displayName,
                "Filed Date" to "${DateSearchParser.formatSlashDate(doc.filedDateIso)} (${DateSearchParser.formatFriendlyDate(doc.filedDateIso)})",
                "Document Date" to if (doc.documentDateIso.isNotBlank()) {
                    "${DateSearchParser.formatSlashDate(doc.documentDateIso)} (${DateSearchParser.formatFriendlyDate(doc.documentDateIso)})"
                } else {
                    "Not dated on form"
                },
                "Reference No." to doc.referenceNumber.ifBlank { "—" },
                "Organization" to doc.organization.ifBlank { "—" },
                "Contractor / Party" to doc.contractorOrParty.ifBlank { "—" }
            )
            for ((k, v) in metaPairs) {
                ensureSpace(16f)
                canvas.drawText("$k:", MARGIN, y, labelPaint)
                val wrappedVal = wrapText(v, bodyPaint, CONTENT_WIDTH - 115f)
                wrappedVal.forEachIndexed { idx, line ->
                    if (idx > 0) ensureSpace(14f)
                    canvas.drawText(line, MARGIN + 115f, y, bodyPaint)
                    y += 14f
                }
            }

            if (doc.summary.isNotBlank()) {
                y += 8f
                ensureSpace(24f)
                canvas.drawText("SUMMARY", MARGIN, y, sectionPaint)
                y += 14f
                for (line in wrapText(doc.summary, bodyPaint, CONTENT_WIDTH)) {
                    ensureSpace(14f)
                    canvas.drawText(line, MARGIN, y, bodyPaint)
                    y += 13.5f
                }
            }

            if (doc.extractedFields.isNotEmpty()) {
                y += 10f
                ensureSpace(24f)
                canvas.drawText("EXTRACTED FIELDS & LINE ITEMS", MARGIN, y, sectionPaint)
                y += 14f
                for (field in doc.extractedFields) {
                    val cleanField = field.replace(Regex("""\.000\b"""), "").replace(Regex("""\bEA\b""", RegexOption.IGNORE_CASE), "Nos")
                    for (line in wrapText("• $cleanField", bodyPaint, CONTENT_WIDTH)) {
                        ensureSpace(14f)
                        canvas.drawText(line, MARGIN, y, bodyPaint)
                        y += 13.5f
                    }
                }
            }

            y += 12f
            ensureSpace(28f)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
            y += 16f
            canvas.drawText("SEARCHABLE OCR FULL TRANSCRIPT", MARGIN, y, sectionPaint)
            y += 15f

            val transcript = buildSearchableTranscript(doc).ifBlank { "(No OCR text extracted)" }
            for (line in wrapText(transcript, monoPaint, CONTENT_WIDTH)) {
                ensureSpace(13f)
                canvas.drawText(line, MARGIN, y, monoPaint)
                y += 12.5f
            }
        } finally {
            currentPage?.let { pageToFinish ->
                currentPage = null
                runCatching { pdf.finishPage(pageToFinish) }
            }
        }
        return pageNum
    }

    /**
     * Builds a comprehensive text transcript from either OCR full text or structured manual entry fields.
     * Ensures every generated PDF is 100% searchable in PDF viewers even when manual entry is used.
     */
    fun buildSearchableTranscript(doc: ScannedDocument): String {
        if (doc.ocrFullText.isNotBlank()) return doc.ocrFullText
        val parts = mutableListOf<String>()
        if (doc.title.isNotBlank()) parts.add("DOCUMENT: ${doc.title}")
        if (doc.referenceNumber.isNotBlank()) parts.add("REFERENCE: ${doc.referenceNumber}")
        if (doc.organization.isNotBlank()) parts.add("ORGANIZATION: ${doc.organization}")
        if (doc.contractorOrParty.isNotBlank()) parts.add("PARTY: ${doc.contractorOrParty}")
        if (doc.documentDateIso.isNotBlank()) {
            parts.add("DOCUMENT DATE: ${doc.documentDateIso} (${DateSearchParser.formatSlashDate(doc.documentDateIso)})")
        }
        if (doc.filedDateIso.isNotBlank()) {
            parts.add("FILED DATE: ${doc.filedDateIso} (${DateSearchParser.formatSlashDate(doc.filedDateIso)})")
        }
        parts.add("CATEGORY: ${doc.categoryEnum.displayName}")
        if (doc.summary.isNotBlank()) parts.add("SUMMARY: ${doc.summary}")
        if (doc.extractedFields.isNotEmpty()) {
            parts.add("LINE ITEMS:")
            doc.extractedFields.forEach { parts.add("• $it") }
        }
        return parts.joinToString("\n")
    }

    private fun drawDailyCoverPage(canvas: Canvas, dateIso: String, documents: List<ScannedDocument>) {
        canvas.drawColor(Color.WHITE)

        val bannerPaint = Paint().apply { color = Color.parseColor("#0F2537") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 108f, bannerPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5A962")
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6E4F0")
            textSize = 11f
            typeface = Typeface.SANS_SERIF
        }

        canvas.drawText("DOCFOLIO • DAILY FILING REGISTER", MARGIN, 34f, brandPaint)
        canvas.drawText(
            "Documents for ${DateSearchParser.formatSlashDate(dateIso)}",
            MARGIN,
            64f,
            titlePaint
        )
        canvas.drawText(
            "${DateSearchParser.formatFriendlyDate(dateIso)}  •  ${documents.size} searchable record(s) compiled",
            MARGIN,
            88f,
            subtitlePaint
        )

        val rowTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141B22")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rowMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4F5D6B")
            textSize = 9.2f
            typeface = Typeface.MONOSPACE
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0DDD5")
            strokeWidth = 1f
        }

        var y = 138f
        documents.forEachIndexed { idx, doc ->
            if (y > PAGE_HEIGHT - 60f) return@forEachIndexed
            val num = "${idx + 1}."
            val lineTitle = if (doc.title.length > 68) doc.title.take(65) + "..." else doc.title
            canvas.drawText("$num $lineTitle", MARGIN, y, rowTitlePaint)
            y += 15f
            val ref = doc.referenceNumber.ifBlank { "No Ref" }
            val party = doc.contractorOrParty.ifBlank { doc.organization.ifBlank { "—" } }
            canvas.drawText(
                "   ${doc.categoryEnum.shortLabel}  |  Ref: $ref  |  $party",
                MARGIN,
                y,
                rowMetaPaint
            )
            y += 12f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
            y += 16f
        }
    }

    internal fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.replace("\r\n", "\n").split("\n")
        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) {
                result.add("")
                continue
            }
            val words = paragraph.split(Regex("\\s+"))
            val currentLine = StringBuilder()
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    if (currentLine.isNotEmpty()) currentLine.append(' ')
                    currentLine.append(word)
                } else {
                    if (currentLine.isNotEmpty()) {
                        result.add(currentLine.toString())
                        currentLine.clear()
                    }
                    // Break ultra-long unbroken tokens safely
                    if (paint.measureText(word) > maxWidth) {
                        var start = 0
                        while (start < word.length) {
                            val count = paint.breakText(word, start, word.length, true, maxWidth, null)
                            val safeCount = if (count <= 0) 1 else count
                            val end = (start + safeCount).coerceAtMost(word.length)
                            result.add(word.substring(start, end))
                            start = end
                        }
                    } else {
                        currentLine.append(word)
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString())
            }
        }
        return result
    }
}
