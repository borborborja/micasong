package com.micasong.player.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micasong.player.data.db.MiCaSongDatabase
import com.micasong.player.data.db.ProviderConfigEntity
import com.micasong.player.data.repository.MediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full server-path integration at runtime: a persisted Subsonic provider config → [MediaRepository.syncAll]
 * → sync against a fake server → differential apply into the real Room DB → repository reads. Also
 * verifies that a re-sync preserves local user state (favorites), exercising [ServerSyncMerge] end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerSyncIntegrationRuntimeTest {

    private lateinit var db: MiCaSongDatabase
    private lateinit var repository: MediaRepository
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiCaSongDatabase::class.java).allowMainThreadQueries().build()
        repository = MediaRepository(context, db.musicDao(), db.playlistDao(), db.providerDao())

        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: ""
                val offset = request.requestUrl?.queryParameter("offset")?.toIntOrNull() ?: 0
                val json = when {
                    path.endsWith("/ping.view") -> """{"subsonic-response":{"status":"ok"}}"""
                    path.endsWith("/getArtists.view") ->
                        """{"subsonic-response":{"artists":{"index":[{"name":"C","artist":[{"id":"ar1","name":"Coldplay","albumCount":1}]}]}}}"""
                    path.endsWith("/getAlbumList2.view") ->
                        if (offset > 0) """{"subsonic-response":{"albumList2":{"album":[]}}}"""
                        else """{"subsonic-response":{"albumList2":{"album":[{"id":"al1","name":"Viva La Vida","artist":"Coldplay","artistId":"ar1","songCount":2,"duration":484,"year":2008,"coverArt":"al1"}]}}}"""
                    path.endsWith("/getAlbum.view") -> """{"subsonic-response":{"album":{"id":"al1","song":[
                        {"id":"s1","title":"Viva La Vida","album":"Viva La Vida","albumId":"al1","artist":"Coldplay","artistId":"ar1","track":7,"duration":242,"genre":"Rock","contentType":"audio/flac","bitRate":990},
                        {"id":"s2","title":"Lost!","album":"Viva La Vida","albumId":"al1","artist":"Coldplay","artistId":"ar1","track":6,"duration":256,"genre":"Rock","contentType":"audio/flac","bitRate":900}
                    ]}}}"""
                    else -> """{"subsonic-response":{"status":"failed"}}"""
                }
                return MockResponse().addHeader("Content-Type", "application/json").setBody(json)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private suspend fun addSubsonicServer() {
        db.providerDao().upsert(
            ProviderConfigEntity(
                type = "SUBSONIC", displayName = "Test",
                primaryUrl = server.url("").toString().trimEnd('/'),
                secondaryUrl = null, username = "alice", secret = "secret",
            )
        )
    }

    @Test
    fun `onboarded server syncs into the database with playable tracks`() = runBlocking {
        addSubsonicServer()
        repository.syncAll()

        val tracks = repository.allTracks.first()
        assertEquals(2, tracks.size)
        val viva = tracks.first { it.title == "Viva La Vida" }
        assertTrue("track should have a playable stream URL", viva.mediaUri.contains("/rest/stream.view"))

        assertEquals(1, repository.albums.first().size)
        assertTrue(repository.genres.first().any { it.name == "Rock" })
    }

    @Test
    fun `re-sync preserves a locally-set favorite`() = runBlocking {
        addSubsonicServer()
        repository.syncAll()

        val viva = repository.allTracks.first().first { it.title == "Viva La Vida" }
        repository.toggleTrackFavorite(viva)                       // favorite it locally
        assertTrue(repository.favoriteTracks.first().any { it.title == "Viva La Vida" })

        repository.syncAll()                                       // server re-sync must not wipe it
        assertTrue(
            "favorite must survive a server re-sync",
            repository.favoriteTracks.first().any { it.title == "Viva La Vida" },
        )
    }
}
