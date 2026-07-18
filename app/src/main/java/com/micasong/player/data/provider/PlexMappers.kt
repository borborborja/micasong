package com.micasong.player.data.provider

import com.micasong.player.data.db.TrackEntity
import org.json.JSONObject

/**
 * Pure mappers from Plex JSON (`MediaContainer`) to MiCaSong entities (spec §46), extracted from
 * [PlexProvider] so the field mapping is unit-testable. A track's [TrackEntity.mediaUri] is the
 * fully-authenticated stream URL (the caller precomputes it from the Part key + token).
 */
object PlexMappers {

    fun stableId(ratingKey: String): Long = StableId.of("plex:$ratingKey")

    /** The stream path (`Media[0].Part[0].key`) of a track metadata object, or null. */
    fun partKey(metadata: JSONObject): String? =
        metadata.optJSONArray("Media")?.optJSONObject(0)
            ?.optJSONArray("Part")?.optJSONObject(0)
            ?.optString("key")?.ifBlank { null }

    /** Map a Plex track `Metadata` object to a [TrackEntity]. */
    fun parseTrack(metadata: JSONObject, providerId: Long, streamUrl: String, coverUrl: String?): TrackEntity {
        val ratingKey = metadata.optString("ratingKey")
        val title = metadata.optString("title", "Sin título")
        val artist = metadata.optString("grandparentTitle").ifBlank { "Artista desconocido" }
        val album = metadata.optString("parentTitle").ifBlank { null }
        return TrackEntity(
            id = stableId(ratingKey),
            providerId = providerId,
            mediaUri = streamUrl,
            title = title,
            titleSort = title.lowercase(),
            albumId = metadata.optString("parentRatingKey").ifBlank { null }?.let { stableId(it) },
            albumName = album,
            artistId = metadata.optString("grandparentRatingKey").ifBlank { null }?.let { stableId(it) },
            artistName = artist,
            albumArtist = artist,
            trackNumber = metadata.optInt("index").takeIf { it > 0 },
            discNumber = metadata.optInt("parentIndex").takeIf { it > 0 },
            durationMs = metadata.optLong("duration"),          // Plex duration is already in ms
            year = metadata.optInt("year").takeIf { it > 0 },
            genre = metadata.optJSONArray("Genre")?.optJSONObject(0)?.optString("tag")?.ifBlank { null },
            mimeType = metadata.optJSONArray("Media")?.optJSONObject(0)?.optString("audioCodec")?.ifBlank { null },
            bitrate = metadata.optJSONArray("Media")?.optJSONObject(0)?.optInt("bitrate")?.takeIf { it > 0 }?.let { it * 1000 },
            sampleRate = null,
            bitDepth = null,
            sizeBytes = null,
            artworkUri = coverUrl,
            dateAdded = metadata.optLong("addedAt"),
        )
    }
}
