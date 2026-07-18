package com.micasong.player.provider

import com.micasong.player.data.provider.ServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `bare host and port gets http scheme`() {
        assertEquals("http://192.168.1.10:4533", ServerUrl.normalize("192.168.1.10:4533"))
    }

    @Test
    fun `existing scheme is preserved`() {
        assertEquals("https://music.example.com", ServerUrl.normalize("https://music.example.com/"))
        assertEquals("http://nas.local:8096", ServerUrl.normalize("http://nas.local:8096"))
    }

    @Test
    fun `trims whitespace and trailing slash`() {
        assertEquals("http://x.y", ServerUrl.normalize("  x.y/  "))
    }

    @Test
    fun `blank input is null`() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("   "))
        assertNull(ServerUrl.normalize(null))
    }

    @Test
    fun `subpath is preserved`() {
        assertEquals("http://host/music", ServerUrl.normalize("host/music"))
    }
}
