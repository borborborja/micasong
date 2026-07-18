package com.micasong.player.playlist

import com.micasong.player.data.playlist.PlaylistFileParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFileParserTest {

    @Test
    fun `m3u with extinf captures title and duration`() {
        val m3u = """
            #EXTM3U
            #EXTINF:123,Coldplay - Viva La Vida
            /music/coldplay/viva.mp3
            #EXTINF:200,Radiohead - Creep
            http://server/creep.flac
        """.trimIndent()
        val result = PlaylistFileParser.parseM3u(m3u)
        assertEquals(2, result.entries.size)
        assertEquals("/music/coldplay/viva.mp3", result.entries[0].path)
        assertEquals("Coldplay - Viva La Vida", result.entries[0].title)
        assertEquals(123, result.entries[0].durationSec)
        assertEquals("http://server/creep.flac", result.entries[1].path)
    }

    @Test
    fun `plain m3u without directives`() {
        val m3u = "song1.mp3\nsong2.flac\n"
        val result = PlaylistFileParser.parseM3u(m3u)
        assertEquals(listOf("song1.mp3", "song2.flac"), result.paths)
        assertNull(result.entries[0].title)
    }

    @Test
    fun `m3u ignores unknown directives and blank lines`() {
        val m3u = "#EXTM3U\n\n#PLAYLIST:My List\n\n/a.mp3\n"
        val result = PlaylistFileParser.parseM3u(m3u)
        assertEquals(listOf("/a.mp3"), result.paths)
    }

    @Test
    fun `pls parses files titles and lengths in order`() {
        val pls = """
            [playlist]
            File1=/music/a.mp3
            Title1=Song A
            Length1=180
            File2=http://server/b.mp3
            Title2=Song B
            Length2=240
            NumberOfEntries=2
            Version=2
        """.trimIndent()
        val result = PlaylistFileParser.parsePls(pls)
        assertEquals(2, result.entries.size)
        assertEquals("/music/a.mp3", result.entries[0].path)
        assertEquals("Song A", result.entries[0].title)
        assertEquals(180, result.entries[0].durationSec)
        assertEquals("http://server/b.mp3", result.entries[1].path)
    }

    @Test
    fun `pls entries sorted by index even when out of order`() {
        val pls = "[playlist]\nFile2=b.mp3\nFile1=a.mp3\nFile10=j.mp3\n"
        val result = PlaylistFileParser.parsePls(pls)
        assertEquals(listOf("a.mp3", "b.mp3", "j.mp3"), result.paths)
    }

    @Test
    fun `dispatch detects pls by content`() {
        val pls = "[playlist]\nFile1=x.mp3\n"
        assertEquals(listOf("x.mp3"), PlaylistFileParser.parse(pls).paths)
    }

    @Test
    fun `dispatch honours file extension`() {
        val content = "File1=x.mp3\n[playlist]"   // ambiguous content, .pls extension decides
        assertEquals(listOf("x.mp3"), PlaylistFileParser.parse(content, "list.pls").paths)
    }

    @Test
    fun `negative extinf duration is dropped`() {
        val result = PlaylistFileParser.parseM3u("#EXTINF:-1,Live Stream\nhttp://radio\n")
        assertNull(result.entries[0].durationSec)
        assertEquals("Live Stream", result.entries[0].title)
    }
}
