package com.example.data

import android.content.Context
import com.example.data.local.SiteLocationDao
import com.example.data.local.SiteLocationEntity
import com.example.data.model.SiteLocation
import com.example.data.model.SiteLocationParser
import com.example.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class SiteLocationRepository(
    private val dao: SiteLocationDao,
    private val appContext: Context? = null
) {
    fun allSitesFlow(): Flow<List<SiteLocation>> =
        dao.getAllSitesFlow().map { list -> list.map { it.toModel() } }

    suspend fun getAllSites(): List<SiteLocation> = withContext(Dispatchers.IO) {
        dao.getAllSites().map { it.toModel() }
    }

    suspend fun upsertSite(site: SiteLocation): SiteLocation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val normalized = site.copy(
            id = site.id.ifBlank { "site_${UUID.randomUUID().toString().take(8)}" },
            name = site.name.trim().take(120),
            addressOrVillage = site.addressOrVillage.trim().take(240),
            googleMapsUrl = site.googleMapsUrl.trim().take(500),
            notes = site.notes.trim().take(400),
            updatedAtMillis = now
        )
        dao.insertSite(SiteLocationEntity.fromModel(normalized))
        normalized
    }

    suspend fun deleteSite(id: String) = withContext(Dispatchers.IO) {
        dao.deleteSite(id)
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSyncedSitePackToCloud(): Boolean =
        prefs()?.getBoolean(KEY_SYNCED_PACK_V2_7_TO_CLOUD, false) ?: false

    fun markSitePackSyncedToCloud() {
        prefs()?.edit()?.putBoolean(KEY_SYNCED_PACK_V2_7_TO_CLOUD, true)?.apply()
    }

    suspend fun seedDefaultSitesIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val prefs = prefs()
        val alreadySeeded = prefs?.getBoolean(KEY_SEEDED_PACK_V2_7, false) ?: false
        val shouldSeed = !alreadySeeded && (prefs != null || dao.getCount() == 0)
        if (shouldSeed) {
            dao.insertDefaultSites(DEFAULT_SITE_LOCATIONS.map { SiteLocationEntity.fromModel(it) })
            prefs?.edit()?.putBoolean(KEY_SEEDED_PACK_V2_7, true)?.apply()
            true
        } else {
            false
        }
    }

    suspend fun insertSitesIfMissing(sites: List<SiteLocation>) = withContext(Dispatchers.IO) {
        if (sites.isNotEmpty()) {
            dao.insertDefaultSites(sites.map { SiteLocationEntity.fromModel(it) })
        }
    }

    suspend fun seedDefaultStoreIfEmpty() {
        seedDefaultSitesIfNeeded()
    }

    /**
     * Synchronizes cloud-backed site locations (`site_locations_master`) into local Room DB
     * so UP and Mumbai devices stay identical in real time without altering firestore.rules.
     */
    suspend fun syncFromCloudDocuments(docs: List<ScannedDocument>) = withContext(Dispatchers.IO) {
        val syncDoc = docs.firstOrNull {
            it.id == CLOUD_SYNC_DOC_ID || it.ocrFullText.contains(LOCATIONS_DELIMITER_START)
        } ?: return@withContext

        if (!syncDoc.ocrFullText.contains(LOCATIONS_DELIMITER_START)) return@withContext
        val parsed = parseLocationsFromOcrText(syncDoc.ocrFullText)
        val cloudContainsV27Sites = parsed.any { it.id in V2_7_ADDED_SITE_IDS }
        if (cloudContainsV27Sites) {
            markSitePackSyncedToCloud()
        }

        if (parsed.isNotEmpty()) {
            val validIds = parsed.map { it.id }.filter { it.isNotBlank() }
            if (validIds.isNotEmpty()) {
                val guardNewPackFromStaleV26Cloud =
                    appContext != null && !hasSyncedSitePackToCloud() && !cloudContainsV27Sites
                if (guardNewPackFromStaleV26Cloud) {
                    dao.insertSites(parsed.map { SiteLocationEntity.fromModel(it) })
                    dao.insertDefaultSites(DEFAULT_SITE_LOCATIONS.map { SiteLocationEntity.fromModel(it) })
                } else {
                    dao.deleteSitesNotIn(validIds)
                    dao.insertSites(parsed.map { SiteLocationEntity.fromModel(it) })
                }
            }
        } else if (appContext == null || hasSyncedSitePackToCloud()) {
            dao.deleteAllSites()
        }
    }

    /**
     * Resolves shortened Google Maps share links (e.g. `https://maps.app.goo.gl/...`) on IO
     * to extract GPS coordinates (`lat`, `lng`) and place label when possible, always preserving
     * the original link on failure or offline.
     */
    suspend fun resolveGoogleMapsLink(url: String): SiteLocationParser.ParsedInput =
        withContext(Dispatchers.IO) {
            val direct = SiteLocationParser.parseUserInput(url)
            if (direct.latitude != null && direct.longitude != null) return@withContext direct
            val targetUrl = direct.googleMapsUrl
            if (targetUrl.isBlank()) return@withContext direct

            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val finalUrl = URLDecoder.decode(resp.request.url.toString(), "UTF-8")
                    val fromFinalUrl = SiteLocationParser.parseCoordinates(finalUrl)
                    if (fromFinalUrl != null && !isOverviewFallbackCoord(fromFinalUrl)) {
                        return@withContext direct.copy(
                            latitude = fromFinalUrl.first,
                            longitude = fromFinalUrl.second
                        )
                    }
                    // Look for 4+2 local Plus Code paired with Bulandshahr/UP area or @lat,lng in body
                    val bodySnippet = resp.body?.string()?.take(24_000) ?: ""
                    val fullPlusInUrl = Regex("""\b([23456789CFGHJMPQRVWX]{4}\+[23456789CFGHJMPQRVWX]{2,3})\b""")
                        .find(finalUrl)
                    if (fullPlusInUrl != null) {
                        if (finalUrl.contains("203", ignoreCase = true) ||
                            finalUrl.contains("Bulandshahr", ignoreCase = true) ||
                            finalUrl.contains("Uttar Pradesh", ignoreCase = true)
                        ) {
                            SiteLocationParser.decodeFullPlusCode("7JWW" + fullPlusInUrl.groupValues[1])?.let {
                                return@withContext direct.copy(latitude = it.first, longitude = it.second)
                            }
                        }
                    }
                    val atMatch = Regex("""@([-+]?\d{1,2}\.\d{4,}),([-+]?\d{1,3}\.\d{4,})""").find(bodySnippet)
                    if (atMatch != null) {
                        val lat = atMatch.groupValues[1].toDoubleOrNull()
                        val lng = atMatch.groupValues[2].toDoubleOrNull()
                        if (SiteLocationParser.isValidLatLng(lat, lng) && !isOverviewFallbackCoord(lat!! to lng!!)) {
                            return@withContext direct.copy(latitude = lat, longitude = lng)
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep original Google Maps URL cleanly on offline / timeout
            }
            direct
        }

    private fun isOverviewFallbackCoord(coord: Pair<Double, Double>): Boolean {
        // Google Maps unrendered regional viewport fallback around Agra (27.1761745, 77.9800129)
        return kotlin.math.abs(coord.first - 27.1761745) < 0.001 &&
            kotlin.math.abs(coord.second - 77.9800129) < 0.001
    }

    companion object {
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()
        }

        private const val PREFS_NAME = "apex_site_prefs"
        private const val KEY_SEEDED_PACK_V2_7 = "seeded_site_pack_v2_7"
        private const val KEY_SYNCED_PACK_V2_7_TO_CLOUD = "synced_site_pack_v2_7_to_cloud"

        const val CLOUD_SYNC_DOC_ID = "site_locations_master"
        const val LOCATIONS_DELIMITER_START = "=== APEX_SITE_LOCATIONS_V1 ==="
        const val LOCATIONS_DELIMITER_END = "=== END_APEX_SITE_LOCATIONS ==="

        val DEFAULT_MATERIAL_STORE = SiteLocation(
            id = "site_main_material_store",
            name = "Main Material Store (Barauli)",
            siteType = SiteLocation.TYPE_STORE,
            addressOrVillage = "Chodhary Dhaba, Barauli, Shikarpur, Bulandshahr, UP 203395",
            latitude = 28.264438,
            longitude = 78.048938,
            googleMapsUrl = "https://maps.app.goo.gl/rctKU9tbs7g4oYvn8?g_st=ac",
            notes = "Main Godown — All Solar Panels, DC Cables, Structures, Bolts & Hardware stored here (Plus Code: 727X+QH).",
            isPinned = true
        )

        val DEFAULT_COMPANY_STORE = SiteLocation(
            id = "site_company_store",
            name = "Company Store (Chigrawati)",
            siteType = SiteLocation.TYPE_DEPOT,
            addressOrVillage = "Chigrawati, Syana, Bulandshahr, UP 203412",
            latitude = 28.597328,
            longitude = 78.0301394,
            googleMapsUrl = "https://maps.app.goo.gl/iRoa1qusLK4eCTSU9?g_st=ac",
            notes = "Company Store — Place where solar materials are purchased / picked up.",
            isPinned = true
        )

        val DEFAULT_PILKHANA_SITE_1 = SiteLocation(
            id = "site_pilkhana_1",
            name = "Pilkhana Site 1",
            siteType = SiteLocation.TYPE_SOLAR_SITE,
            addressOrVillage = "Pilkhana, Debai, Bulandshahr, UP 203393",
            latitude = 28.216396,
            longitude = 78.339329,
            googleMapsUrl = "https://maps.app.goo.gl/Rsz1G9E5UhKE2SfW7?g_st=aw",
            notes = "Solar Site 1 — Pilkhana",
            isPinned = false
        )

        val DEFAULT_UMRARI_SITE_2 = SiteLocation(
            id = "site_umrari_2",
            name = "Umrari Site 2",
            siteType = SiteLocation.TYPE_SOLAR_SITE,
            addressOrVillage = "Umrari, Debai, Bulandshahr, UP 203393",
            latitude = 28.160205,
            longitude = 78.302028,
            googleMapsUrl = "",
            notes = "Solar Site 2 — Umrari (Chilmapur Umrari / Debai block)",
            isPinned = false
        )

        val DEFAULT_HARJIPURA_SITE_3 = SiteLocation(
            id = "site_harjipura_3",
            name = "Harjipura Site 3",
            siteType = SiteLocation.TYPE_SOLAR_SITE,
            addressOrVillage = "Harjipura, Debai, Bulandshahr, UP 203393",
            latitude = 28.112462,
            longitude = 78.230293,
            googleMapsUrl = "https://maps.app.goo.gl/7DoLMHT7d6pgtozbA?g_st=ac",
            notes = "Solar Site 3 — Harjipura",
            isPinned = false
        )

        val V2_7_ADDED_SITE_IDS = setOf(
            "site_company_store",
            "site_pilkhana_1",
            "site_umrari_2",
            "site_harjipura_3"
        )

        val DEFAULT_SITE_LOCATIONS = listOf(
            DEFAULT_MATERIAL_STORE,
            DEFAULT_COMPANY_STORE,
            DEFAULT_PILKHANA_SITE_1,
            DEFAULT_UMRARI_SITE_2,
            DEFAULT_HARJIPURA_SITE_3
        )

        fun serializeLocationsToJson(locations: List<SiteLocation>): String {
            val array = JSONArray()
            for (loc in locations) {
                val obj = JSONObject()
                obj.put("id", loc.id)
                obj.put("name", loc.name)
                obj.put("siteType", loc.siteType)
                obj.put("addressOrVillage", loc.addressOrVillage)
                if (loc.latitude != null) obj.put("latitude", loc.latitude) else obj.put("latitude", JSONObject.NULL)
                if (loc.longitude != null) obj.put("longitude", loc.longitude) else obj.put("longitude", JSONObject.NULL)
                obj.put("googleMapsUrl", loc.googleMapsUrl)
                obj.put("notes", loc.notes)
                obj.put("isPinned", loc.isPinned)
                obj.put("createdAtMillis", loc.createdAtMillis)
                obj.put("updatedAtMillis", loc.updatedAtMillis)
                array.put(obj)
            }
            return array.toString()
        }

        fun parseLocationsFromOcrText(ocrText: String): List<SiteLocation> {
            if (!ocrText.contains(LOCATIONS_DELIMITER_START)) return emptyList()
            val startIdx = ocrText.indexOf(LOCATIONS_DELIMITER_START) + LOCATIONS_DELIMITER_START.length
            val endIdx = if (ocrText.contains(LOCATIONS_DELIMITER_END)) {
                ocrText.indexOf(LOCATIONS_DELIMITER_END, startIdx).let { if (it >= 0) it else ocrText.length }
            } else {
                ocrText.length
            }
            val jsonStr = ocrText.substring(startIdx, endIdx).trim()
            if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()

            return try {
                val arr = JSONArray(jsonStr)
                val out = mutableListOf<SiteLocation>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val lat = if (obj.isNull("latitude")) null else obj.optDouble("latitude").takeIf { !it.isNaN() }
                    val lng = if (obj.isNull("longitude")) null else obj.optDouble("longitude").takeIf { !it.isNaN() }
                    out.add(
                        SiteLocation(
                            id = obj.optString("id", ""),
                            name = obj.optString("name", ""),
                            siteType = obj.optString("siteType", SiteLocation.TYPE_STORE),
                            addressOrVillage = obj.optString("addressOrVillage", ""),
                            latitude = lat,
                            longitude = lng,
                            googleMapsUrl = obj.optString("googleMapsUrl", ""),
                            notes = obj.optString("notes", ""),
                            isPinned = obj.optBoolean("isPinned", false),
                            createdAtMillis = obj.optLong("createdAtMillis", 0L),
                            updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                        )
                    )
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun formatCloudSyncText(locations: List<SiteLocation>): String = buildString {
            append(LOCATIONS_DELIMITER_START).append('\n')
            append(serializeLocationsToJson(locations)).append('\n')
            append(LOCATIONS_DELIMITER_END).append("\n\n")
            append("APEX SOLAR WORK — STORED SITE LOCATIONS & GPS\n")
            locations.forEachIndexed { idx, site ->
                append("${idx + 1}. ${site.name} [${SiteLocation.typeDisplayLabel(site.siteType)}]\n")
                if (site.addressOrVillage.isNotBlank()) append("   Address: ${site.addressOrVillage}\n")
                if (site.hasCoordinates) append("   GPS: ${site.formattedCoordinates()}\n")
                if (site.googleMapsUrl.isNotBlank()) append("   Map: ${site.googleMapsUrl}\n")
                if (site.notes.isNotBlank()) append("   Notes: ${site.notes}\n")
            }
        }.take(50000)

        fun formatWhatsAppShareText(site: SiteLocation): String = buildString {
            append("📍 *${site.name}*\n")
            append("🏷️ ${SiteLocation.typeDisplayLabel(site.siteType)}\n")
            if (site.addressOrVillage.isNotBlank()) {
                append("📌 ${site.addressOrVillage}\n")
            }
            if (site.hasCoordinates) {
                append("🧭 GPS: `${site.formattedCoordinates()}`\n")
            }
            val mapUrl = site.canonicalMapsWebUrl()
            if (mapUrl.isNotBlank()) {
                append("🗺️ Google Maps: $mapUrl\n")
            }
            if (site.notes.isNotBlank()) {
                append("📝 ${site.notes}")
            }
        }.trim()
    }
}
