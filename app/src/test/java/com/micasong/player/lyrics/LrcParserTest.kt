package com.micasong.player.lyrics

import com.micasong.player.data.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses timestamps into milliseconds`() {
        val lrc = """
            [00:12.50]First line
            [01:05.00]Second line
        """.trimIndent()
        val result = LrcParser.parse(lrc)
        assertTrue(result.synced)
        assertEquals(2, result.lines.size)
        assertEquals(12_500L, result.lines[0].timeMs)
        assertEquals("First line", result.lines[0].text)
        assertEquals(65_000L, result.lines[1].timeMs)
    }

    @Test
    fun `multiple timestamps on one line expand into several`() {
        val result = LrcParser.parse("[00:10.00][00:20.00]Repeated chorus")
        assertEquals(2, result.lines.size)
        assertEquals(listOf(10_000L, 20_000L), result.lines.map { it.timeMs })
        assertTrue(result.lines.all { it.text == "Repeated chorus" })
    }

    @Test
    fun `metadata tags are ignored`() {
        val lrc = """
            [ar:Coldplay]
            [ti:Viva La Vida]
            [00:01.00]I used to rule the world
        """.trimIndent()
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.lines.size)
        assertEquals("I used to rule the world", result.lines[0].text)
    }

    @Test
    fun `plain lyrics are unsynced`() {
        val result = LrcParser.parse("Just some\nplain lyrics")
        assertFalse(result.synced)
        assertEquals(2, result.lines.size)
    }

    @Test
    fun `active line tracks playback position`() {
        val result = LrcParser.parse("[00:00.00]A\n[00:10.00]B\n[00:20.00]C")
        assertEquals(-1, result.activeIndexAt(-1))
        assertEquals(0, result.activeIndexAt(5_000))
        assertEquals(1, result.activeIndexAt(15_000))
        assertEquals(2, result.activeIndexAt(25_000))
    }

    @Test
    fun `blank input yields empty unsynced lyrics`() {
        val result = LrcParser.parse("")
        assertTrue(result.lines.isEmpty())
        assertFalse(result.synced)
    }
}
