package com.example

import com.example.data.DocumentRepository
import com.example.data.MediaRelayService
import com.example.model.DateMatchMode
import com.example.model.DocumentCategory
import com.example.model.ScannedDocument
import com.example.util.DateSearchParser
import com.example.util.SearchablePdfGenerator
import com.example.viewmodel.DocFolioViewModel
import com.example.viewmodel.VaultFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM Unit Tests for DocFolio core business logic.
 * These tests execute directly on host JVM without requiring Robolectric or native Android libs.
 */
class DocFolioPureJvmTest {

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
    fun firestoreSearchHelpers_normalizeBoundsMergeDedupeAndMatchMultiTokenKeywords() {
        val (orderedStart, orderedEnd) = DocumentRepository.normalizeDateRangeBounds(
            startDateIso = "2026-03-15",
            endDateIso = "2026-01-01"
        )
        assertEquals("2026-01-01", orderedStart)
        assertEquals("2026-03-15", orderedEnd)

        val (invalidStart, validEnd) = DocumentRepository.normalizeDateRangeBounds(
            startDateIso = "not-a-date",
            endDateIso = "2026-02-24"
        )
        assertNull(invalidStart)
        assertEquals("2026-02-24", validEnd)

        val docA = ScannedDocument(
            id = "doc_a",
            title = "Material Requisition Slip #6717",
            referenceNumber = "6717",
            organization = "Kalpataru Projects",
            contractorOrParty = "Apex Solar",
            documentDateIso = "2026-02-24",
            filedDateIso = "2026-01-12",
            extractedFields = listOf("Approved By: Rajesh Sharma"),
            localPdfFileName = "slip_6717_searchable.pdf",
            ocrFullText = "Nut Bolt 8mm Required 440 Issued 440"
        )
        val docB = ScannedDocument(
            id = "doc_b",
            title = "Solar System Inspection Protocol",
            filedDateIso = "2026-02-24",
            ocrFullText = "Earthing Resistance Test"
        )
        val merged = DocumentRepository.mergeDistinctDocuments(
            first = listOf(docA, docB),
            second = listOf(docB, docA)
        )
        assertEquals(listOf("doc_a", "doc_b"), merged.map { it.id })

        assertTrue(DocumentRepository.matchesKeyword(docA, "kalpataru 6717"))
        assertTrue(DocumentRepository.matchesKeyword(docA, "rajesh sharma"))
        assertTrue(DocumentRepository.matchesKeyword(docA, "slip_6717_searchable.pdf"))
        assertTrue(DocumentRepository.matchesKeyword(docA, "24/02/2026"))
        assertTrue(DocumentRepository.matchesKeyword(docA, ""))
        assertFalse(DocumentRepository.matchesKeyword(docA, "kalpataru earthing"))

        val today = DateSearchParser.todayIso()
        val sevenDaysAgo = DateSearchParser.daysAgoIso(6)
        val monthStart = DateSearchParser.startOfCurrentMonthIso()
        assertTrue(DateSearchParser.isValidIsoDate(sevenDaysAgo))
        assertTrue(DateSearchParser.isValidIsoDate(monthStart))
        assertTrue(sevenDaysAgo <= today)
        assertTrue(monthStart <= today)
        assertTrue(monthStart.endsWith("-01"))
    }

    @Test
    fun mediaRelayService_generatesValidStreamUrls() {
        val urlWithoutToken = MediaRelayService.getMediaStreamUrl("doc_999", "photo")
        assertEquals("https://docfolio.130.210.30.151.sslip.io/api/documents/doc_999/photo", urlWithoutToken)

        val urlWithToken = MediaRelayService.getMediaStreamUrl("doc_999", "pdf", "test_tok_123")
        assertEquals("https://docfolio.130.210.30.151.sslip.io/api/documents/doc_999/pdf?token=test_tok_123", urlWithToken)
    }

