package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

/** Runtime verification of playlist management against a real Room DB (create/add/remove/rename/delete). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistRuntimeTest {

    private lateinit var db: MiCaSongDatabase
    private lateinit var repository: MediaRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiCaSongDatabase::class.java).allowMainThreadQueries().build()
        repository = MediaRepository(context, db.musicDao(), db.playlistDao(), db.providerDao(), db.downloadDao(), com.micasong.player.data.cache.DownloadTrigger {}, db.radioDao())
        db.musicDao().upsertTracks((1L..4L).map { track(it) })
    }

    @After
    fun tearDown() = db.close()

    private fun track(id: Long) = TrackEntity(
        id = id, providerId = 1, mediaUri = "uri://$id", title = "T$id", titleSort = "t$id",
        albumId = 1, albumName = "A", artistId = 1, artistName = "Ar", albumArtist = "Ar",
        trackNumber = id.toInt(), discNumber = 1, durationMs = 1000, year = null, genre = null,
        mimeType = null, bitrate = null, sampleRate = null, bitDepth = null, sizeBytes = null,
        artworkUri = null, dateAdded = id,
    )

    @Test
    fun `create add read remove rename delete`() = runBlocking {
        val id = repository.createPlaylist("Favorites")

        repository.addTracksToPlaylist(id, listOf(1, 2, 3))
        assertEquals(listOf("T1", "T2", "T3"), repository.tracksInPlaylist(id).first().map { it.title })
        assertEquals(3, repository.playlists.first().first().trackCount)

        // Adding a duplicate + a new one → dedups, appends 4
        repository.addTracksToPlaylist(id, listOf(2, 4))
        assertEquals(listOf(1L, 2L, 3L, 4L), repository.tracksInPlaylist(id).first().map { it.id })

        repository.removeTrackFromPlaylist(id, 2)
        assertEquals(listOf(1L, 3L, 4L), repository.tracksInPlaylist(id).first().map { it.id })
        assertEquals(3, repository.playlists.first().first().trackCount)

        repository.renamePlaylist(id, "Best of")
        assertEquals("Best of", repository.playlists.first().first().name)

        repository.deletePlaylist(id)
        assertTrue(repository.playlists.first().isEmpty())
    }

    @Test
    fun `save queue as playlist keeps order`() = runBlocking {
        val id = repository.saveAsPlaylist("My Queue", listOf(3, 1, 4))
        assertEquals(listOf(3L, 1L, 4L), repository.tracksInPlaylist(id).first().map { it.id })
        assertEquals("My Queue", repository.playlists.first().first { it.id == id }.name)
    }
}
