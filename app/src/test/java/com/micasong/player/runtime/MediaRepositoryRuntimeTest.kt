package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.db.TrackEntity
import com.micasong.player.data.repository.MediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime verification of the [MediaRepository] against a real Room database (Robolectric): the
 * repository's entity→domain mapping, favorite toggling, personal mix and search are exercised
 * end to end on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaRepositoryRuntimeTest {

    private lateinit var db: MiCaSongDatabase
    private lateinit var repository: MediaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiCaSongDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = MediaRepository(context, db.musicDao(), db.playlistDao(), db.providerDao(), db.downloadDao(), com.micasong.player.data.cache.DownloadTrigger {}, db.radioDao())
    }

    @After
    fun tearDown() = db.close()

    private fun track(id: Long, title: String, fav: Boolean = false, rating: Int = 0) = TrackEntity(
        id = id, providerId = 1, mediaUri = "uri://$id", title = title, titleSort = title.lowercase(),
        albumId = 1, albumName = "Album", artistId = 10, artistName = "Artist", albumArtist = "Artist",
        trackNumber = id.toInt(), discNumber = 1, durationMs = 200_000, year = 2020, genre = "Rock",
        mimeType = "audio/flac", bitrate = null, sampleRate = null, bitDepth = null, sizeBytes = null,
        artworkUri = null, dateAdded = id, isFavorite = fav, userRating = rating,
    )

    @Test
    fun `maps entities to domain and exposes albums`() = runBlocking {
        db.musicDao().upsertAlbums(listOf(AlbumEntity(1, "Album", "album", "Artist", 10, 2020, 3, 0, null)))
        db.musicDao().upsertTracks(listOf(track(1, "One"), track(2, "Two"), track(3, "Three")))

        val albums = repository.albums.first()
        assertEquals(1, albums.size)
        assertEquals("Album", albums.first().name)
        assertEquals(3, repository.allTracks.first().size)
    }

    @Test
    fun `toggling a favorite is reflected through the repository`() = runBlocking {
        db.musicDao().upsertTracks(listOf(track(1, "Fav me")))
        val t = repository.allTracks.first().first()
        assertTrue(!t.isFavorite)

        repository.toggleTrackFavorite(t)
        assertEquals(listOf("Fav me"), repository.favoriteTracks.first().map { it.title })
    }

    @Test
    fun `personal mix draws from the library`() = runBlocking {
        db.musicDao().upsertTracks((1..20L).map { track(it, "T$it", rating = 6) })
        val mix = repository.trackMix(10)
        assertTrue(mix.isNotEmpty())
        assertTrue(mix.size <= 10)
        assertEquals(mix.size, mix.map { it.id }.toSet().size)   // no duplicates
    }

    @Test
    fun `search finds tracks by title and artist`() = runBlocking {
        db.musicDao().upsertTracks(listOf(track(1, "Viva La Vida"), track(2, "Clocks")))
        assertEquals(listOf("Viva La Vida"), repository.searchTracks("viva").first().map { it.title })
        assertEquals(2, repository.searchTracks("artist").first().size)   // both by "Artist"
    }
}
