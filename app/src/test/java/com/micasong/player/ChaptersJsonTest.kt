package com.micasong.player

import com.micasong.player.data.audio.ChapterMarker
import com.micasong.player.data.audio.Chapters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies chaptersJson parsing and chapter navigation (spec §19). */
class ChaptersJsonTest {

    @Test
    fun `parses markers and resolves the active chapter`() {
        val json = Chapters.toJson(
            listOf(
                ChapterMarker("Intro", 0),
                ChapterMarker("Part One", 60_000),
                ChapterMarker("Part Two", 180_000),
            )
        )
        val info = Chapters.fromJson(json, totalDurationMs = 300_000)
        assertEquals(3, info.count)
        assertEquals("Part One", info.chapterAt(90_000)!!.title)
        assertEquals(180_000, info.chapters[2].startMs)
        assertEquals(300_000, info.chapters[2].endMs) // last chapter runs to the end

        val nav = Chapters.navStateAt(info, 200_000)!!
        assertEquals(3, nav.index)
        assertEquals("Part Two", nav.title)
    }

    @Test
    fun `blank or invalid json yields no chapters`() {
        assertTrue(Chapters.fromJson(null, 1000).isEmpty)
        assertTrue(Chapters.fromJson("not json", 1000).isEmpty)
    }
}
