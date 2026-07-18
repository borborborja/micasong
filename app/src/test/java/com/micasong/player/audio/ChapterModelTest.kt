package com.micasong.player.audio

import com.micasong.player.data.audio.Chapters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterModelTest {

    // Three chapters over a 30-minute book.
    private val info = Chapters.fromMarkers(
        markers = listOf(
            "Introduction" to 0L,
            "Chapter One" to 600_000L,      // 10:00
            "Chapter Two" to 1_200_000L,    // 20:00
        ),
        totalDurationMs = 1_800_000L,       // 30:00
    )

    @Test
    fun `builds contiguous chapters`() {
        assertEquals(3, info.count)
        assertEquals(0L, info.chapters[0].startMs)
        assertEquals(600_000L, info.chapters[0].endMs)
        assertEquals(600_000L, info.chapters[0].durationMs)
        assertEquals(1_800_000L, info.chapters[2].endMs)
    }

    @Test
    fun `chapter at position finds the current chapter`() {
        assertEquals("Introduction", info.chapterAt(120_000)?.title)   // 2:00
        assertEquals("Chapter One", info.chapterAt(600_000)?.title)    // boundary → next chapter
        assertEquals("Chapter Two", info.chapterAt(1_500_000)?.title)  // 25:00
    }

    @Test
    fun `position past the end clamps to last chapter`() {
        assertEquals("Chapter Two", info.chapterAt(2_000_000)?.title)
    }

    @Test
    fun `nav state exposes template fields`() {
        val s = Chapters.navStateAt(info, positionMs = 900_000)!!   // 15:00 → Chapter One
        assertEquals(3, s.count)
        assertEquals(2, s.index)                     // 1-based
        assertEquals("Chapter One", s.title)
        assertEquals(300_000L, s.positionInChapterMs) // 5:00 into the chapter
        assertEquals(300_000L, s.remainingInChapterMs)
        assertEquals(600_000L, s.chapterDurationMs)
        assertEquals("Chapter Two", s.nextChapterTitle)
        assertEquals(900_000L, s.totalProgressMs)
        assertEquals(1_800_000L, s.totalDurationMs)
    }

    @Test
    fun `last chapter has no next`() {
        val s = Chapters.navStateAt(info, positionMs = 1_500_000)!!
        assertNull(s.nextChapterTitle)
    }

    @Test
    fun `markers are sorted before building`() {
        val unsorted = Chapters.fromMarkers(listOf("B" to 100L, "A" to 0L), totalDurationMs = 200L)
        assertEquals("A", unsorted.chapters[0].title)
        assertEquals("B", unsorted.chapters[1].title)
    }

    @Test
    fun `empty markers yield no chapters and null state`() {
        val empty = Chapters.fromMarkers(emptyList(), 1000)
        assertEquals(0, empty.count)
        assertNull(Chapters.navStateAt(empty, 0))
    }
}