    @Test
    fun searchablePdfGenerator_buildsTranscriptFromManualFieldsWhenOcrEmpty() {
        val manualDoc = ScannedDocument(
            id = "doc_manual_1",
            title = "Site Material Delivery Note",
            category = DocumentCategory.DELIVERY_CHALLAN.name,
            documentDateIso = "2026-02-24",
            filedDateIso = "2026-02-25",
            referenceNumber = "DN-8841",
            organization = "Kalpataru Projects",
            contractorOrParty = "Apex Solar",
            summary = "Delivery of solar panels pallet 4",
            extractedFields = listOf("Panel Model: 540W Mono", "Quantity: 28 Nos"),
            ocrFullText = ""
        )
        val transcript = SearchablePdfGenerator.buildSearchableTranscript(manualDoc)
        assertTrue(transcript.contains("DOCUMENT: Site Material Delivery Note"))
        assertTrue(transcript.contains("REFERENCE: DN-8841"))
        assertTrue(transcript.contains("ORGANIZATION: Kalpataru Projects"))
        assertTrue(transcript.contains("PARTY: Apex Solar"))
        assertTrue(transcript.contains("DOCUMENT DATE: 2026-02-24 (24/02/2026)"))
        assertTrue(transcript.contains("FILED DATE: 2026-02-25 (25/02/2026)"))
        assertTrue(transcript.contains("CATEGORY: Delivery Challan"))
        assertTrue(transcript.contains("SUMMARY: Delivery of solar panels pallet 4"))
        assertTrue(transcript.contains("• Panel Model: 540W Mono"))

        // When OCR text is already populated, it is preserved verbatim
        val ocrDoc = manualDoc.copy(ocrFullText = "Raw OCR scanned text from camera")
        assertEquals("Raw OCR scanned text from camera", SearchablePdfGenerator.buildSearchableTranscript(ocrDoc))
    }

    @Test
    fun companyVaultAndRelayConstants_areConfiguredForPublicMultiDeviceSync() {
        assertEquals("company_documents", DocumentRepository.COMPANY_COLLECTION_NAME)
        assertEquals("company_documents", DocumentRepository.COMPANY_DOCS_PATH)
        assertEquals("docfolio_secret_test_key_99812", MediaRelayService.RELAY_INTERNAL_SECRET)
    }

    @Test
    fun seedDocuments_haveCorrectedTypoFreeMaterialNames() {
        val file = listOf(
            java.io.File("src/main/assets/seed_documents.json"),
            java.io.File("app/src/main/assets/seed_documents.json")
        ).firstOrNull { it.exists() } ?: error("seed_documents.json not found")
        val text = file.readText()
        assertFalse("Should not contain ANTI THETT typo", text.contains("ANTI THETT"))
        assertFalse("Should not contain COLUM typo", text.contains("\"COLUM 3350"))
        assertFalse("Should not contain PUERLING typo", text.contains("PUERLING"))
        assertFalse("Should not contain CONNECTION PLAT: typo", text.contains("CONNECTION PLAT:"))
        assertTrue("Should contain ANTI THEFT", text.contains("ANTI THEFT NUT BOLT M16"))
        assertTrue("Should contain COLUMN 3350", text.contains("COLUMN 3350"))
        assertTrue("Should contain PURLIN 5125", text.contains("PURLIN 5125"))
        assertTrue("Should contain CONNECTION PLATE", text.contains("CONNECTION PLATE"))
    }

    @Test
    fun formatQuantityForDisplay_cleansRawErpDecimalJunkToCleanPieces() {
        assertEquals("12 pcs", com.example.ui.formatQuantityForDisplay("12.000 EA"))
        assertEquals("95 pcs", com.example.ui.formatQuantityForDisplay("95.000 EA"))
        assertEquals("20 pcs", com.example.ui.formatQuantityForDisplay("20.000 EA"))
        assertEquals("50 pcs", com.example.ui.formatQuantityForDisplay("50.000 EA"))
        assertEquals("4 pcs", com.example.ui.formatQuantityForDisplay("4.000 EA"))
        assertEquals("5 pcs", com.example.ui.formatQuantityForDisplay("5.000 EA"))
        assertEquals("228 pcs", com.example.ui.formatQuantityForDisplay("228.000"))
        assertEquals("12 pcs", com.example.ui.formatQuantityForDisplay("12"))
        assertEquals("10 mtr", com.example.ui.formatQuantityForDisplay("10 MTR"))
        assertEquals("", com.example.ui.formatQuantityForDisplay(""))
    }

