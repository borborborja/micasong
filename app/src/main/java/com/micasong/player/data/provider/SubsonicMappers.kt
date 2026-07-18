package com.micasong.player.data.provider

import com.micasong.player.data.db.TrackEntity
import org.json.JSONObject

/**
 * Pure mappers from Subsonic JSON to MiCaSong entities (spec §47), extracted from
 * [SubsonicProvider] so the field mapping is unit-testable without a live server.
 *
 * Crucially, a track's [TrackEntity.mediaUri] is set to a ready-to-play, authenticated `stream`
 * URL — server ids alone aren't playable, so the caller precomputes the stream/cover URLs and
 * passes them in.
 */
object SubsonicMappers {

    fun stableId(serverId: String): Long = StableId.of(serverId)

    /**
     * Map a Subsonic `song` object to a [TrackEntity]. [streamUrl] is the authenticated, playable
     * stream URL for this song and [coverUrl] its artwork URL.
     */
    fun parseSong(song: JSONObject, providerId: Long, streamUrl: String, coverUrl: String?): TrackEntity {
        val serverId = song.optString("id")
        val title = song.optString("title", "Sin título")
        val album = song.optString("album").ifBlank { null }
        val artist = song.optString("artist").ifBlank { "Artista desconocido" }
        return TrackEntity(
            id = stableId(serverId),
            providerId = providerId,
            mediaUri = streamUrl,                                   // playable, authenticated
            title = title,
            titleSort = title.lowercase(),
            albumId = song.optString("albumId").ifBlank { null }?.let { stableId(it) },
            albumName = album,
            artistId = song.optString("artistId").ifBlank { null }?.let { stableId(it) },
            artistName = artist,
            albumArtist = song.optString("albumArtist").ifBlank { null },
            trackNumber = song.optInt("track").takeIf { it > 0 },
            discNumber = song.optInt("discNumber").takeIf { it > 0 },
            durationMs = song.optLong("duration") * 1000,          // Subsonic duration is in seconds
            year = song.optInt("year").takeIf { it > 0 },
            genre = song.optString("genre").ifBlank { null },
            mimeType = song.optString("contentType").ifBlank { null },
            bitrate = song.optInt("bitRate").takeIf { it > 0 }?.let { it * 1000 },   // kbps → bps
            sampleRate = song.optInt("samplingRate").takeIf { it > 0 },              // OpenSubsonic
            bitDepth = song.optInt("bitDepth").takeIf { it > 0 },                    // OpenSubsonic
            sizeBytes = song.optLong("size").takeIf { it > 0 },
            artworkUri = coverUrl,
            dateAdded = 0L,
        )
    }
}
