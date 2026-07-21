package com.micasong.player.provider

import com.micasong.player.data.db.ArtistEntity
import com.micasong.player.data.db.TrackEntity
import com.micasong.player.data.provider.ProviderSnapshot
import com.micasong.player.data.provider.SnapshotDerive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotDeriveTest {

    private fun entity(
        id: Long,
        artistId: Long? = 10,
        artistName: String? = "Artista",
        albumId: Long? = 20,
        albumName: String? = "Disco",
        genre: String? = null,
    ) = TrackEntity(
        id = id, providerId = 2, mediaUri = "http://s/$id", title = "T$id", titleSort = "t$id",
        albumId = albumId, albumName = albumName, artistId = artistId, artistName = artistName,
        albumArtist = null, trackNumber = null, discNumber = null, durationMs = 60_000, year = 2020,
        genre = genre, mimeType = null, bitrate = null, sampleRate = null, bitDepth = null,
        sizeBytes = null, artworkUri = "http://s/art/$id", dateAdded = 0L,
    )

    @Test
    fun `derives artists, albums and genres from a flat track list`() {
        val snapshot = ProviderSnapshot(
            tracks = listOf(
                entity(1, genre = "Rock"),
                entity(2, genre = "Rock"),
                entity(3, artistId = 11, artistName = "Otra", albumId = 21, albumName = "B", genre = "Jazz"),
            ),
            albums = emptyList(), artists = emptyList(), genres = emptyList(),
        )
        val enriched = SnapshotDerive.enrich(snapshot)

        assertEquals(2, enriched.artists.size)
        val artista = enriched.artists.first { it.id == 10L }
        assertEquals("Artista", artista.name)
        assertEquals(2, artista.trackCount)
        assertEquals(1, artista.albumCount)

        assertEquals(2, enriched.albums.size)
        val disco = enriched.albums.first { it.id == 20L }
        assertEquals("Disco", disco.name)
        assertEquals(2, disco.trackCount)
        assertEquals(120_000L, disco.durationMs)
        assertEquals(10L, disco.artistId)
        assertTrue(disco.artworkUri != null)

        assertEquals(setOf("Rock", "Jazz"), enriched.genres.map { it.name }.toSet())
        assertEquals(2, enriched.genres.first { it.name == "Rock" }.trackCount)
    }

    @Test
    fun `provider-supplied entities are left untouched`() {
        val supplied = listOf(ArtistEntity(99, "Del servidor", "del servidor", 1, 1, null))
        val snapshot = ProviderSnapshot(
            tracks = listOf(entity(1)),
            albums = emptyList(), artists = supplied, genres = emptyList(),
        )
        assertEquals(supplied, SnapshotDerive.enrich(snapshot).artists)
    }

    @Test
    fun `tracks without ids do not invent entries`() {
        val snapshot = ProviderSnapshot(
            tracks = listOf(entity(1, artistId = null, albumId = null)),
            albums = emptyList(), artists = emptyList(), genres = emptyList(),
        )
        val enriched = SnapshotDerive.enrich(snapshot)
        assertTrue(enriched.artists.isEmpty())
        assertTrue(enriched.albums.isEmpty())
    }
}
