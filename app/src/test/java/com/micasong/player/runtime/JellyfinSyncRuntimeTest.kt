package com.micasong.player.runtime

import com.micasong.player.data.provider.JellyfinProvider
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
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
 * End-to-end runtime verification of the Jellyfin client against a local fake server
 * ([MockWebServer]): the authenticated Items request, JSON parsing, entity mapping and the
 * playable stream URL are all exercised for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JellyfinSyncRuntimeTest {

    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: ""
                val json = if (path.endsWith("/Items")) """{"Items":[
                    {"Id":"i1","Name":"Creep","Album":"Pablo Honey","AlbumId":"a1","AlbumArtist":"Radiohead","Artists":["Radiohead"],"ArtistItems":[{"Id":"ar1","Name":"Radiohead"}],"IndexNumber":2,"ParentIndexNumber":1,"ProductionYear":1993,"Genres":["Alternative"],"RunTimeTicks":2380000000},
                    {"Id":"i2","Name":"Karma Police","Album":"OK Computer","AlbumId":"a2","AlbumArtist":"Radiohead","Artists":["Radiohead"],"ArtistItems":[{"Id":"ar1","Name":"Radiohead"}],"IndexNumber":6,"ParentIndexNumber":1,"ProductionYear":1997,"Genres":["Alternative"],"RunTimeTicks":2620000000}
                ]}""" else """{"Items":[]}"""
                return MockResponse().addHeader("Content-Type", "application/json").setBody(json)
            }
        }
        server.start()
    }

    @After
    fun stopServer() = server.shutdown()

    @Test
    fun `syncs a fake Jellyfin server into playable tracks`() = runBlocking {
        val baseUrl = server.url("").toString().trimEnd('/')
        val provider = JellyfinProvider(
            ProviderConfig(
                id = 1002, type = ProviderType.JELLYFIN, displayName = "JF",
                primaryUrl = baseUrl, username = "user1", secret = "token1",
            )
        )

        val snapshot = provider.sync()

        assertEquals(2, snapshot.tracks.size)
        val creep = snapshot.tracks.first { it.title == "Creep" }
        assertEquals("Radiohead", creep.artistName)
        assertEquals(2, creep.trackNumber)
        assertEquals(238_000L, creep.durationMs)   // ticks / 10000
        assertEquals(1993, creep.year)

        assertTrue("mediaUri should be a stream URL", creep.mediaUri.contains("/Audio/i1/stream"))
        assertTrue(creep.mediaUri.contains("api_key=token1"))

        assertEquals(2, snapshot.albums.size)
        assertTrue(snapshot.genres.any { it.name == "Alternative" })
    }
}
