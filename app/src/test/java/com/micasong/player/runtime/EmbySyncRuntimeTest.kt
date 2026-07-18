package com.micasong.player.runtime

import com.micasong.player.data.provider.EmbyProvider
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime verification of the Emby client against a fake server ([MockWebServer]): the Items
 * request carries the X-Emby-Authorization header, and items map to playable tracks (spec §46).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmbySyncRuntimeTest {

    private lateinit var server: MockWebServer
    @Volatile private var itemsAuthHeader: String? = null

    @Before
    fun startServer() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: ""
                val json = if (path.endsWith("/Items")) {
                    itemsAuthHeader = request.getHeader("X-Emby-Authorization")
                    """{"Items":[
                        {"Id":"i1","Name":"Song","Album":"Alb","AlbumId":"a1","AlbumArtist":"Art","Artists":["Art"],"ArtistItems":[{"Id":"ar1","Name":"Art"}],"IndexNumber":1,"ProductionYear":2020,"Genres":["Pop"],"RunTimeTicks":1800000000}
                    ]}"""
                } else """{"Items":[]}"""
                return MockResponse().addHeader("Content-Type", "application/json").setBody(json)
            }
        }
        server.start()
    }

    @After
    fun stopServer() = server.shutdown()

    @Test
    fun `syncs a fake Emby server and sends the emby auth header`() = runBlocking {
        val baseUrl = server.url("").toString().trimEnd('/')
        val provider = EmbyProvider(
            ProviderConfig(
                id = 1003, type = ProviderType.EMBY, displayName = "Emby",
                primaryUrl = baseUrl, username = "user1", secret = "token1",
            )
        )

        val snapshot = provider.sync()

        assertEquals(1, snapshot.tracks.size)
        val t = snapshot.tracks.first()
        assertEquals("Song", t.title)
        assertEquals(180_000L, t.durationMs) // ticks / 10000
        assertTrue(t.mediaUri.contains("/Audio/i1/stream"))
        assertTrue(t.mediaUri.contains("api_key=token1"))

        assertNotNull("Emby auth header must be present", itemsAuthHeader)
        assertTrue(itemsAuthHeader!!.contains("Token=\"token1\""))
    }
}