    @Test
    fun gatePass1000367086_hasAccurateRowQuantitiesAndSummary() {
        val file = listOf(
            java.io.File("src/main/assets/seed_documents.json"),
            java.io.File("app/src/main/assets/seed_documents.json")
        ).firstOrNull { it.exists() } ?: error("seed_documents.json not found")
        val text = file.readText()
        assertTrue("Connection Plate should have 12", text.contains("\"CONNECTION PLATE 140X80: 12.000 EA\""))
        assertTrue("Anti Theft should have 95", text.contains("\"ANTI THEFT NUT BOLT M16: 95.000 EA\""))
        assertTrue("Module should have 20", text.contains("\"MODULE 400W: 20.000 EA\""))
        assertTrue("Nut Bolt M12 should have 50", text.contains("\"NUT BOLT M 12: 50.000 EA\""))
        assertTrue("DCDB should have 4", text.contains("\"DCDB 1 IN 4 OUT: 4.000 EA\""))
        assertTrue("Rafter 3350 should have 5", text.contains("\"RAFTER 3350: 5.000 EA\""))
        assertTrue("Summary should specify 20 PV Modules", text.contains("20 PV Modules"))
        assertFalse("Summary must not say 50 PV Modules", text.contains("50 PV Modules"))
    }

    @Test
    fun siteExpenseRepository_formatsWhatsAppReportCorrectly() {
        val expenses = listOf(
            com.example.data.model.SiteExpense("1", "2026-11-17", "Diesel", 1000.0, "Aziz", "", "FUEL"),
            com.example.data.model.SiteExpense("2", "2026-11-17", "Augr Machine Repair", 100.0, "Nadeem", "", "REPAIR")
        )
        val materials = listOf(
            com.example.data.model.SiteMaterialRecord("m1", "2026-11-17", 1, "J Bolt", "96", "53", "")
        )
        val dummyDao = object : com.example.data.local.SiteExpenseDao {
            override fun getAllExpensesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteExpenseEntity>>()
            override fun getExpensesByDateFlow(dateIso: String) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteExpenseEntity>>()
            override fun getAllMaterialsFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteMaterialRecordEntity>>()
            override fun getMaterialsByDateFlow(dateIso: String) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteMaterialRecordEntity>>()
            override suspend fun insertExpense(expense: com.example.data.local.SiteExpenseEntity) {}
            override suspend fun insertExpenses(expenses: List<com.example.data.local.SiteExpenseEntity>) {}
            override suspend fun deleteExpense(id: String) {}
            override suspend fun insertMaterial(material: com.example.data.local.SiteMaterialRecordEntity) {}
            override suspend fun insertMaterials(materials: List<com.example.data.local.SiteMaterialRecordEntity>) {}
            override suspend fun deleteMaterial(id: String) {}
            override suspend fun getCount() = 0
            override suspend fun getMaterialCount() = 0
        }
        val repo = com.example.data.SiteExpenseRepository(dummyDao)
        val report = repo.formatWhatsAppReport("17 November 2026", expenses, materials)
        assertTrue(report.contains("APEX SOLAR WORK - DAILY SITE REPORT"))
        assertTrue(report.contains("17 November 2026"))
        assertTrue(report.contains("TOTAL EXPENSE: ₹1100"))
        assertTrue(report.contains("Paid by Aziz:* ₹1000"))
        assertTrue(report.contains("Paid by Nadeem:* ₹100"))
        assertTrue(report.contains("J Bolt* — OUT: 96 | Left in Store: 53"))

        val blankPayerExpenses = listOf(
            com.example.data.model.SiteExpense("3", "2026-11-17", "Chai", 50.0, "", "", "FOOD")
        )
        val blankReport = repo.formatWhatsAppReport("17 November 2026", blankPayerExpenses, emptyList())
        assertTrue(blankReport.contains("TOTAL EXPENSE: ₹50"))
        assertTrue(blankReport.contains("Other:* ₹50"))
    }
}
