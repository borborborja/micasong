package com.micasong.player

import com.micasong.player.data.lyrics.LrcParser
import com.micasong.player.data.provider.SubsonicMappers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies OpenSubsonic getLyricsBySongId → LRC conversion and downstream parsing (spec §41). */
class SubsonicLyricsTest {

    @Test
    fun `synced structured lyrics become LRC with timestamps`() {
        val json = JSONObject(
            """
            {"subsonic-response":{"status":"ok","lyricsList":{"structuredLyrics":[{
              "synced":true,
              "line":[
                {"start":0,"value":"First line"},
                {"start":1500,"value":"Second line"},
                {"start":63120,"value":"Later"}
              ]
            }]}}}
            """.trimIndent()
        )
        val lrc = SubsonicMappers.parseLyrics(json)!!
        assertTrue(lrc.contains("[00:00.00]First line"))
        assertTrue(lrc.contains("[00:01.50]Second line"))
        assertTrue(lrc.contains("[01:03.12]Later"))

        val parsed = LrcParser.parse(lrc)
        assertTrue(parsed.synced)
        assertEquals("Second line", parsed.lines[parsed.activeIndexAt(2000)].text)
    }

    @Test
    fun `legacy plain lyrics are returned as text`() {
        val json = JSONObject("""{"subsonic-response":{"lyrics":{"value":"Just plain words"}}}""")
        assertEquals("Just plain words", SubsonicMappers.parseLyrics(json))
    }

    @Test
    fun `no lyrics returns null`() {
        val json = JSONObject("""{"subsonic-response":{"status":"ok"}}""")
        assertNull(SubsonicMappers.parseLyrics(json))
    }
}
