package com.micasong.player.data.provider

import com.micasong.player.data.db.TrackEntity
import org.json.JSONObject

/**
 * Pure mappers from Kodi JSON-RPC `AudioLibrary.GetSongs` results to MiCaSong entities (spec §46).
 * Extracted from [KodiProvider] so field mapping is unit-testable. The playable [streamUrl] (a Kodi
 * `/vfs/` URL) and [coverUrl] are precomputed by the provider.
 */
object KodiMappers {

    fun stableId(songId: Int): Long = StableId.of("kodi:$songId")

    private fun JSONObject.firstOfArray(key: String): String? =
        optJSONArray(key)?.optString(0)?.ifBlank { null } ?: optString(key).ifBlank { null }

    fun parseSong(song: JSONObject, providerId: Long, streamUrl: String, coverUrl: String?): TrackEntity {
        val songId = song.optInt("songid")
        val title = song.optString("title", "Sin título")
        val artist = song.firstOfArray("artist") ?: "Artista desconocido"
        val album = song.optString("album").ifBlank { null }
        return TrackEntity(
            id = stableId(songId),
            providerId = providerId,
            mediaUri = streamUrl,
            title = title,
            titleSort = title.lowercase(),
            albumId = album?.let { StableId.of("kodi:album:${it.lowercase()}") },
            albumName = album,
            artistId = (song.firstOfArray("albumartist") ?: artist).let { StableId.of("kodi:artist:${it.lowercase()}") },
            artistName = artist,
            albumArtist = song.firstOfArray("albumartist") ?: artist,
            trackNumber = song.optInt("track").takeIf { it > 0 },
            discNumber = song.optInt("disc").takeIf { it > 0 },
            durationMs = song.optLong("duration") * 1000,   // Kodi duration is in seconds
            year = song.optInt("year").takeIf { it > 0 },
            genre = song.firstOfArray("genre"),
            mimeType = null,
            bitrate = null,
            sampleRate = null,
            bitDepth = null,
            sizeBytes = null,
            artworkUri = coverUrl,
            dateAdded = 0L,
        )
    }
}
