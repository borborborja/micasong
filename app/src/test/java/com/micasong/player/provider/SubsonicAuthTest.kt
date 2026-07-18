package com.micasong.player.provider

import com.micasong.player.data.provider.SubsonicAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SubsonicAuthTest {

    @Test
    fun `token matches the official Subsonic documentation vector`() {
        // From the Subsonic API docs: password "sesame" + salt "c19b2d".
        assertEquals("26719a1196d2a940705a59634eb18eab", SubsonicAuth.token("sesame", "c19b2d"))
    }

    @Test
    fun `md5 of empty string is well known`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", SubsonicAuth.md5Hex(""))
    }

    @Test
    fun `auth params contain the required keys`() {
        val params = SubsonicAuth.authParams("alice", "secret", "abcdef", "MiCaSong")
        assertEquals("alice", params["u"])
        assertEquals(SubsonicAuth.token("secret", "abcdef"), params["t"])
        assertEquals("abcdef", params["s"])
        assertEquals("json", params["f"])
        assertEquals("MiCaSong", params["c"])
        assertEquals(SubsonicAuth.API_VERSION, params["v"])
    }

    @Test
    fun `endpoint url is well formed and encodes values`() {
        val url = SubsonicAuth.endpointUrl(
            baseUrl = "https://music.example.com/",
            view = "stream",
            params = linkedMapOf("id" to "tr 1", "maxBitRate" to "192"),
        )
        assertEquals("https://music.example.com/rest/stream.view?id=tr+1&maxBitRate=192", url)
    }

    @Test
    fun `random salt is hex and deterministic for a seed`() {
        val a = SubsonicAuth.randomSalt(Random(1))
        val b = SubsonicAuth.randomSalt(Random(1))
        assertEquals(a, b)
        assertEquals(12, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
    }
}
