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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the Subsonic client detects the server version/OpenSubsonic and falls back to legacy auth. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubsonicNegotiateRuntimeTest {

    private lateinit var server: MockWebServer

    @After
    fun stop() = server.shutdown()

    private fun provider(): SubsonicProvider {
        val base = server.url("/").toString().trimEnd('/')
        return SubsonicProvider(
            ProviderConfig(id = 1000, type = ProviderType.SUBSONIC, displayName = "S", primaryUrl = base, username = "u", secret = "p"),
        )
    }

    @Test
    fun `adopts server version and openSubsonic flag`() {
        server = MockWebServer().apply {
            enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true}}"""))
            start()
        }
        val p = provider()
        runBlocking { assertNull(p.negotiate()) }
        assertTrue(p.openSubsonic)
        // Subsequent requests use the negotiated version.
        assertTrue(p.endpoint("getArtists").contains("v=1.16.1"))
    }

    @Test
    fun `falls back to legacy auth when token is rejected`() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val url = request.requestUrl!!
                    return if (url.queryParameter("p") != null) {
                        // Legacy auth accepted.
                        MockResponse().setBody("""{"subsonic-response":{"status":"ok","version":"1.15.0"}}""")
                    } else {
                        // Token auth not supported for this user (code 41).
                        MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":41,"message":"Token auth not supported"}}}""")
                    }
                }
            }
            start()
        }
        val p = provider()
        runBlocking { assertNull(p.negotiate()) }
        // Now every request uses hex-encoded legacy auth, not the salted token.
        val url = p.endpoint("getArtists")
        assertTrue("should use legacy p=enc:", url.contains("p=enc%3A") || url.contains("p=enc:"))
        assertTrue(url.contains("v=1.15.0"))
    }
}
