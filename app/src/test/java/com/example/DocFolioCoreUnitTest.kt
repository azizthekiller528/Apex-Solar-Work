package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeminiOcrService
import com.example.data.local.DocFolioDatabase
import com.example.data.local.LocalDocumentCacheRepository
import com.example.model.DateMatchMode
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.util.DateSearchParser
import com.example.util.SearchablePdfGenerator
import com.example.viewmodel.DocFolioViewModel
import com.example.viewmodel.VaultFilterState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocFolioCoreUnitTest {

    @Test
    fun parseDateQuery_handlesBossSlashDatesWithDisambiguation() {
        val ambiguous = DateSearchParser.parseDateQuery("12/1/2026")
        assertNotNull(ambiguous)
        assertEquals("2026-01-12", ambiguous!!.primaryIso) // DD/MM/YYYY default
        assertEquals("2026-12-01", ambiguous.alternateIso) // MM/DD/YYYY alternate

        val unambiguousSlipDate = DateSearchParser.parseDateQuery("24/02/2026")
        assertNotNull(unambiguousSlipDate)
        assertEquals("2026-02-24", unambiguousSlipDate!!.primaryIso)
        assertNull(unambiguousSlipDate.alternateIso)

        val iso = DateSearchParser.parseDateQuery("2026-01-12")
        assertNotNull(iso)
        assertEquals("2026-01-12", iso!!.primaryIso)

        val friendly = DateSearchParser.parseDateQuery("12 Jan 2026")
        assertNotNull(friendly)
        assertEquals("2026-01-12", friendly!!.primaryIso)

        assertNull(DateSearchParser.parseDateQuery("31/02/2026"))
        assertNull(DateSearchParser.parseDateQuery("Apex Solar"))
    }

    @Test
    fun utcMillisRoundTrip_preservesExactDateWithoutTimezoneDrift() {
        val iso = "2026-02-24"
        val millis = DateSearchParser.isoToUtcMillis(iso)
        assertNotNull(millis)
        assertEquals(iso, DateSearchParser.utcMillisToIso(millis!!))
        assertEquals("24/02/2026", DateSearchParser.formatSlashDate(iso))
        assertEquals("24 Feb 2026", DateSearchParser.formatFriendlyDate(iso))
    }

    @Test
    fun vaultFilter_findsDocumentsByBossDateAndOcrKeywords() {
        val slip = ScannedDocument(
            id = "doc_slip_6717",
            userId = "uid_1",
            title = "Material Requisition Slip #6717 — Nut Bolt 8mm",
            category = DocumentCategory.MATERIAL_SLIP.name,
            documentDateIso = "2026-02-24",
            filedDateIso = "2026-01-12",
            referenceNumber = "6717 / FCN-ST-13 (R1)",
            organization = "Kalpataru Projects International Ltd.",
            contractorOrParty = "Apex Solar — MEP",
            ocrFullText = "Nut Bolt 8mm Required 440 Issued 440 Returnable"
        )
        val protocol = ScannedDocument(
            id = "doc_solar_proto",
            userId = "uid_1",
            title = "Solar System Inspection Protocol",
            category = DocumentCategory.INSPECTION_PROTOCOL.name,
            documentDateIso = "",
            filedDateIso = "2026-02-25",
            organization = "Kalpataru / Jal Jeevan Mission",
            ocrFullText = "PV POWER TECHNOLOGIES PVT LTD Earthing Resistance Test"
        )
        val docs = listOf(slip, protocol)

        // Boss types "12/1/2026" -> finds document filed on 2026-01-12
        val byFiled = DocFolioViewModel.applyVaultFilter(
            docs,
            VaultFilterState(query = "12/1/2026", dateMatchMode = DateMatchMode.FILED_ONLY)
        )
        assertEquals(1, byFiled.size)
        assertEquals("doc_slip_6717", byFiled.first().id)

        // Boss types "24/02/2026" -> finds document with Document Date 2026-02-24
        val byDocDate = DocFolioViewModel.applyVaultFilter(
            docs,
            VaultFilterState(query = "24/02/2026", dateMatchMode = DateMatchMode.EITHER)
        )
        assertEquals(1, byDocDate.size)
        assertEquals("doc_slip_6717", byDocDate.first().id)

        // Explicit calendar date + keyword search combo works together
        val combo = DocFolioViewModel.applyVaultFilter(
            docs,
            VaultFilterState(query = "Nut Bolt", explicitDateIso = "2026-01-12")
        )
        assertEquals(1, combo.size)

        // OCR text search finds inner table vendor
        val byOcr = DocFolioViewModel.applyVaultFilter(
            docs,
            VaultFilterState(query = "PV POWER TECHNOLOGIES")
        )
        assertEquals(1, byOcr.size)
        assertEquals("doc_solar_proto", byOcr.first().id)
    }

    @Test
    fun geminiOcrParser_normalizesValidAndBlankDatePayloads() {
        val service = GeminiOcrService()
        val json = """
            {
              "title": "Material Requisition Slip #6717",
              "category": "MATERIAL_SLIP",
              "documentDateIso": "24/02/2026",
              "referenceNumber": "6717",
              "organization": "Kalpataru Projects International Ltd.",
              "contractorOrParty": "Apex Solar",
              "summary": "440 Nut Bolt 8mm issued.",
              "extractedFields": ["Item: Nut Bolt 8mm", "Issued: 440"],
              "ocrFullText": "KALPATARU 6717 Apex Solar Nut Bolt 8mm 440"
            }
        """.trimIndent()
        val result = service.parseExtractionJson(json)
        assertEquals("2026-02-24", result.documentDateIso)
        assertEquals(DocumentCategory.MATERIAL_SLIP, result.category)
        assertEquals(2, result.extractedFields.size)
        assertTrue(result.ocrFullText.contains("Apex Solar"))
    }

    @Test
    fun pdfWrapTextAndHighlight_handleLongTokensAndMultiLineTranscripts() {
        val paint = android.graphics.Paint().apply { textSize = 10f }
        val longTranscript = "KALPATARU PROJECTS INTERNATIONAL LTD.\n\n" +
            "FCN-ST-13_R1_SUPER_LONG_REFERENCE_CODE_1234567890_ABCDEFGHIJKLMN\n" +
            "Nut Bolt 8mm Required 440 Issued 440 Returnable Apex Solar MEP"

        val wrapped = SearchablePdfGenerator.wrapText(longTranscript, paint, 120f)
        assertTrue(wrapped.isNotEmpty())
        assertTrue(wrapped.any { it.contains("KALPATARU") })
        assertTrue(wrapped.any { it.contains("440") })

        val (annotated, matches) = com.example.ui.buildHighlightedText(
            source = "Nut Bolt 8mm (Required: 440, Issued: 440) — nut bolt",
            needle = "nut bolt",
            highlightBg = androidx.compose.ui.graphics.Color.Yellow,
            highlightFg = androidx.compose.ui.graphics.Color.Black
        )
        assertEquals(2, matches)
        assertTrue(annotated.spanStyles.size == 2)
    }

    @Test
    fun roomLocalCache_storesQueriesByDateAndOcrAndSyncsCleanly() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DocFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val cache = LocalDocumentCacheRepository(db.documentDao())
            val slip = ScannedDocument(
                id = "doc_slip_6717",
                userId = "uid_1",
                title = "Material Requisition Slip #6717",
                category = DocumentCategory.MATERIAL_SLIP.name,
                documentDateIso = "2026-02-24",
                filedDateIso = "2026-01-12",
                localImageFileName = "doc_slip_6717_scan.jpg",
                localPdfFileName = "doc_slip_6717_searchable.pdf",
                ocrFullText = "KALPATARU Nut Bolt 8mm Apex Solar Returnable"
            )
            val protocol = ScannedDocument(
                id = "doc_solar_proto",
                userId = "uid_1",
                title = "Solar System Inspection Protocol",
                category = DocumentCategory.INSPECTION_PROTOCOL.name,
                documentDateIso = "",
                filedDateIso = "2026-02-25",
                localImageFileName = "doc_solar_proto_scan.jpg",
                localPdfFileName = "doc_solar_proto_searchable.pdf",
                ocrFullText = "PV POWER TECHNOLOGIES Earthing Strip"
            )

            cache.syncFromCloud("uid_1", listOf(slip, protocol), "/data/docfolio")

            val allCached = cache.observeCachedDocuments("uid_1").first()
            assertEquals(2, allCached.size)

            val jan12 = cache.observeCachedDocumentsByDate("uid_1", "2026-01-12").first()
            assertEquals(1, jan12.size)
            assertEquals("doc_slip_6717_searchable.pdf", jan12.first().fileName)
            assertEquals("/data/docfolio/doc_slip_6717_scan.jpg", jan12.first().localImagePath)
            assertEquals("/data/docfolio/doc_slip_6717_searchable.pdf", jan12.first().localPdfPath)

            val feb24ByDocDate = cache.observeCachedDocumentsByDate("uid_1", "2026-02-24").first()
            assertEquals(1, feb24ByDocDate.size)
            assertEquals("doc_slip_6717", feb24ByDocDate.first().id)

            val ocrSearch = cache.searchCachedDocuments("uid_1", "Apex Solar").first()
            assertEquals(1, ocrSearch.size)
            assertEquals("doc_slip_6717", ocrSearch.first().id)

            // Syncing with only protocol prunes removed slip cleanly
            cache.syncFromCloud("uid_1", listOf(protocol), "/data/docfolio")
            val afterPrune = cache.observeCachedDocuments("uid_1").first()
            assertEquals(1, afterPrune.size)
            assertEquals("doc_solar_proto", afterPrune.first().id)
        } finally {
            db.close()
        }
    }

    @Test
    fun roomDao_queryByKeywordOrDateRange_handlesDynamicTypingRangesAndWildcards() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DocFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val cache = LocalDocumentCacheRepository(db.documentDao())
            val janSlip = ScannedDocument(
                id = "doc_jan",
                userId = "uid_1",
                title = "Material Requisition Slip #6717",
                category = DocumentCategory.MATERIAL_SLIP.name,
                documentDateIso = "2026-01-10",
                filedDateIso = "2026-01-12",
                referenceNumber = "6717",
                organization = "Kalpataru Projects",
                contractorOrParty = "Apex Solar",
                summary = "100% issued Nut Bolt 8mm",
                localImageFileName = "jan.jpg",
                localPdfFileName = "jan_searchable.pdf",
                ocrFullText = "Nut Bolt 8mm 100% Returnable"
            )
            val febProtocol = ScannedDocument(
                id = "doc_feb",
                userId = "uid_1",
                title = "Solar System Inspection Protocol",
                category = DocumentCategory.INSPECTION_PROTOCOL.name,
                documentDateIso = "",
                filedDateIso = "2026-02-20",
                referenceNumber = "SSP-09",
                organization = "Kalpataru",
                contractorOrParty = "PV Power Technologies",
                summary = "Earthing and mounting check",
                extractedFields = listOf("Approved By: Rajesh Sharma", "Capacity: 5kW"),
                localImageFileName = "feb.jpg",
                localPdfFileName = "feb_searchable.pdf",
                ocrFullText = "PV POWER TECHNOLOGIES Earthing Strip"
            )
            val marChallan = ScannedDocument(
                id = "doc_mar",
                userId = "uid_1",
                title = "Delivery Challan #88",
                category = DocumentCategory.DELIVERY_CHALLAN.name,
                documentDateIso = "2026-03-05",
                filedDateIso = "2026-03-06",
                referenceNumber = "DC-88",
                organization = "Eastman",
                contractorOrParty = "Site Store",
                summary = "Inverter battery dispatch",
                localImageFileName = "mar.jpg",
                localPdfFileName = "mar_searchable.pdf",
                ocrFullText = "Battery Inverter Dispatch"
            )
            cache.syncFromCloud("uid_1", listOf(janSlip, febProtocol, marChallan), "/docs")

            // 1. Dynamic case-insensitive keyword search across referenceNumber / party / OCR
            val byRef = cache.queryDocumentsByKeywordOrDateRange(userId = "uid_1", keyword = "6717").first()
            assertEquals(listOf("doc_jan"), byRef.map { it.id })

            // 2. Literal '%' wildcard escaping matches only doc containing "100%"
            val byPercent = cache.queryDocumentsByKeywordOrDateRange(userId = "uid_1", keyword = "100%").first()
            assertEquals(listOf("doc_jan"), byPercent.map { it.id })

            // 2b. Custom extractedFields content is indexed and searchable in Room
            val byExtractedField = cache.queryDocumentsByKeywordOrDateRange(
                userId = "uid_1",
                keyword = "Rajesh Sharma"
            ).first()
            assertEquals(listOf("doc_feb"), byExtractedField.map { it.id })

            // 3. Inclusive date range (auto-orders inverted start > end)
            val janToFeb = cache.queryDocumentsByKeywordOrDateRange(
                userId = "uid_1",
                startDateIso = "2026-02-28",
                endDateIso = "2026-01-01"
            ).first()
            assertEquals(setOf("doc_jan", "doc_feb"), janToFeb.map { it.id }.toSet())

            // 4. DOCUMENT_ONLY must NOT match blank documentDateIso when startDateIso is null
            val docOnlyUpToFeb = cache.queryDocumentsByKeywordOrDateRange(
                userId = "uid_1",
                startDateIso = null,
                endDateIso = "2026-02-28",
                dateMatchMode = DateMatchMode.DOCUMENT_ONLY
            ).first()
            assertEquals(listOf("doc_jan"), docOnlyUpToFeb.map { it.id })

            // 5. Combined keyword + date range + category
            val combined = cache.queryDocumentsByKeywordOrDateRange(
                userId = "uid_1",
                keyword = "kalpataru",
                startDateIso = "2026-02-01",
                endDateIso = "2026-03-01",
                category = DocumentCategory.INSPECTION_PROTOCOL.name
            ).first()
            assertEquals(listOf("doc_feb"), combined.map { it.id })

            // 6. Typed date range parsing in DateSearchParser
            val parsedRange = DateSearchParser.parseDateRangeQuery("24/02/2026 - 12/1/2026")
            assertNotNull(parsedRange)
            assertEquals("2026-01-12", parsedRange!!.startIso)
            assertEquals("2026-02-24", parsedRange.endIso)

            val typedRangeFilter = DocFolioViewModel.applyVaultFilter(
                listOf(janSlip, febProtocol, marChallan),
                VaultFilterState(query = "12/1/2026 to 25/02/2026")
            )
            assertEquals(setOf("doc_jan", "doc_feb"), typedRangeFilter.map { it.id }.toSet())
        } finally {
            db.close()
        }
    }
}
