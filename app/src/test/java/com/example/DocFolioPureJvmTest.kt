package com.example

import com.example.data.DocumentRepository
import com.example.data.GeminiOcrService
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
            override suspend fun insertDefaultExpenses(expenses: List<com.example.data.local.SiteExpenseEntity>) {}
            override suspend fun deleteExpense(id: String) {}
            override suspend fun insertMaterial(material: com.example.data.local.SiteMaterialRecordEntity) {}
            override suspend fun insertMaterials(materials: List<com.example.data.local.SiteMaterialRecordEntity>) {}
            override suspend fun deleteMaterial(id: String) {}
            override suspend fun getCount() = 0
            override suspend fun getMaterialCount() = 0
            override suspend fun getExpensesByDate(dateIso: String) = emptyList<com.example.data.local.SiteExpenseEntity>()
            override suspend fun getAllExpenseDates() = emptyList<String>()
            override suspend fun deleteExpensesForDate(dateIso: String) {}
            override suspend fun deleteExpensesNotIn(dateIso: String, validIds: List<String>) {}
            override suspend fun getMaterialsByDate(dateIso: String) = emptyList<com.example.data.local.SiteMaterialRecordEntity>()
            override suspend fun deleteMaterialsForDate(dateIso: String) {}
            override suspend fun deleteMaterialsNotIn(dateIso: String, validIds: List<String>) {}
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

        val nov23Expenses = listOf(
            com.example.data.model.SiteExpense("exp_23_1", "2026-11-23", "Diesel", 2500.0, "Aziz", "", "FUEL"),
            com.example.data.model.SiteExpense("exp_23_2", "2026-11-23", "Loading Solar Panels onto the Pickup", 320.0, "Aziz", "Rate: ₹8 per piece | Pieces: 40", "OTHER"),
            com.example.data.model.SiteExpense("exp_23_3", "2026-11-23", "Tea (Chai) for Labour and Driver", 110.0, "Aziz", "", "FOOD"),
            com.example.data.model.SiteExpense("exp_23_4", "2026-11-23", "Amount Given to Nadeem Bhai", 200.0, "Aziz", "", "OTHER"),
            com.example.data.model.SiteExpense("exp_23_5", "2026-11-23", "Driver Dinner and Cold Drink", 190.0, "Aziz", "", "FOOD")
        )
        val nov23Report = repo.formatWhatsAppReport("23 November 2026", nov23Expenses, emptyList())
        assertTrue(nov23Report.contains("TOTAL EXPENSE: ₹3320"))
        assertTrue(nov23Report.contains("Paid by Aziz:* ₹3320"))
        assertTrue(nov23Report.contains("Diesel — *₹2500*"))
        assertTrue(nov23Report.contains("Loading Solar Panels onto the Pickup (Rate: ₹8 per piece | Pieces: 40) — *₹320*"))
        assertTrue(nov23Report.contains("Tea (Chai) for Labour and Driver — *₹110*"))
        assertTrue(nov23Report.contains("Amount Given to Nadeem Bhai — *₹200*"))
        assertTrue(nov23Report.contains("Driver Dinner and Cold Drink — *₹190*"))
    }

    @Test
    fun deviceTelemetry_mergeDeviceRecord_addsAndUpdatesDevicesCleanly() {
        val initialJson = "{}"
        val mergedFirst = com.example.util.DeviceTelemetryManager.mergeDeviceRecord(
            existingJson = initialJson,
            deviceId = "dev_alpha",
            deviceModel = "Samsung Galaxy S23",
            androidVersion = "14",
            versionName = "1.9",
            versionCode = 10,
            nowIso = "2026-11-23T10:00:00Z"
        )

        val root1 = org.json.JSONObject(mergedFirst)
        assertEquals(1, root1.getInt("totalDevices"))
        val dev1 = root1.getJSONObject("devices").getJSONObject("dev_alpha")
        assertEquals("Samsung Galaxy S23", dev1.getString("deviceModel"))
        assertEquals("1.9", dev1.getString("appVersion"))
        assertEquals(1, dev1.getInt("pingCount"))
        assertEquals("2026-11-23T10:00:00Z", dev1.getString("firstSeen"))
        assertEquals("2026-11-23T10:00:00Z", dev1.getString("lastSeen"))

        // Update dev_alpha later and add dev_beta
        val mergedSecond = com.example.util.DeviceTelemetryManager.mergeDeviceRecord(
            existingJson = mergedFirst,
            deviceId = "dev_alpha",
            deviceModel = "Samsung Galaxy S23",
            androidVersion = "14",
            versionName = "1.9",
            versionCode = 10,
            nowIso = "2026-11-23T15:00:00Z"
        )
        val root2 = org.json.JSONObject(mergedSecond)
        assertEquals(1, root2.getInt("totalDevices"))
        val dev1Updated = root2.getJSONObject("devices").getJSONObject("dev_alpha")
        assertEquals(2, dev1Updated.getInt("pingCount"))
        assertEquals("2026-11-23T10:00:00Z", dev1Updated.getString("firstSeen"))
        assertEquals("2026-11-23T15:00:00Z", dev1Updated.getString("lastSeen"))

        val mergedThird = com.example.util.DeviceTelemetryManager.mergeDeviceRecord(
            existingJson = mergedSecond,
            deviceId = "dev_beta",
            deviceModel = "Xiaomi Redmi Note 12",
            androidVersion = "13",
            versionName = "1.9",
            versionCode = 10,
            nowIso = "2026-11-23T15:05:00Z"
        )
        val root3 = org.json.JSONObject(mergedThird)
        assertEquals(2, root3.getInt("totalDevices"))
        val dev2 = root3.getJSONObject("devices").getJSONObject("dev_beta")
        assertEquals("Xiaomi Redmi Note 12", dev2.getString("deviceModel"))
        assertEquals(1, dev2.getInt("pingCount"))
    }

    @Test
    fun siteExpenseRepository_serializeAndParseExpensesJson_preservesAllFieldsAndDelimiters() {
        val originalExpenses = listOf(
            com.example.data.model.SiteExpense("exp_1", "2026-11-23", "Diesel", 2500.0, "Aziz", "", "FUEL"),
            com.example.data.model.SiteExpense("exp_2", "2026-11-23", "Loading Solar Panels", 320.0, "Aziz", "40 pcs", "OTHER"),
            com.example.data.model.SiteExpense("exp_3", "2026-11-23", "Chai", 110.0, "Aziz", "", "FOOD")
        )
        val originalMaterials = listOf(
            com.example.data.model.SiteMaterialRecord("mat_1", "2026-11-23", 1, "J Bolt", "96", "53", "Near Gate 2")
        )

        val expJson = com.example.data.SiteExpenseRepository.serializeExpensesToJson(originalExpenses)
        val matJson = com.example.data.SiteExpenseRepository.serializeMaterialsToJson(originalMaterials)

        val ocrFullText = buildString {
            append(com.example.data.SiteExpenseRepository.EXPENSES_DELIMITER_START).append("\n")
            append(expJson).append("\n")
            append(com.example.data.SiteExpenseRepository.MATERIALS_DELIMITER_START).append("\n")
            append(matJson).append("\n")
            append(com.example.data.SiteExpenseRepository.DATA_DELIMITER_END).append("\n\n")
            append("Human readable text summary here: Total ₹2,930")
        }

        val parsedExpenses = com.example.data.SiteExpenseRepository.parseExpensesFromOcrText(ocrFullText)
        val parsedMaterials = com.example.data.SiteExpenseRepository.parseMaterialsFromOcrText(ocrFullText)

        assertEquals(3, parsedExpenses.size)
        assertEquals("Diesel", parsedExpenses[0].title)
        assertEquals(2500.0, parsedExpenses[0].amount, 0.001)
        assertEquals("Aziz", parsedExpenses[0].paidBy)
        assertEquals("FUEL", parsedExpenses[0].category)

        assertEquals("Loading Solar Panels", parsedExpenses[1].title)
        assertEquals("40 pcs", parsedExpenses[1].quantityNote)

        assertEquals(1, parsedMaterials.size)
        assertEquals("J Bolt", parsedMaterials[0].itemName)
        assertEquals("96", parsedMaterials[0].qtyOut)
        assertEquals("53", parsedMaterials[0].qtyInStore)
        assertEquals("Near Gate 2", parsedMaterials[0].remarks)
    }

    @Test
    fun siteExpenseRepository_parseFromOcrText_handlesCorruptedOrEmptyGracefully() {
        // Plain OCR text without delimiters should return empty lists without crashing
        val plainText = "Invoice from ABC Suppliers. Date: 2026-11-23. Total: 5000"
        val expenses = com.example.data.SiteExpenseRepository.parseExpensesFromOcrText(plainText)
        val materials = com.example.data.SiteExpenseRepository.parseMaterialsFromOcrText(plainText)
        assertTrue(expenses.isEmpty())
        assertTrue(materials.isEmpty())

        // Delimiter present but malformed JSON
        val malformedText = """
            === APEX_EXPENSES_DATA_V1 ===
            not a json array
            === APEX_MATERIALS_DATA_V1 ===
            {invalid json}
            === END_APEX_DATA ===
        """.trimIndent()
        val malformedExp = com.example.data.SiteExpenseRepository.parseExpensesFromOcrText(malformedText)
        val malformedMat = com.example.data.SiteExpenseRepository.parseMaterialsFromOcrText(malformedText)
        assertTrue(malformedExp.isEmpty())
        assertTrue(malformedMat.isEmpty())
    }

    @Test
    fun siteExpenseRepository_formatIsoDateHeaderAndChipDate_formatsProperly() {
        val header = com.example.data.SiteExpenseRepository.formatIsoDateHeader("2026-11-23")
        assertEquals("23 November 2026", header)

        val chip = com.example.data.SiteExpenseRepository.formatChipDate("2026-11-23")
        assertNotNull(chip)
        assertTrue(chip.isNotBlank())
    }

    @Test
    fun siteExpenseRepository_syncFromCloudDocuments_handlesEmptyDeletionsAndReplacements() = kotlinx.coroutines.runBlocking {
        var deletedDateExpenses = ""
        var deletedDateMaterials = ""
        var insertedExpenseCount = 0

        val fakeDao = object : com.example.data.local.SiteExpenseDao {
            override fun getAllExpensesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteExpenseEntity>>()
            override fun getExpensesByDateFlow(dateIso: String) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteExpenseEntity>>()
            override fun getAllMaterialsFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteMaterialRecordEntity>>()
            override fun getMaterialsByDateFlow(dateIso: String) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteMaterialRecordEntity>>()
            override suspend fun insertExpense(expense: com.example.data.local.SiteExpenseEntity) {}
            override suspend fun insertExpenses(expenses: List<com.example.data.local.SiteExpenseEntity>) {
                insertedExpenseCount += expenses.size
            }
            override suspend fun insertDefaultExpenses(expenses: List<com.example.data.local.SiteExpenseEntity>) {}
            override suspend fun deleteExpense(id: String) {}
            override suspend fun insertMaterial(material: com.example.data.local.SiteMaterialRecordEntity) {}
            override suspend fun insertMaterials(materials: List<com.example.data.local.SiteMaterialRecordEntity>) {}
            override suspend fun deleteMaterial(id: String) {}
            override suspend fun getCount() = 0
            override suspend fun getMaterialCount() = 0
            override suspend fun getExpensesByDate(dateIso: String) = emptyList<com.example.data.local.SiteExpenseEntity>()
            override suspend fun getAllExpenseDates() = emptyList<String>()
            override suspend fun deleteExpensesForDate(dateIso: String) {
                deletedDateExpenses = dateIso
            }
            override suspend fun deleteExpensesNotIn(dateIso: String, validIds: List<String>) {}
            override suspend fun getMaterialsByDate(dateIso: String) = emptyList<com.example.data.local.SiteMaterialRecordEntity>()
            override suspend fun deleteMaterialsForDate(dateIso: String) {
                deletedDateMaterials = dateIso
            }
            override suspend fun deleteMaterialsNotIn(dateIso: String, validIds: List<String>) {}
        }

        val repo = com.example.data.SiteExpenseRepository(fakeDao)

        // Cloud doc with empty expenses and empty materials
        val emptyDoc = com.example.model.ScannedDocument(
            id = "daily_expenses_2026_11_23",
            documentDateIso = "2026-11-23",
            ocrFullText = "${com.example.data.SiteExpenseRepository.EXPENSES_DELIMITER_START}\n[]\n${com.example.data.SiteExpenseRepository.MATERIALS_DELIMITER_START}\n[]\n${com.example.data.SiteExpenseRepository.DATA_DELIMITER_END}"
        )

        repo.syncFromCloudDocuments(listOf(emptyDoc))
        assertEquals("2026-11-23", deletedDateExpenses)
        assertEquals("2026-11-23", deletedDateMaterials)
        assertEquals(0, insertedExpenseCount)
    }

    @Test
    fun isExpenseTabDocument_hidesExpenseSyncDocsAndKeepsGatePassesAndSlips() {
        val dailyKharchaDoc = ScannedDocument(
            id = "daily_expenses_2026_11_23",
            title = "Daily Kharcha — 23 November 2026",
            category = DocumentCategory.SITE_REPORT.name
        )
        val expenseReportDoc = ScannedDocument(
            id = "expense_report_2026_11_23",
            title = "Daily Site Expenses — 23 Nov 2026",
            category = DocumentCategory.SITE_REPORT.name
        )
        val delimiterDoc = ScannedDocument(
            id = "custom_sync_id",
            title = "Custom Sync Sheet",
            ocrFullText = "${com.example.data.SiteExpenseRepository.EXPENSES_DELIMITER_START}\n[]"
        )
        val gatePassDoc = ScannedDocument(
            id = "apex_doc_07_slip_14146",
            title = "Gate Pass 14146",
            category = DocumentCategory.DELIVERY_CHALLAN.name
        )
        val siteNoteDoc = ScannedDocument(
            id = "apex_doc_13_omfa_purlin",
            title = "Site Note - Purlin 18 No (260/-)",
            category = DocumentCategory.SITE_REPORT.name
        )
        val scannedSlipDoc = ScannedDocument(
            id = "apex_doc_16_nut_bolt_slip",
            title = "Expense Slip - Nut Bolt (870/-)",
            category = DocumentCategory.INVOICE_RECEIPT.name
        )

        assertTrue(DocumentRepository.isExpenseTabDocument(dailyKharchaDoc))
        assertTrue(DocumentRepository.isExpenseTabDocument(expenseReportDoc))
        assertTrue(DocumentRepository.isExpenseTabDocument(delimiterDoc))
        assertFalse(DocumentRepository.isExpenseTabDocument(gatePassDoc))
        assertFalse(DocumentRepository.isExpenseTabDocument(siteNoteDoc))
        assertFalse(DocumentRepository.isExpenseTabDocument(scannedSlipDoc))
        assertFalse(DocumentRepository.isHiddenFromVault(gatePassDoc))
        assertTrue(DocumentRepository.isHiddenFromVault(dailyKharchaDoc))
    }

    @Test
    fun siteLocationParser_parsesDirectCoordsMapsUrlsAndPlusCodes() {
        val direct = com.example.data.model.SiteLocationParser.parseUserInput("28.264438, 78.048938")
        assertEquals(28.264438, direct.latitude!!, 0.0001)
        assertEquals(78.048938, direct.longitude!!, 0.0001)
        assertEquals("", direct.googleMapsUrl)

        val atUrl = com.example.data.model.SiteLocationParser.parseUserInput(
            "https://www.google.com/maps/place/Test/@28.401234,78.123456,17z"
        )
        assertEquals(28.401234, atUrl.latitude!!, 0.0001)
        assertEquals(78.123456, atUrl.longitude!!, 0.0001)
        assertTrue(atUrl.googleMapsUrl.startsWith("https://www.google.com/maps/place"))

        val plusCode = com.example.data.model.SiteLocationParser.decodeFullPlusCode("7JWW727X+QH")
        assertNotNull(plusCode)
        assertEquals(28.2644375, plusCode!!.first, 0.0001)
        assertEquals(78.0489375, plusCode.second, 0.0001)

        val editedInput = com.example.data.model.SiteLocationParser.parseUserInput(
            "https://maps.app.goo.gl/rctKU9tbs7g4oYvn8?g_st=ac (28.264438, 78.048938)"
        )
        assertEquals("https://maps.app.goo.gl/rctKU9tbs7g4oYvn8?g_st=ac", editedInput.googleMapsUrl)
        assertEquals(28.264438, editedInput.latitude!!, 0.0001)
        assertEquals(78.048938, editedInput.longitude!!, 0.0001)

        val placePathUrl = com.example.data.model.SiteLocationParser.parseUserInput(
            "https://www.google.com/maps/place/28.112462,78.230293/data=!4m6!3m5!1s0!7e2"
        )
        assertEquals(28.112462, placePathUrl.latitude!!, 0.0001)
        assertEquals(78.230293, placePathUrl.longitude!!, 0.0001)
    }

    @Test
    fun siteLocationRepository_serializesParsesAndHidesFromVault() {
        val defaultSites = com.example.data.SiteLocationRepository.DEFAULT_SITE_LOCATIONS
        assertEquals(5, defaultSites.size)
        val defaultStore = com.example.data.SiteLocationRepository.DEFAULT_MATERIAL_STORE
        val syncText = com.example.data.SiteLocationRepository.formatCloudSyncText(defaultSites)
        val parsed = com.example.data.SiteLocationRepository.parseLocationsFromOcrText(syncText)

        assertEquals(5, parsed.size)
        assertEquals(defaultStore.id, parsed[0].id)
        assertEquals("Main Material Store (Shikarpur)", parsed[0].name)
        assertEquals(defaultStore.latitude!!, parsed[0].latitude!!, 0.0001)
        assertEquals(defaultStore.longitude!!, parsed[0].longitude!!, 0.0001)
        assertEquals(defaultStore.googleMapsUrl, parsed[0].googleMapsUrl)
        assertTrue(parsed[0].isPinned)
        assertEquals("Company Store (Chigrawati, Siyana)", parsed[1].name)
        assertEquals(28.597328, parsed[1].latitude!!, 0.0001)
        assertEquals(78.0301394, parsed[1].longitude!!, 0.0001)
        assertTrue(parsed[1].isPinned)
        assertEquals("Pilkhana Site 1", parsed[2].name)
        assertEquals("Umrari Site 2", parsed[3].name)
        assertEquals("Harjipura Site 3", parsed[4].name)

        val legacySites = listOf(
            defaultStore.copy(name = "Main Material Store (Barauli)"),
            com.example.data.SiteLocationRepository.DEFAULT_COMPANY_STORE.copy(
                name = "Company Store (Chigrawati)",
                addressOrVillage = "Chigrawati, Syana, Bulandshahr, UP 203412"
            )
        )
        assertTrue(com.example.data.SiteLocationRepository.hasLegacyStoreNames(legacySites))
        assertEquals(
            "Main Material Store (Shikarpur)",
            com.example.data.SiteLocationRepository.upgradeLegacyStoreLocation(legacySites[0])?.name
        )
        assertEquals(
            "Company Store (Chigrawati, Siyana)",
            com.example.data.SiteLocationRepository.upgradeLegacyStoreLocation(legacySites[1])?.name
        )
        assertFalse(com.example.data.SiteLocationRepository.hasLegacyStoreNames(defaultSites))

        val syncDoc = ScannedDocument(
            id = com.example.data.SiteLocationRepository.CLOUD_SYNC_DOC_ID,
            title = "Apex Solar Stored Site Locations & GPS",
            category = DocumentCategory.SITE_REPORT.name,
            ocrFullText = syncText
        )
        assertTrue(DocumentRepository.isSiteLocationSyncDocument(syncDoc))
        assertTrue(DocumentRepository.isHiddenFromVault(syncDoc))
    }

    @Test
    fun siteLocationRepository_syncFromCloudDocuments_handlesUpdatesAndDeletions() = kotlinx.coroutines.runBlocking {
        val storeMap = linkedMapOf<String, com.example.data.local.SiteLocationEntity>()
        val fakeDao = object : com.example.data.local.SiteLocationDao {
            override fun getAllSitesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.SiteLocationEntity>>()
            override suspend fun getAllSites() = storeMap.values.toList()
            override suspend fun insertSite(site: com.example.data.local.SiteLocationEntity) {
                storeMap[site.id] = site
            }
            override suspend fun insertSites(sites: List<com.example.data.local.SiteLocationEntity>) {
                sites.forEach { storeMap[it.id] = it }
            }
            override suspend fun insertDefaultSites(sites: List<com.example.data.local.SiteLocationEntity>) {
                sites.forEach { storeMap.putIfAbsent(it.id, it) }
            }
            override suspend fun deleteSite(id: String) {
                storeMap.remove(id)
            }
            override suspend fun deleteSitesNotIn(validIds: List<String>) {
                val keep = validIds.toSet()
                storeMap.keys.removeAll { it !in keep }
            }
            override suspend fun deleteAllSites() {
                storeMap.clear()
            }
            override suspend fun getCount() = storeMap.size
        }

        val repo = com.example.data.SiteLocationRepository(fakeDao)
        assertTrue(repo.seedDefaultSitesIfNeeded())
        assertEquals(5, fakeDao.getCount())
        assertFalse(repo.seedDefaultSitesIfNeeded())

        val subset = listOf(
            com.example.data.SiteLocationRepository.DEFAULT_MATERIAL_STORE,
            com.example.data.SiteLocationRepository.DEFAULT_PILKHANA_SITE_1
        )
        val partialDoc = ScannedDocument(
            id = com.example.data.SiteLocationRepository.CLOUD_SYNC_DOC_ID,
            title = "Apex Solar Site Locations",
            category = DocumentCategory.SITE_REPORT.name,
            ocrFullText = com.example.data.SiteLocationRepository.formatCloudSyncText(subset)
        )
        repo.syncFromCloudDocuments(listOf(partialDoc))
        assertEquals(2, fakeDao.getCount())
        assertTrue(storeMap.containsKey("site_main_material_store"))
        assertTrue(storeMap.containsKey("site_pilkhana_1"))
        assertFalse(storeMap.containsKey("site_company_store"))

        val emptyDoc = partialDoc.copy(
            ocrFullText = com.example.data.SiteLocationRepository.formatCloudSyncText(emptyList())
        )
        repo.syncFromCloudDocuments(listOf(emptyDoc))
        assertEquals(0, fakeDao.getCount())
    }

    @Test
    fun appThemeMode_parsesAndTogglesBetweenDayAndNight() {
        assertEquals(com.example.ui.theme.AppThemeMode.SYSTEM, com.example.ui.theme.AppThemeMode.fromStorageKey(null))
        assertEquals(com.example.ui.theme.AppThemeMode.SYSTEM, com.example.ui.theme.AppThemeMode.fromStorageKey("unknown"))
        assertEquals(com.example.ui.theme.AppThemeMode.LIGHT, com.example.ui.theme.AppThemeMode.fromStorageKey("light"))
        assertEquals(com.example.ui.theme.AppThemeMode.DARK, com.example.ui.theme.AppThemeMode.fromStorageKey("dark"))

        assertEquals(com.example.ui.theme.AppThemeMode.DARK, com.example.ui.theme.AppThemeMode.nextManualToggle(currentlyDark = false))
        assertEquals(com.example.ui.theme.AppThemeMode.LIGHT, com.example.ui.theme.AppThemeMode.nextManualToggle(currentlyDark = true))

        assertEquals(com.example.ui.theme.AppThemeMode.DARK, com.example.ui.theme.AppThemeMode.nextCycle(com.example.ui.theme.AppThemeMode.SYSTEM))
        assertEquals(com.example.ui.theme.AppThemeMode.LIGHT, com.example.ui.theme.AppThemeMode.nextCycle(com.example.ui.theme.AppThemeMode.DARK))
        assertEquals(com.example.ui.theme.AppThemeMode.SYSTEM, com.example.ui.theme.AppThemeMode.nextCycle(com.example.ui.theme.AppThemeMode.LIGHT))
    }
}
