package com.micasong.player.data.tags

import kotlin.math.roundToInt

/**
 * Configuration for the custom tag parser (spec §7, §5.3). Separators are user-configurable per
 * field, and several toggles mirror the reference app's parser options.
 */
data class TagParserConfig(
    // Space is intentionally NOT a default separator — it would split names like "The Beatles".
    val artistSeparators: List<String> = listOf("/", ";"),
    val genreSeparators: List<String> = listOf("/", ";", ","),
    val mbidSeparators: List<String> = listOf("/", ";"),
    val ratingAsUserRating: Boolean = true,
    val ignoreMbids: Boolean = false,
    val ignoreExplicit: Boolean = false,
    val preferYearTag: Boolean = false,
)

/**
 * The structured result of parsing a track's raw tags (spec §7). Multi-value fields are already
 * split; ratings are on MiCaSong's half-star 0..10 scale; MusicBee "Love" state is mapped to
 * favorite / excluded-from-mixes per spec §10.
 */
data class ParsedTags(
    val title: String? = null,
    val album: String? = null,
    val artists: List<String> = emptyList(),
    val albumArtists: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val originalDate: String? = null,
    val releaseDate: String? = null,
    val bpm: Int? = null,
    val compilation: Boolean = false,
    val explicit: Boolean = false,
    val rating: Int = 0,
    val isFavorite: Boolean = false,
    val excludedFromMixes: Boolean = false,
    val musicBrainzArtistIds: List<String> = emptyList(),
    val musicBrainzAlbumId: String? = null,
    val musicBrainzTrackId: String? = null,
)

/**
 * Post-processing tag parser (spec §7). TagLib does the raw reading on device; this pure-Kotlin
 * stage applies MiCaSong's semantics — multi-value splitting, date/year resolution, rating
 * normalisation and the MusicBee Love mapping — and is fully unit-testable.
 */
object TagParser {

    fun parse(raw: Map<String, String>, config: TagParserConfig = TagParserConfig()): ParsedTags {
        val tags = raw.mapKeys { it.key.uppercase() }
        fun get(vararg keys: String): String? = keys.firstNotNullOfOrNull { tags[it]?.takeIf(String::isNotBlank) }

        val year = resolveYear(get("YEAR"), get("DATE"), get("ORIGINALDATE"), config.preferYearTag)
        val love = get("LOVE", "LOVERATING", "MUSICBEE_LOVE")?.lowercase()

        return ParsedTags(
            title = get("TITLE"),
            album = get("ALBUM"),
            artists = splitValues(get("ARTIST", "ARTISTS"), config.artistSeparators),
            albumArtists = splitValues(get("ALBUMARTIST", "ALBUM ARTIST"), config.artistSeparators),
            genres = splitValues(get("GENRE"), config.genreSeparators),
            composers = splitValues(get("COMPOSER"), config.artistSeparators),
            trackNumber = get("TRACK", "TRACKNUMBER")?.substringBefore('/')?.trim()?.toIntOrNull(),
            discNumber = get("DISC", "DISCNUMBER")?.substringBefore('/')?.trim()?.toIntOrNull(),
            year = year,
            originalDate = get("ORIGINALDATE"),
            releaseDate = get("RELEASEDATE", "DATE"),
            bpm = get("BPM", "TBPM")?.trim()?.toDoubleOrNull()?.roundToInt(),
            compilation = get("COMPILATION", "TCMP")?.let { it == "1" || it.equals("true", true) } ?: false,
            explicit = if (config.ignoreExplicit) false else parseExplicit(get("ITUNESADVISORY", "EXPLICIT")),
            rating = if (config.ratingAsUserRating) parseRating(get("RATING", "POPM", "FMPS_RATING")) else 0,
            isFavorite = love == "l" || love == "loved",
            excludedFromMixes = love == "b" || love == "banned",
            musicBrainzArtistIds = if (config.ignoreMbids) emptyList()
                else splitValues(get("MUSICBRAINZ_ARTISTID", "MUSICBRAINZ ARTIST ID"), config.mbidSeparators),
            musicBrainzAlbumId = if (config.ignoreMbids) null else get("MUSICBRAINZ_ALBUMID", "MUSICBRAINZ ALBUM ID"),
            musicBrainzTrackId = if (config.ignoreMbids) null else get("MUSICBRAINZ_TRACKID", "MUSICBRAINZ RELEASE TRACK ID"),
        )
    }

    /** Split a raw multi-value field on any of the configured separators, trimming empties. */
    fun splitValues(raw: String?, separators: List<String>): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        var parts = listOf(raw)
        for (sep in separators) {
            parts = parts.flatMap { it.split(sep) }
        }
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Resolve the release year, honouring the "prefer Year tag" option (spec §10). */
    fun resolveYear(yearTag: String?, dateTag: String?, originalDate: String?, preferYearTag: Boolean): Int? {
        val fromYear = yearTag?.take(4)?.toIntOrNull()
        val fromDate = dateTag?.take(4)?.toIntOrNull()
        val fromOriginal = originalDate?.take(4)?.toIntOrNull()
        return if (preferYearTag) fromYear ?: fromDate ?: fromOriginal
        else fromOriginal ?: fromDate ?: fromYear
    }

    private fun parseExplicit(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
        "1", "true", "explicit", "yes" -> true
        else -> false
    }

    /**
     * Normalise a rating tag to MiCaSong's half-star 0..10 scale. Handles a 0-5 (fractional)
     * scale, a 0-100 percentage, and POPM 0-255 bytes via nearest-linear mapping.
     */
    fun parseRating(raw: String?): Int {
        val v = raw?.trim()?.toDoubleOrNull() ?: return 0
        val halfStars = when {
            v <= 5.0 -> v * 2                    // 0-5 star scale (incl. half stars)
            v <= 100.0 -> v / 10.0               // 0-100 percentage
            else -> v / 255.0 * 10.0             // POPM 0-255 byte
        }
        return halfStars.roundToInt().coerceIn(0, 10)
    }
}
