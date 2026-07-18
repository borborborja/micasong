package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.db.MusicDao
import com.micasong.player.data.db.TrackEntity
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
 * Runtime verification of the Room data layer using Robolectric (real SQLite on the JVM). Unlike
 * the pure-logic tests, this exercises the actual database — schema, queries, flows and mutations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseRuntimeTest {

    private lateinit var db: MiCaSongDatabase
    private lateinit var dao: MusicDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiCaSongDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.musicDao()
    }

    @After
    fun tearDown() = db.close()

    private fun track(id: Long, title: String, albumId: Long, fav: Boolean = false) = TrackEntity(
        id = id, providerId = 1, mediaUri = "uri://$id", title = title, titleSort = title.lowercase(),
        albumId = albumId, albumName = "Album $albumId", artistId = 10, artistName = "Artist",
        albumArtist = "Artist", trackNumber = id.toInt(), discNumber = 1, durationMs = 200_000,
        year = 2020, genre = "Rock", mimeType = "audio/flac", bitrate = null, sampleRate = null,
        bitDepth = null, sizeBytes = null, artworkUri = null, dateAdded = id, isFavorite = fav,
    )

    private fun album(id: Long) = AlbumEntity(
        id = id, name = "Album $id", nameSort = "album $id", albumArtist = "Artist", artistId = 10,
        year = 2020, trackCount = 2, durationMs = 400_000, artworkUri = null, dateAdded = id,
    )

    @Test
    fun `albums and tracks persist and query back`() = runBlocking {
        dao.upsertAlbums(listOf(album(1), album(2)))
        dao.upsertTracks(listOf(track(1, "Alpha", 1), track(2, "Beta", 1), track(3, "Gamma", 2)))

        assertEquals(2, dao.albums().first().size)
        assertEquals(listOf("Alpha", "Beta"), dao.tracksByAlbum(1).first().map { it.title })
        assertEquals(1, dao.tracksByAlbum(2).first().size)
        assertEquals(3, dao.trackCount().first())
    }

    @Test
    fun `favorites and search work at runtime`() = runBlocking {
        dao.upsertTracks(listOf(track(1, "Viva La Vida", 1), track(2, "Clocks", 1)))
        dao.setTrackFavorite(1, true)

        assertEquals(listOf("Viva La Vida"), dao.favoriteTracks().first().map { it.title })
        assertEquals(listOf("Viva La Vida"), dao.searchTracks("viva").first().map { it.title })
        assertTrue(dao.searchTracks("clock").first().isNotEmpty())
    }

    @Test
    fun `play counts and resume points update`() = runBlocking {
        dao.upsertTracks(listOf(track(1, "Song", 1)))
        dao.registerPlay(1, ts = 12345)
        dao.setResumePosition(1, 60_000)

        val t = dao.trackById(1)!!
        assertEquals(1, t.playCount)
        assertEquals(12345L, t.lastPlayed)
        assertEquals(60_000L, t.resumePositionMs)
    }

    @Test
    fun `rating persists and the track flow reflects the change`() = runBlocking {
        dao.upsertTracks(listOf(track(1, "Song", 1)))
        assertEquals(0, dao.trackByIdFlow(1).first()!!.userRating)

        dao.setTrackRating(1, 8)
        assertEquals(8, dao.trackByIdFlow(1).first()!!.userRating)

        dao.setTrackRating(1, 0)
        assertEquals(0, dao.trackByIdFlow(1).first()!!.userRating)
    }

    @Test
    fun `replace local library clears then repopulates`() = runBlocking {
        dao.upsertTracks(listOf(track(1, "Old", 1)))
        dao.replaceLocalLibrary(
            providerId = 1,
            tracks = listOf(track(2, "New", 2)),
            albums = listOf(album(2)),
            artists = emptyList(),
            genres = emptyList(),
        )
        assertEquals(listOf("New"), dao.allTracks().first().map { it.title })
    }
}
