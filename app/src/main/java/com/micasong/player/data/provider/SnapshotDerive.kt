package com.micasong.player.data.provider

import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.ArtistEntity
import com.micasong.player.data.db.GenreEntity

/**
 * Fills in the entity tables a provider couldn't supply by deriving them from its tracks
 * (spec §6 capability degradation). Several backends only expose a flat track list (WebDAV,
 * Kodi, Plex, AudioBookShelf) or return tracks without a separate artist listing (Jellyfin,
 * Emby); without this, their artist/album/genre browse pages come up empty. Pure and testable.
 */
object SnapshotDerive {

    /** Derive whichever of artists/albums/genres is empty from the snapshot's tracks. */
    fun enrich(snapshot: ProviderSnapshot): ProviderSnapshot {
        if (snapshot.tracks.isEmpty()) return snapshot
        val artists = snapshot.artists.ifEmpty { deriveArtists(snapshot) }
        val albums = snapshot.albums.ifEmpty { deriveAlbums(snapshot) }
        val genres = snapshot.genres.ifEmpty { deriveGenres(snapshot) }
        return snapshot.copy(artists = artists, albums = albums, genres = genres)
    }

    private fun deriveArtists(snapshot: ProviderSnapshot): List<ArtistEntity> =
        snapshot.tracks
            .filter { it.artistId != null }
            .groupBy { it.artistId!! }
            .map { (id, tracks) ->
                val name = tracks.firstNotNullOfOrNull { it.artistName } ?: "?"
                ArtistEntity(
                    id = id,
                    name = name,
                    nameSort = name.lowercase(),
                    albumCount = tracks.mapNotNull { it.albumId }.distinct().size,
                    trackCount = tracks.size,
                    artworkUri = null,
                )
            }

    private fun deriveAlbums(snapshot: ProviderSnapshot): List<AlbumEntity> =
        snapshot.tracks
            .filter { it.albumId != null }
            .groupBy { it.albumId!! }
            .map { (id, tracks) ->
                val name = tracks.firstNotNullOfOrNull { it.albumName } ?: "?"
                AlbumEntity(
                    id = id,
                    name = name,
                    nameSort = name.lowercase(),
                    albumArtist = tracks.firstNotNullOfOrNull { it.albumArtist }
                        ?: tracks.firstNotNullOfOrNull { it.artistName },
                    artistId = tracks.firstNotNullOfOrNull { it.artistId },
                    year = tracks.firstNotNullOfOrNull { it.year },
                    trackCount = tracks.size,
                    durationMs = tracks.sumOf { it.durationMs },
                    artworkUri = tracks.firstNotNullOfOrNull { it.artworkUri },
                )
            }

    private fun deriveGenres(snapshot: ProviderSnapshot): List<GenreEntity> =
        snapshot.tracks
            .mapNotNull { it.genre?.ifBlank { null } }
            .groupingBy { it }
            .eachCount()
            .map { (name, count) -> GenreEntity(StableId.of(name), name, count) }
}
