package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.backup.BackupContent
import com.micasong.player.data.backup.BackupManager
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.db.PlaylistEntity
import com.micasong.player.data.db.ProviderConfigEntity
import com.micasong.player.data.db.TrackEntity
import com.micasong.player.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end backup verification: seed real data, pack an encrypted archive, wipe the database,
 * restore, and confirm providers/playlists/settings come back — plus wrong-password rejection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRuntimeTest {

    private lateinit var db: MiCaSongDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var manager: BackupManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiCaSongDatabase::class.java)
            .allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        manager = BackupManager(db.musicDao(), db.playlistDao(), db.providerDao(), settings)
    }

    @After
    fun tearDown() = db.close()

    private fun track(id: Long) = TrackEntity(
        id = id, providerId = 1, mediaUri = "uri://$id", title = "T$id", titleSort = "t$id",
        albumId = 1, albumName = "Album", artistId = 10, artistName = "Artist", albumArtist = "Artist",
        trackNumber = id.toInt(), discNumber = 1, durationMs = 200_000, year = 2020, genre = "Rock",
        mimeType = "audio/flac", bitrate = null, sampleRate = null, bitDepth = null, sizeBytes = null,
        artworkUri = null, dateAdded = id,
    )

    @Test
    fun `backup packs data and restore brings it back`() = runBlocking {
        // Seed tracks, a provider and a playlist referencing two of the tracks.
        db.musicDao().upsertTracks((1L..3L).map { track(it) })
        db.providerDao().upsert(
            ProviderConfigEntity(
                type = "SUBSONIC", displayName = "Casa", primaryUrl = "http://nas:4040",
                secondaryUrl = null, username = "borja", secret = "s3cr3t",
            )
        )
        val plId = db.playlistDao().upsert(PlaylistEntity(name = "Favoritas", providerId = 1L))
        db.playlistDao().setPlaylistTracks(plId, listOf(1L, 2L))
        settings.setGapless(false)

        val archive = manager.createBackup(
            selection = setOf(BackupContent.SETTINGS, BackupContent.PROVIDERS, BackupContent.PLAYLISTS),
            password = "pw",
            appVersion = "0.0.5",
            createdAtMs = 1_000L,
        )

        // Wipe the user data, then flip the setting back to the default.
        db.providerDao().all().first().forEach { db.providerDao().delete(it.rowId) }
        db.playlistDao().delete(plId)
        settings.setGapless(true)
        assertTrue(db.providerDao().all().first().isEmpty())

        val result = manager.restoreBackup(archive, "pw")

        assertTrue(result.ok)
        assertEquals(1, result.providersRestored)
        assertEquals(1, result.playlistsRestored)
        assertTrue(result.settingsRestored)

        val providers = db.providerDao().all().first()
        assertEquals(1, providers.size)
        assertEquals("Casa", providers[0].displayName)
        assertEquals("s3cr3t", providers[0].secret)

        val playlists = db.playlistDao().playlists().first()
        assertEquals(listOf("Favoritas"), playlists.map { it.name })
        assertEquals(listOf(1L, 2L), db.playlistDao().memberTrackIds(playlists[0].id))

        assertFalse(settings.settings.first().gaplessPlayback)
    }

    @Test
    fun `wrong password is rejected`() = runBlocking {
        db.providerDao().upsert(ProviderConfigEntity(type = "SUBSONIC", displayName = "X", primaryUrl = "http://x", secondaryUrl = null, username = null, secret = null))
        val archive = manager.createBackup(setOf(BackupContent.PROVIDERS), "right", "0.0.5", 1L)

        val result = manager.restoreBackup(archive, "wrong")
        assertFalse(result.ok)
    }

    @Test
    fun `restore drops playlist members whose tracks are absent`() = runBlocking {
        db.musicDao().upsertTracks(listOf(track(1)))
        val plId = db.playlistDao().upsert(PlaylistEntity(name = "Mix", providerId = 1L))
        db.playlistDao().setPlaylistTracks(plId, listOf(1L, 999L)) // 999 does not exist
        val archive = manager.createBackup(setOf(BackupContent.PLAYLISTS), "pw", "0.0.5", 1L)

        db.playlistDao().delete(plId)
        manager.restoreBackup(archive, "pw")

        val restored = db.playlistDao().playlists().first().single()
        // The missing track (999) is filtered out; only the existing track survives.
        assertEquals(listOf(1L), db.playlistDao().memberTrackIds(restored.id))
    }
}
