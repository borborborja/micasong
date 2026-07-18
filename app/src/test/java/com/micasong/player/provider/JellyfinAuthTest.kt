package com.micasong.player.provider

import com.micasong.player.data.provider.JellyfinAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinAuthTest {

    @Test
    fun `authorization header without token`() {
        val h = JellyfinAuth.authorizationHeader(deviceId = "dev-1", deviceName = "Pixel")
        assertEquals(
            "MediaBrowser Client=\"MiCaSong\", Device=\"Pixel\", DeviceId=\"dev-1\", Version=\"0.1.0\"",
            h,
        )
    }

    @Test
    fun `authorization header includes token when present`() {
        val h = JellyfinAuth.authorizationHeader(deviceId = "dev-1", token = "abc123")
        assertTrue(h.endsWith("Token=\"abc123\""))
    }

    @Test
    fun `endpoint url joins base and path`() {
        assertEquals(
            "https://jf.example.com/Users/u1/Items",
            JellyfinAuth.endpointUrl("https://jf.example.com/", "/Users/u1/Items"),
        )
    }

    @Test
    fun `endpoint url encodes query params`() {
        val url = JellyfinAuth.endpointUrl(
            "https://jf.example.com",
            "/Audio/x/universal",
            linkedMapOf("static" to "false", "maxStreamingBitrate" to "192000"),
        )
        assertEquals("https://jf.example.com/Audio/x/universal?static=false&maxStreamingBitrate=192000", url)
    }

    @Test
    fun `path without leading slash is normalised`() {
        assertEquals(
            "https://jf.example.com/System/Info",
            JellyfinAuth.endpointUrl("https://jf.example.com", "System/Info"),
        )
    }
}
