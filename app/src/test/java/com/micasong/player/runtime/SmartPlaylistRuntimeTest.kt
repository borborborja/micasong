package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.db.AlbumEntity
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.db.TrackEntity
import com.micasong.player.data.repository.MediaRepository
import com.micasong.player.data.smart.FilterNode
import com.micasong.player.data.smart.FilterOperator
import com.micasong.player.data.smart.FilterTarget
import com.micasong.player.data.smart.MatchMode
import com.micasong.player.data.smart.SmartPlaylistDefinition
import com.micasong.player.data.smart.SortDirection
import com.micasong.player.data.smart.SortField
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
 * Verifies the smart-playlist wiring end-to-end: a rule tree is persisted on the playlist row and
 * re-evaluated live against the catalog through [MediaRepository.tracksInPlaylist].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartPlaylistRuntimeTest {

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
    fun `smart playlist persists rules and resolves live`() = runBlocking {
        db.musicDao().upsertAlbums(listOf(AlbumEntity(1, "Album", "album", "Artist", 10, 2020, 3, 0, null)))
        db.musicDao().upsertTracks(listOf(
            track(1, "Alpha", fav = true, rating = 8),
            track(2, "Beta", fav = false, rating = 2),
            track(3, "Gamma", fav = true, rating = 10),
        ))

        // "favorite == true", ordered by rating desc.
        val def = SmartPlaylistDefinition(
            filter = FilterNode.Group(MatchMode.ALL, listOf(
                FilterNode.Rule(FilterTarget.FAVORITE, FilterOperator.EQUALS, "true"),
            )),
            sortField = SortField.RATING,
            sortDirection = SortDirection.DESC,
        )
        val id = repository.createSmartPlaylist("Favoritas top", def)

        val tracks = repository.tracksInPlaylist(id).first()
        assertEquals(listOf("Gamma", "Alpha"), tracks.map { it.title })

        // Adding a new favorite is reflected without editing the playlist.
        db.musicDao().upsertTracks(listOf(track(4, "Delta", fav = true, rating = 9)))
        val updated = repository.tracksInPlaylist(id).first()
        assertEquals(listOf("Gamma", "Delta", "Alpha"), updated.map { it.title })

        // The definition round-trips from the stored JSON.
        val stored = repository.smartPlaylistDefinition(id)
        assertTrue(stored != null && stored.sortField == SortField.RATING)
    }
}
