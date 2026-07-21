package com.micasong.player.cache

import com.micasong.player.data.cache.BasicAuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BasicAuthInterceptorTest {

    private val client = OkHttpClient.Builder().addInterceptor(BasicAuthInterceptor).build()

    @Test
    fun `userinfo becomes a Basic auth header and leaves the url`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()

        val url = "http://kodi:secret@${server.hostName}:${server.port}/vfs/track.mp3"
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }

        val received = server.takeRequest()
        val expected = java.util.Base64.getEncoder().encodeToString("kodi:secret".toByteArray())
        assertEquals("Basic $expected", received.getHeader("Authorization"))
        assertEquals("/vfs/track.mp3", received.path)
        server.shutdown()
    }

    @Test
    fun `urls without credentials are untouched`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()

        client.newCall(Request.Builder().url(server.url("/plain")).build()).execute().use { it.body?.string() }

        assertNull(server.takeRequest().getHeader("Authorization"))
        server.shutdown()
    }
}
