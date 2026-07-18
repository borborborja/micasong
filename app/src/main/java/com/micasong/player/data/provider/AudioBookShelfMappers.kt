package com.micasong.player.data.provider

import com.micasong.player.data.audio.ChapterMarker
import com.micasong.player.data.audio.Chapters
import com.micasong.player.data.db.TrackEntity
import org.json.JSONObject

/**
 * Pure mappers from AudioBookShelf item JSON to MiCaSong entities (spec §46, experimental). Each
 * library item (book) becomes one audiobook track carrying its chapters, so the Now Playing
 * chapter navigator (spec §19) works with real data. [streamUrl]/[coverUrl] are precomputed.
 */
object AudioBookShelfMappers {

    fun stableId(itemId: String): Long = StableId.of("abs:$itemId")

    /** Build a chaptersJson string from an ABS `media.chapters` array (start/end in seconds). */
    fun chaptersJson(media: JSONObject): String? {
        val arr = media.optJSONArray("chapters") ?: return null
        if (arr.length() == 0) return null
        val markers = (0 until arr.length()).map { i ->
            val ch = arr.getJSONObject(i)
            ChapterMarker(
                title = ch.optString("title").ifBlank { "Capítulo ${i + 1}" },
                startMs = (ch.optDouble("start", 0.0) * 1000).toLong(),
            )
        }
        return Chapters.toJson(markers)
    }

    fun parseItem(item: JSONObject, providerId: Long, streamUrl: String, coverUrl: String?): TrackEntity {
        val itemId = item.optString("id")
        val media = item.optJSONObject("media") ?: JSONObject()
        val metadata = media.optJSONObject("metadata") ?: JSONObject()
        val title = metadata.optString("title", "Sin título")
        val author = metadata.optString("authorName").ifBlank { metadata.optString("author").ifBlank { "Autor desconocido" } }
        val series = metadata.optString("seriesName").ifBlank { null }
        return TrackEntity(
            id = stableId(itemId),
            providerId = providerId,
            mediaUri = streamUrl,
            title = title,
            titleSort = title.lowercase(),
            albumId = (series ?: title).let { StableId.of("abs:album:${it.lowercase()}") },
            albumName = series ?: title,
            artistId = StableId.of("abs:artist:${author.lowercase()}"),
            artistName = author,
            albumArtist = author,
            trackNumber = null,
            discNumber = null,
            durationMs = (media.optDouble("duration", 0.0) * 1000).toLong(),
            year = metadata.optString("publishedYear").toIntOrNull(),
            genre = metadata.optJSONArray("genres")?.optString(0)?.ifBlank { null },
            mimeType = null,
            bitrate = null,
            sampleRate = null,
            bitDepth = null,
            sizeBytes = media.optLong("size").takeIf { it > 0 },
            artworkUri = coverUrl,
            dateAdded = 0L,
            isAudiobook = true,
            chaptersJson = chaptersJson(media),
        )
    }
}
