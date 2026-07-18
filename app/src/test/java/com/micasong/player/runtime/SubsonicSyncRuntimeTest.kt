package com.micasong.player.runtime

import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import com.micasong.player.data.provider.SubsonicProvider
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
 * End-to-end runtime verification of the Subsonic client against a local fake Subsonic server
 * ([MockWebServer]). Exercises the whole path — authenticated URL building, real HTTP requests,
 * JSON parsing and entity mapping — without a real server. Runs under Robolectric because the
 * provider uses android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubsonicSyncRuntimeTest {

    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
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
                    path.endsWith("/getAlbum.view") -> """{"subsonic-response":{"album":{"id":"al1","name":"Viva La Vida","song":[
                        {"id":"s1","title":"Viva La Vida","album":"Viva La Vida","albumId":"al1","artist":"Coldplay","artistId":"ar1","track":7,"duration":242,"year":2008,"genre":"Rock","coverArt":"s1","contentType":"audio/flac","bitRate":990,"size":8000000},
                        {"id":"s2","title":"Lost!","album":"Viva La Vida","albumId":"al1","artist":"Coldplay","artistId":"ar1","track":6,"duration":256,"year":2008,"genre":"Rock","coverArt":"s2","contentType":"audio/flac","bitRate":900,"size":7000000}
                    ]}}}"""
                    else -> """{"subsonic-response":{"status":"failed"}}"""
                }
                return MockResponse().addHeader("Content-Type", "application/json").setBody(json)
            }
        }
        server.start()
    }

    @After
    fun stopServer() = server.shutdown()

    @Test
    fun `syncs a fake Subsonic server into tracks, albums, artists and genres`() = runBlocking {
        val baseUrl = server.url("").toString().trimEnd('/')
        val provider = SubsonicProvider(
            ProviderConfig(
                id = 1001, type = ProviderType.SUBSONIC, displayName = "Test",
                primaryUrl = baseUrl, username = "alice", secret = "secret",
            )
        )

        val snapshot = provider.sync()

        assertEquals(2, snapshot.tracks.size)
        val viva = snapshot.tracks.first { it.title == "Viva La Vida" }
        assertEquals("Coldplay", viva.artistName)
        assertEquals(7, viva.trackNumber)
        assertEquals(242_000L, viva.durationMs)
        assertEquals("Rock", viva.genre)
        assertEquals(990_000, viva.bitrate)

        assertTrue("mediaUri should be a stream URL", viva.mediaUri.contains("/rest/stream.view"))
        assertTrue(viva.mediaUri.contains("id=s1"))
        assertTrue(viva.mediaUri.contains("t="))

        assertEquals(1, snapshot.albums.size)
        assertEquals("Viva La Vida", snapshot.albums.first().name)
        assertEquals(1, snapshot.artists.size)
        assertEquals("Coldplay", snapshot.artists.first().name)
        assertTrue(snapshot.genres.any { it.name == "Rock" && it.trackCount == 2 })
    }

    @Test
    fun `unreachable server yields an empty snapshot, not a crash`() = runBlocking {
        val provider = SubsonicProvider(
            ProviderConfig(id = 1, type = ProviderType.SUBSONIC, displayName = "Dead", primaryUrl = "http://127.0.0.1:1")
        )
        val snapshot = provider.sync()
        assertTrue(snapshot.tracks.isEmpty())
    }
}
