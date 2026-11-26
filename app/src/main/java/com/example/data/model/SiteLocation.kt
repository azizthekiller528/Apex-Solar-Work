package com.example.data.model

import java.util.Locale

data class SiteLocation(
    val id: String = "",
    val name: String = "",
    val siteType: String = TYPE_STORE, // "STORE", "SOLAR_SITE", "DEPOT", "OTHER"
    val addressOrVillage: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val googleMapsUrl: String = "",
    val notes: String = "",
    val isPinned: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null

    fun formattedCoordinates(): String {
        val lat = latitude ?: return ""
        val lng = longitude ?: return ""
        return String.format(Locale.US, "%.6f, %.6f", lat, lng)
    }

    fun canonicalMapsWebUrl(): String {
        if (googleMapsUrl.isNotBlank()) return googleMapsUrl.trim()
        val lat = latitude
        val lng = longitude
        return if (lat != null && lng != null) {
            String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", lat, lng)
        } else {
            ""
        }
    }

    companion object {
        const val TYPE_STORE = "STORE"
        const val TYPE_SOLAR_SITE = "SOLAR_SITE"
        const val TYPE_DEPOT = "DEPOT"
        const val TYPE_OTHER = "OTHER"

        fun typeDisplayLabel(type: String): String = when (type.uppercase(Locale.US)) {
            TYPE_STORE -> "Material Store"
            TYPE_SOLAR_SITE -> "Solar Site"
            TYPE_DEPOT -> "Kalpataru Depot"
            else -> "Site Location"
        }
    }
}

/**
 * Pure-JVM helpers for parsing GPS coordinates, Google Maps URLs, and Open Location Codes (Plus Codes).
 */
object SiteLocationParser {
    private const val OLC_ALPHABET = "23456789CFGHJMPQRVWX"

    private val DIRECT_COORD_REGEX = Regex(
        """^\s*([-+]?\d{1,2}(?:\.\d+)?)\s*[,;\s]\s*([-+]?\d{1,3}(?:\.\d+)?)\s*$"""
    )
    private val AT_COORD_REGEX = Regex(
        """@([-+]?\d{1,2}\.\d{3,}),([-+]?\d{1,3}\.\d{3,})"""
    )
    private val BANG_3D4D_REGEX = Regex(
        """!3d([-+]?\d{1,2}\.\d{3,})!4d([-+]?\d{1,3}\.\d{3,})"""
    )
    private val QUERY_COORD_REGEX = Regex(
        """(?:q|ll|query|center)=([-+]?\d{1,2}\.\d{3,})(?:,|%2C)([-+]?\d{1,3}\.\d{3,})""",
        RegexOption.IGNORE_CASE
    )
    private val GEO_URI_REGEX = Regex(
        """geo:([-+]?\d{1,2}\.\d{3,}),([-+]?\d{1,3}\.\d{3,})""",
        RegexOption.IGNORE_CASE
    )
    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val FULL_PLUS_CODE_REGEX = Regex("""\b([23456789CFGHJMPQRVWX]{4,8}\+[23456789CFGHJMPQRVWX]{2,4})\b""")

    data class ParsedInput(
        val latitude: Double?,
        val longitude: Double?,
        val googleMapsUrl: String
    )

    fun parseUserInput(rawInput: String): ParsedInput {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ParsedInput(null, null, "")
        val extractedUrl = URL_REGEX.find(trimmed)?.value?.trimEnd(',', '.', ';', ')') ?: ""
        val coords = parseCoordinates(trimmed)
        return ParsedInput(
            latitude = coords?.first,
            longitude = coords?.second,
            googleMapsUrl = extractedUrl
        )
    }

    fun parseCoordinates(text: String): Pair<Double, Double>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val matchers = listOf(DIRECT_COORD_REGEX, GEO_URI_REGEX, BANG_3D4D_REGEX, AT_COORD_REGEX, QUERY_COORD_REGEX)
        for (regex in matchers) {
            val match = regex.find(trimmed) ?: continue
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (isValidLatLng(lat, lng)) {
                return lat!! to lng!!
            }
        }

        // Check full 8+ char Plus Code (e.g., 7JWW727X+QH)
        val plusMatch = FULL_PLUS_CODE_REGEX.find(trimmed.uppercase(Locale.US))
        if (plusMatch != null) {
            decodeFullPlusCode(plusMatch.groupValues[1])?.let { return it }
        }
        return null
    }

    /**
     * Decodes a full Open Location Code (e.g. "7JWW727X+QH") into its center (lat, lng).
     */
    fun decodeFullPlusCode(rawCode: String): Pair<Double, Double>? {
        val clean = rawCode.trim().uppercase(Locale.US).replace("+", "")
        if (clean.length < 8 || clean.length % 2 != 0) return null
        val stepDegrees = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)
        var lat = -90.0
        var lng = -180.0
        val pairs = minOf(clean.length / 2, stepDegrees.size)
        for (i in 0 until pairs) {
            val latIdx = OLC_ALPHABET.indexOf(clean[i * 2])
            val lngIdx = OLC_ALPHABET.indexOf(clean[i * 2 + 1])
            if (latIdx < 0 || lngIdx < 0) return null
            val step = stepDegrees[i]
            lat += latIdx * step
            lng += lngIdx * step
        }
        val lastStep = stepDegrees[pairs - 1]
        lat += lastStep / 2.0
        lng += lastStep / 2.0
        return if (isValidLatLng(lat, lng)) lat to lng else null
    }

    fun isValidLatLng(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        if (lat.isNaN() || lng.isNaN() || lat.isInfinite() || lng.isInfinite()) return false
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) return false
        return !(lat == 0.0 && lng == 0.0)
    }
}
