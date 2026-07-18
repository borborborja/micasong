package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.cache.AutoCacheRules
import com.micasong.player.data.cache.CacheTier
import com.micasong.player.data.cache.DownloadState
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.db.TrackEntity
import com.micasong.player.data.repository.MediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the offline-download data layer: enqueue (dedup), auto-cache reconcile, removal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadRuntimeTest {

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

    private fun track(id: Long, fav: Boolean = false, genre: String = "Rock") = TrackEntity(
        id = id, providerId = 7, mediaUri = "http://s/$id", title = "T$id", titleSort = "t$id",
        albumId = 1, albumName = "Album", artistId = 10, artistName = "Artist", albumArtist = "Artist",
        trackNumber = id.toInt(), discNumber = 1, durationMs = 200_000, year = 2020, genre = genre,
        mimeType = "audio/flac", bitrate = null, sampleRate = null, bitDepth = null, sizeBytes = null,
        artworkUri = null, dateAdded = id, isFavorite = fav,
    )

    @Test
    fun `enqueue dedups and reports state`() = runBlocking {
        db.musicDao().upsertTracks(listOf(track(1), track(2)))
        repository.enqueueDownloads(listOf(1L, 2L))
        repository.enqueueDownloads(listOf(1L)) // duplicate ignored

        val rows = repository.downloads.first()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.state == DownloadState.QUEUED.ordinal })
        assertEquals(7L, rows.first().providerId)
        assertNull(repository.downloadedPath(1L))
    }

    @Test
    fun `auto-cache reconcile queues favorites and removes when rule drops`() = runBlocking {
        db.musicDao().upsertTracks(listOf(track(1, fav = true), track(2, fav = false)))

        val added = repository.runAutoCacheReconcile(AutoCacheRules(cacheFavorites = true))
        assertEquals(1, added)
        assertEquals(listOf(1L), repository.downloads.first().map { it.trackId })

        // Track 1 stops being a favorite → its auto-cached copy is removed on the next reconcile.
        db.musicDao().setTrackFavorite(1, false)
        repository.runAutoCacheReconcile(AutoCacheRules(cacheFavorites = true))
        assertTrue(repository.downloads.first().isEmpty())
    }

    @Test
    fun `remove deletes the row`() = runBlocking {
        db.musicDao().upsertTracks(listOf(track(1)))
        repository.enqueueDownloads(listOf(1L), tier = CacheTier.PERMANENT)
        assertEquals(1, repository.downloads.first().size)
        repository.removeDownload(1L)
        assertTrue(repository.downloads.first().isEmpty())
    }
}
