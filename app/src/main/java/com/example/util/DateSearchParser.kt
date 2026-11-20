package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

data class ParsedDateQuery(
    val rawInput: String,
    val primaryIso: String,
    val alternateIso: String? = null
)

data class ParsedDateRange(
    val startIso: String,
    val endIso: String
)

object DateSearchParser {

    private val isoRegex = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""")
    private val dmyRegex = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})$""")
    private val textMonthFirstRegex = Regex("""^([A-Za-z]{3,9})\s+(\d{1,2}),?\s+(\d{4})$""")
    private val textDayFirstRegex = Regex("""^(\d{1,2})\s+([A-Za-z]{3,9}),?\s+(\d{4})$""")

    private val monthMap = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    private val monthNamesShort = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private val monthNamesFull = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun todayIso(): String {
        val cal = Calendar.getInstance()
        return buildIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun daysAgoIso(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo.coerceAtLeast(0))
        return buildIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun startOfCurrentMonthIso(): String {
        val cal = Calendar.getInstance()
        return buildIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            1
        )
    }

    fun isValidIsoDate(iso: String): Boolean {
        val parts = iso.split("-")
        if (parts.size != 3) return false
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        return isValidCalendarDate(y, m, d)
    }

    /**
     * Parses user-typed dates such as "12/1/2026", "24/02/2026", "24-2-2026",
     * "2026-01-12", "12 Jan 2026", or "Jan 12 2026".
     * Defaults to DD/MM/YYYY (matching Indian engineering site forms), while providing
     * an optional alternateIso (MM/DD/YYYY) whenever both numbers are <= 12 and differ.
     */
    fun parseDateQuery(raw: String): ParsedDateQuery? {
        val trimmed = raw.trim()
        if (trimmed.length < 6) return null

        isoRegex.matchEntire(trimmed)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return null
            val month = m.groupValues[2].toIntOrNull() ?: return null
            val day = m.groupValues[3].toIntOrNull() ?: return null
            if (isValidCalendarDate(year, month, day)) {
                return ParsedDateQuery(trimmed, buildIso(year, month, day))
            }
            return null
        }

        dmyRegex.matchEntire(trimmed)?.let { m ->
            val first = m.groupValues[1].toIntOrNull() ?: return null
            val second = m.groupValues[2].toIntOrNull() ?: return null
            val rawYear = m.groupValues[3].toIntOrNull() ?: return null
            val year = if (rawYear < 100) 2000 + rawYear else rawYear

            val dmyValid = isValidCalendarDate(year, second, first)
            val mdyValid = isValidCalendarDate(year, first, second)

            return when {
                dmyValid && mdyValid && first != second -> ParsedDateQuery(
                    rawInput = trimmed,
                    primaryIso = buildIso(year, second, first),
                    alternateIso = buildIso(year, first, second)
                )
                dmyValid -> ParsedDateQuery(trimmed, buildIso(year, second, first))
                mdyValid -> ParsedDateQuery(trimmed, buildIso(year, first, second))
                else -> null
            }
        }

        textDayFirstRegex.matchEntire(trimmed)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return null
            val month = monthMap[m.groupValues[2].lowercase(Locale.US)] ?: return null
            val year = m.groupValues[3].toIntOrNull() ?: return null
            if (isValidCalendarDate(year, month, day)) {
                return ParsedDateQuery(trimmed, buildIso(year, month, day))
            }
            return null
        }

        textMonthFirstRegex.matchEntire(trimmed)?.let { m ->
            val month = monthMap[m.groupValues[1].lowercase(Locale.US)] ?: return null
            val day = m.groupValues[2].toIntOrNull() ?: return null
            val year = m.groupValues[3].toIntOrNull() ?: return null
            if (isValidCalendarDate(year, month, day)) {
                return ParsedDateQuery(trimmed, buildIso(year, month, day))
            }
            return null
        }

        return null
    }

    /**
     * Parses a user-typed date range such as "12/1/2026 - 24/2/2026",
     * "12/1/2026 to 24/2/2026", or "2026-01-01..2026-01-31".
     * Automatically orders startIso <= endIso.
     */
    fun parseDateRangeQuery(raw: String): ParsedDateRange? {
        val trimmed = raw.trim()
        if (trimmed.length < 13) return null
        val rangeSeparator = Regex("""\s+(?:to|-|–|—)\s+|\.\.""")
        val parts = rangeSeparator.split(trimmed)
        if (parts.size != 2) return null
        val first = parseDateQuery(parts[0])?.primaryIso ?: return null
        val second = parseDateQuery(parts[1])?.primaryIso ?: return null
        return if (first <= second) {
            ParsedDateRange(startIso = first, endIso = second)
        } else {
            ParsedDateRange(startIso = second, endIso = first)
        }
    }

    fun normalizeToIsoOrEmpty(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return parseDateQuery(raw)?.primaryIso ?: ""
    }

    /** Formats "2026-02-24" -> "24/02/2026" */
    fun formatSlashDate(iso: String): String {
        val parts = iso.split("-")
        if (parts.size != 3) return iso
        return "${parts[2]}/${parts[1]}/${parts[0]}"
    }

    /** Formats "2026-02-24" -> "24 Feb 2026" */
    fun formatFriendlyDate(iso: String): String {
        val parts = iso.split("-")
        if (parts.size != 3) return iso
        val year = parts[0].toIntOrNull() ?: return iso
        val month = parts[1].toIntOrNull() ?: return iso
        val day = parts[2].toIntOrNull() ?: return iso
        if (month !in 1..12) return iso
        return "$day ${monthNamesShort[month - 1]} $year"
    }

    fun formatMonthYear(year: Int, month1Based: Int): String {
        val safeMonth = month1Based.coerceIn(1, 12)
        return "${monthNamesFull[safeMonth - 1]} $year"
    }

    fun utcMillisToIso(utcMillis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = utcMillis
        return buildIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun isoToUtcMillis(iso: String): Long? {
        val parts = iso.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (!isValidCalendarDate(y, m, d)) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(y, m - 1, d, 0, 0, 0)
        return cal.timeInMillis
    }

    fun daysInMonth(year: Int, month1Based: Int): Int {
        val cal = GregorianCalendar(year, month1Based - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Returns 0 for Sunday .. 6 for Saturday */
    fun firstDayOfWeekOffset(year: Int, month1Based: Int): Int {
        val cal = GregorianCalendar(year, month1Based - 1, 1)
        return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    fun buildIso(year: Int, month: Int, day: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    fun parseYearMonth(iso: String): Pair<Int, Int> {
        val parts = iso.split("-")
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (y != null && m != null && m in 1..12) return y to m
        }
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    fun formatTimestampShort(date: Date): String {
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        return fmt.format(date)
    }

    private fun isValidCalendarDate(year: Int, month: Int, day: Int): Boolean {
        if (year !in 1900..2100 || month !in 1..12 || day !in 1..31) return false
        return try {
            val cal = GregorianCalendar()
            cal.isLenient = false
            cal.set(year, month - 1, day)
            cal.time
            true
        } catch (_: Exception) {
            false
        }
    }
}
