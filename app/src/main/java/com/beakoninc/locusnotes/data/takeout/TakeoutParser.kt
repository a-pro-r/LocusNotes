package com.beakoninc.locusnotes.data.takeout

/** One row of a Google Takeout "Saved" list CSV. */
data class TakeoutPlace(
    val listName: String,
    val title: String,
    val note: String,
    val url: String,
    val tags: List<String>,
    val comment: String,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    // Takeout "Saved" also exports non-place saves (movie watchlists, reading
    // lists, images); only rows with a Maps URL are actual places
    val isPlace: Boolean get() = url.contains("/maps/")
}

/**
 * Parses Google Takeout "Saved" CSVs (columns: Title,Note,URL,Tags,Comment).
 *
 * Saved places come as Maps URLs with an opaque feature id and no coordinates;
 * dropped pins carry them in the URL (e.g. /maps/search/36.955,-111.893).
 * Rows without coordinates are geocoded later by title.
 */
object TakeoutParser {

    fun parseCsv(listName: String, text: String): List<TakeoutPlace> {
        val rows = parseRows(text.removePrefix("﻿"))
        if (rows.size < 2) return emptyList()
        // Drop the header; Takeout also emits one fully blank data row per file
        return rows.drop(1).mapNotNull { row ->
            val title = row.getOrElse(0) { "" }.trim()
            val note = row.getOrElse(1) { "" }.trim()
            val url = row.getOrElse(2) { "" }.trim()
            val tags = row.getOrElse(3) { "" }
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val comment = row.getOrElse(4) { "" }.trim()

            if (title.isEmpty() && url.isEmpty()) return@mapNotNull null

            val coords = extractCoordinates(url)
            TakeoutPlace(
                listName = listName,
                title = title.ifEmpty { "Saved place" },
                note = note,
                url = url,
                tags = tags,
                comment = comment,
                latitude = coords?.first,
                longitude = coords?.second
            )
        }
    }

    /** RFC 4180: quoted fields may contain commas, newlines, and "" escapes. */
    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false

        fun endField() {
            row.add(field.toString())
            field.clear()
        }

        fun endRow() {
            endField()
            rows.add(row.toList())
            row.clear()
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\r' -> { /* \n ends the row */ }
                    '\n' -> endRow()
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private val searchPath = Regex("""/maps/search/(-?\d{1,3}(?:\.\d+)?),\s*\+?(-?\d{1,3}(?:\.\d+)?)""")
    private val dataLatLng = Regex("""!3d(-?\d{1,3}(?:\.\d+)?)!4d(-?\d{1,3}(?:\.\d+)?)""")
    private val atPath = Regex("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")
    private val qParam = Regex("""[?&]q=(-?\d{1,3}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)""")

    fun extractCoordinates(url: String): Pair<Double, Double>? {
        for (regex in listOf(searchPath, dataLatLng, atPath, qParam)) {
            val match = regex.find(url) ?: continue
            val lat = match.groupValues[1].toDoubleOrNull() ?: continue
            val lon = match.groupValues[2].toDoubleOrNull() ?: continue
            if (lat in -90.0..90.0 && lon in -180.0..180.0) return lat to lon
        }
        return null
    }
}
