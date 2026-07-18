package com.micasong.player.runtime

import com.micasong.player.data.provider.ProviderConfig
import com.micasong.player.data.provider.ProviderType
import com.micasong.player.data.provider.SubsonicProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the Subsonic connection test returns clear errors instead of failing silently (§5.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubsonicConnectionRuntimeTest {

    private fun provider(base: String) = SubsonicProvider(
        ProviderConfig(id = 1000, type = ProviderType.SUBSONIC, displayName = "S", primaryUrl = base, username = "u", secret = "p"),
    )

    @Test
    fun `ok status passes`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""))
        server.start()
        assertNull(provider(server.url("/").toString().trimEnd('/')).testConnection())
        server.shutdown()
    }

    @Test
    fun `wrong credentials give a clear message`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""))
        server.start()
        val err = provider(server.url("/").toString().trimEnd('/')).testConnection()
        assertNotNull(err)
        assertTrue(err!!.contains("contraseña", ignoreCase = true))
        server.shutdown()
    }

    @Test
    fun `unreachable server gives a connection message`() = runBlocking {
        // Point at a port that isn't listening.
        val err = provider("http://127.0.0.1:1").testConnection()
        assertNotNull(err)
        assertTrue(err!!.contains("conectar", ignoreCase = true))
    }
}
