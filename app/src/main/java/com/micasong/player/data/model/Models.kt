package com.micasong.player.data.model

/**
 * Domain models exposed by the repository to the UI and playback layers.
 *
 * These are deliberately provider-agnostic: a [Track] may originate from the local device,
 * a Subsonic server, Jellyfin, etc. Fields that a given provider cannot supply are nullable
 * so the UI can degrade gracefully (see the capability matrix, spec §6).
 */

/** Offline availability state of a media item (spec §34). */
enum class OfflineState { NONE, PLAYBACK_CACHE, ROLLING, PERMANENT }

data class Track(
    val id: Long,
    val providerId: Long,
    val mediaUri: String,
    val title: String,
    val albumId: Long?,
    val albumName: String?,
    val artistId: Long?,
    val artistName: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val year: Int?,
    val genre: String?,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val sizeBytes: Long?,
    val artworkUri: String?,
    val isFavorite: Boolean = false,
    val userRating: Int = 0,          // 0..10 → half-star precision (spec §10)
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0L,
    val resumePositionMs: Long = 0L,
    val isAudiobook: Boolean = false,
    val excludedFromMixes: Boolean = false,
    val offlineState: OfflineState = OfflineState.NONE,
) {
    /** A short "1:01:42 · 2024 · FLAC · 24/96"-style quality descriptor (spec §22.3). */
    val qualityLabel: String?
        get() {
            val parts = buildList {
                mimeType?.let { add(it.substringAfterLast('/').uppercase()) }
                if (bitDepth != null && sampleRate != null) {
                    add("$bitDepth/${sampleRate / 1000}")
                } else if (bitrate != null && bitrate > 0) {
                    add("${bitrate / 1000} kbps")
                }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}

data class Album(
    val id: Long,
    val name: String,
    val albumArtist: String?,
    val artistId: Long?,
    val year: Int?,
    val trackCount: Int,
    val durationMs: Long,
    val artworkUri: String?,
    val isFavorite: Boolean = false,
    val dateAdded: Long = 0L,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
)

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val artworkUri: String?,
    val isFavorite: Boolean = false,
    val biography: String? = null,
)

data class Genre(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val artworkUri: String? = null,
)

data class Playlist(
    val id: Long,
    val name: String,
    val providerId: Long,
    val isSmart: Boolean,
    val trackCount: Int,
    val artworkUri: String? = null,
)
