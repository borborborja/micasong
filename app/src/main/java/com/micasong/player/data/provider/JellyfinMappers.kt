package com.micasong.player.data.provider

import com.micasong.player.data.db.TrackEntity
import org.json.JSONObject
import kotlin.math.abs

/**
 * Pure mappers from Jellyfin JSON items to MiCaSong entities (spec §46), extracted from
 * [JellyfinProvider] so the mapping is unit-testable. As with Subsonic, [TrackEntity.mediaUri]
 * holds a ready-to-play, authenticated stream URL (a bare item id isn't playable).
 */
object JellyfinMappers {

    fun stableId(id: String): Long = abs(id.hashCode().toLong()) or 1L

    fun parseItem(item: JSONObject, providerId: Long, streamUrl: String, coverUrl: String?): TrackEntity {
        val title = item.optString("Name", "Sin título")
        val album = item.optString("Album").ifBlank { null }
        val artistName = item.optString("AlbumArtist").ifBlank { firstArtist(item) } ?: "Artista desconocido"
        val artistServerId = item.optJSONArray("ArtistItems")?.optJSONObject(0)?.optString("Id")
            ?.ifBlank { null } ?: artistName
        return TrackEntity(
            id = stableId(item.optString("Id")),
            providerId = providerId,
            mediaUri = streamUrl,
            title = title,
            titleSort = title.lowercase(),
            albumId = item.optString("AlbumId").ifBlank { null }?.let { stableId(it) },
            albumName = album,
            artistId = stableId(artistServerId),
            artistName = artistName,
            albumArtist = item.optString("AlbumArtist").ifBlank { null },
            trackNumber = item.optInt("IndexNumber").takeIf { it > 0 },
            discNumber = item.optInt("ParentIndexNumber").takeIf { it > 0 },
            durationMs = item.optLong("RunTimeTicks") / 10_000,   // 100-ns ticks → ms
            year = item.optInt("ProductionYear").takeIf { it > 0 },
            genre = item.optJSONArray("Genres")?.optString(0)?.ifBlank { null },
            mimeType = null,
            bitrate = null,
            sampleRate = null,
            bitDepth = null,
            sizeBytes = null,
            artworkUri = coverUrl,
            dateAdded = 0L,
        )
    }

    private fun firstArtist(item: JSONObject): String? =
        item.optJSONArray("Artists")?.optString(0)?.ifBlank { null }
}
