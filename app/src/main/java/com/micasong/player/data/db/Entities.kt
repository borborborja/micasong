package com.micasong.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.micasong.player.data.model.Album
import com.micasong.player.data.model.Artist
import com.micasong.player.data.model.Genre
import com.micasong.player.data.model.OfflineState
import com.micasong.player.data.model.Playlist
import com.micasong.player.data.model.Track

/**
 * Room persistence layer — the unified, offline-first local catalog (spec §2, §10).
 * Everything from every provider is dumped here so the UI is fast and works without a
 * network connection. Per-user state (favorites, ratings, history, resume points, offline
 * state) lives alongside the catalog rows.
 */

@Entity(
    tableName = "tracks",
    indices = [Index("albumId"), Index("artistId"), Index("genre"), Index("title")],
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    val providerId: Long,
    val mediaUri: String,
    val title: String,
    val titleSort: String?,
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
    val dateAdded: Long,
    // user state
    val isFavorite: Boolean = false,
    val userRating: Int = 0,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0L,
    val resumePositionMs: Long = 0L,
    val isAudiobook: Boolean = false,
    val excludedFromMixes: Boolean = false,
    val offlineState: Int = 0,
)

@Entity(tableName = "albums", indices = [Index("artistId"), Index("name")])
data class AlbumEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val nameSort: String?,
    val albumArtist: String?,
    val artistId: Long?,
    val year: Int?,
    val trackCount: Int,
    val durationMs: Long,
    val artworkUri: String?,
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
)

@Entity(tableName = "artists", indices = [Index("name")])
data class ArtistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val nameSort: String?,
    val albumCount: Int,
    val trackCount: Int,
    val artworkUri: String?,
    val isFavorite: Boolean = false,
    val biography: String? = null,
)

@Entity(tableName = "genres", indices = [Index(value = ["name"], unique = true)])
data class GenreEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val trackCount: Int,
    val artworkUri: String? = null,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val providerId: Long,
    val isSmart: Boolean = false,
    val trackCount: Int = 0,
    val artworkUri: String? = null,
    /** For smart playlists: serialized filter rules (spec §31). */
    val smartRulesJson: String? = null,
    val sortOrder: String? = null,
    val limit: Int? = null,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("trackId")],
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

// ---- Mappers to domain ----

fun TrackEntity.toDomain() = Track(
    id = id,
    providerId = providerId,
    mediaUri = mediaUri,
    title = title,
    albumId = albumId,
    albumName = albumName,
    artistId = artistId,
    artistName = artistName,
    albumArtist = albumArtist,
    trackNumber = trackNumber,
    discNumber = discNumber,
    durationMs = durationMs,
    year = year,
    genre = genre,
    mimeType = mimeType,
    bitrate = bitrate,
    sampleRate = sampleRate,
    bitDepth = bitDepth,
    sizeBytes = sizeBytes,
    artworkUri = artworkUri,
    isFavorite = isFavorite,
    userRating = userRating,
    playCount = playCount,
    skipCount = skipCount,
    lastPlayed = lastPlayed,
    resumePositionMs = resumePositionMs,
    isAudiobook = isAudiobook,
    excludedFromMixes = excludedFromMixes,
    offlineState = OfflineState.entries.getOrElse(offlineState) { OfflineState.NONE },
)

fun AlbumEntity.toDomain() = Album(
    id = id,
    name = name,
    albumArtist = albumArtist,
    artistId = artistId,
    year = year,
    trackCount = trackCount,
    durationMs = durationMs,
    artworkUri = artworkUri,
    isFavorite = isFavorite,
    dateAdded = dateAdded,
    playCount = playCount,
    lastPlayed = lastPlayed,
)

fun ArtistEntity.toDomain() = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    trackCount = trackCount,
    artworkUri = artworkUri,
    isFavorite = isFavorite,
    biography = biography,
)

fun GenreEntity.toDomain() = Genre(id = id, name = name, trackCount = trackCount, artworkUri = artworkUri)

fun PlaylistEntity.toDomain() = Playlist(
    id = id,
    name = name,
    providerId = providerId,
    isSmart = isSmart,
    trackCount = trackCount,
    artworkUri = artworkUri,
)
