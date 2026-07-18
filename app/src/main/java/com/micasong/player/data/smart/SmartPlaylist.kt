package com.micasong.player.data.smart

import com.micasong.player.data.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** How a smart playlist orders its results (spec §31). */
enum class SortField { TITLE, ARTIST, ALBUM, YEAR, RATING, PLAY_COUNT, LAST_PLAYED, DURATION, RANDOM, STABLE_RANDOM }
enum class SortDirection { ASC, DESC }

/**
 * A dynamic, always-up-to-date playlist (spec §31): a filter tree + sort + optional limit.
 * Serialized to JSON and stored on the playlist row; re-evaluated against the live library
 * every time it's opened.
 */
@Serializable
data class SmartPlaylistDefinition(
    val filter: FilterNode = FilterNode.Group(),
    val sortField: SortField = SortField.TITLE,
    val sortDirection: SortDirection = SortDirection.ASC,
    val limit: Int? = null,
    /** Seed for STABLE_RANDOM so the order is reproducible until refreshed (spec §31 API). */
    val stableSeed: Long = 0L,
) {
    /** Filter → sort → limit. Pure; the caller supplies the current library snapshot. */
    fun apply(tracks: List<Track>): List<Track> {
        val filtered = tracks.filter { FilterEngine.evaluate(filter, it) }
        val sorted = sort(filtered)
        return if (limit != null && limit > 0) sorted.take(limit) else sorted
    }

    private fun sort(tracks: List<Track>): List<Track> {
        val comparator: Comparator<Track> = when (sortField) {
            SortField.TITLE -> compareBy { it.title.lowercase() }
            SortField.ARTIST -> compareBy { it.artistName?.lowercase() ?: "" }
            SortField.ALBUM -> compareBy { it.albumName?.lowercase() ?: "" }
            SortField.YEAR -> compareBy { it.year ?: 0 }
            SortField.RATING -> compareBy { it.userRating }
            SortField.PLAY_COUNT -> compareBy { it.playCount }
            SortField.LAST_PLAYED -> compareBy { it.lastPlayed }
            SortField.DURATION -> compareBy { it.durationMs }
            SortField.RANDOM -> return tracks.shuffled()
            SortField.STABLE_RANDOM -> return tracks.shuffled(java.util.Random(stableSeed))
        }
        return if (sortDirection == SortDirection.DESC) tracks.sortedWith(comparator.reversed())
        else tracks.sortedWith(comparator)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String?): SmartPlaylistDefinition? =
            text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<SmartPlaylistDefinition>(it) }.getOrNull() }
        fun toJson(def: SmartPlaylistDefinition): String = json.encodeToString(def)
    }
}
