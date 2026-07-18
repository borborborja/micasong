package com.micasong.player.runtime

import com.micasong.player.data.provider.KodiProvider
import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runtime verification of the Kodi JSON-RPC client against a fake server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KodiSyncRuntimeTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"id":1,"jsonrpc":"2.0","result":{"songs":[
                    {"songid":10,"title":"Bohemian Rhapsody","artist":["Queen"],"albumartist":["Queen"],"album":"A Night at the Opera","track":11,"disc":1,"duration":355,"year":1975,"genre":["Rock"],"file":"/music/queen/bohemian.flac","thumbnail":"image://music%2f"}
                ]}}"""
            )
        )
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `reads songs via json-rpc and builds a vfs stream url`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        val provider = KodiProvider(
            ProviderConfig(id = 1005, type = ProviderType.KODI, displayName = "Kodi", primaryUrl = base, username = "kodi", secret = "pw"),
        )

        val snapshot = provider.sync()

        assertEquals(1, snapshot.tracks.size)
        val t = snapshot.tracks.first()
        assertEquals("Bohemian Rhapsody", t.title)
        assertEquals("Queen", t.artistName)
        assertEquals(11, t.trackNumber)
        assertEquals(355_000L, t.durationMs)
        assertTrue(t.mediaUri.contains("/vfs/"))
        assertTrue("stream URL carries basic-auth userinfo", t.mediaUri.contains("kodi:pw@"))

        // The JSON-RPC request went out as a POST to /jsonrpc with a Basic auth header.
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/jsonrpc"))
        assertTrue(req.getHeader("Authorization")!!.startsWith("Basic "))
    }
}
